package com.example.fucknotes.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The few Google Drive v3 calls the sync needs, over plain HTTP.
 *
 * No client library: a folder, a search, an upload and a download are four URLs, and keeping
 * them in view here is what makes the sync readable. Every call carries a bearer token from
 * [tokens]; one 401 is answered by asking for a fresh token and trying once more.
 */
class DriveApi(private val tokens: TokenSource) {

    fun interface TokenSource {
        suspend fun token(forceRefresh: Boolean): String
    }

    class DriveHttpException(val status: Int, message: String) : IOException("Drive $status: $message")

    @Serializable
    data class DriveFile(
        val id: String,
        val name: String = "",
        val mimeType: String = "",
        val parents: List<String> = emptyList(),
        val appProperties: Map<String, String> = emptyMap(),
        val trashed: Boolean = false,
        val modifiedTime: String = "",
    ) {
        val isFolder: Boolean get() = mimeType == MIME_FOLDER
    }

    @Serializable
    data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

    @Serializable
    data class About(val user: User = User())

    @Serializable
    data class User(val displayName: String = "", val emailAddress: String = "")

    private class Response(val status: Int, val body: ByteArray, val headers: Map<String, List<String>>) {
        val text: String get() = body.toString(Charsets.UTF_8)
    }

    suspend fun about(): User = json.decodeFromString<About>(
        call("GET", "$API/about?fields=user(displayName,emailAddress)").text
    ).user

    /** The file, or null when Drive no longer has it. */
    suspend fun get(id: String): DriveFile? {
        val response = call("GET", "$API/files/${enc(id)}?fields=$FIELDS", allow404 = true)
        return if (response.status == 404) null else json.decodeFromString(response.text)
    }

    suspend fun list(query: String): List<DriveFile> {
        val all = mutableListOf<DriveFile>()
        var token: String? = null
        do {
            val url = buildString {
                append("$API/files?q=").append(enc(query))
                append("&fields=nextPageToken,files($FIELDS)&pageSize=200&spaces=drive")
                token?.let { append("&pageToken=").append(enc(it)) }
            }
            val page = json.decodeFromString<FileList>(call("GET", url).text)
            all += page.files
            token = page.nextPageToken
        } while (!token.isNullOrEmpty())
        return all
    }

    suspend fun children(parentId: String): List<DriveFile> =
        list("'${q(parentId)}' in parents and trashed = false")

    suspend fun findChild(parentId: String, name: String, mimeType: String? = null): DriveFile? {
        val query = buildString {
            append("'${q(parentId)}' in parents and name = '${q(name)}' and trashed = false")
            mimeType?.let { append(" and mimeType = '${q(it)}'") }
        }
        return list(query).firstOrNull()
    }

    suspend fun createFolder(name: String, parentId: String?, appProperties: Map<String, String> = emptyMap()): DriveFile {
        val body = metadata(name = name, mimeType = MIME_FOLDER, parents = listOfNotNull(parentId), appProperties = appProperties)
        return json.decodeFromString(call("POST", "$API/files?fields=$FIELDS", body, JSON).text)
    }

    suspend fun update(
        id: String,
        name: String? = null,
        appProperties: Map<String, String>? = null,
        addParent: String? = null,
        removeParent: String? = null,
        trashed: Boolean? = null,
    ): DriveFile {
        val url = buildString {
            append("$API/files/${enc(id)}?fields=$FIELDS")
            addParent?.let { append("&addParents=").append(enc(it)) }
            removeParent?.let { append("&removeParents=").append(enc(it)) }
        }
        val body = metadata(name = name, appProperties = appProperties, trashed = trashed)
        return json.decodeFromString(call("PATCH", url, body, JSON).text)
    }

    suspend fun delete(id: String) {
        call("DELETE", "$API/files/${enc(id)}", allow404 = true)
    }

    /**
     * Uploads [file] as a new Drive file, or as the new content of [existingId].
     *
     * Resumable upload in two steps: the metadata starts a session, the bytes follow in one PUT.
     * It takes any size, which a multipart body does not.
     */
    suspend fun upload(
        file: File,
        mimeType: String,
        name: String,
        parentId: String?,
        appProperties: Map<String, String>,
        existingId: String?,
    ): DriveFile {
        val body = if (existingId == null) metadata(name, mimeType, listOfNotNull(parentId), appProperties)
        else metadata(name = name, appProperties = appProperties)
        val start = if (existingId == null) "$UPLOAD/files?uploadType=resumable&fields=$FIELDS"
        else "$UPLOAD/files/${enc(existingId)}?uploadType=resumable&fields=$FIELDS"
        val session = call(if (existingId == null) "POST" else "PATCH", start, body, JSON, extra = mapOf(
            "X-Upload-Content-Type" to mimeType,
            "X-Upload-Content-Length" to file.length().toString(),
        ))
        val location = session.headers.entries.firstOrNull { it.key.equals("Location", ignoreCase = true) }?.value?.firstOrNull()
            ?: throw DriveHttpException(session.status, "No upload session")
        val result = withContext(Dispatchers.IO) {
            val connection = (URL(location).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = UPLOAD_TIMEOUT_MS
                setRequestProperty("Content-Type", mimeType)
                setFixedLengthStreamingMode(file.length())
            }
            try {
                connection.outputStream.use { out -> file.inputStream().use { it.copyTo(out, 256 * 1024) } }
                read(connection)
            } finally { connection.disconnect() }
        }
        if (result.status !in 200..299) throw DriveHttpException(result.status, result.text.take(300))
        return json.decodeFromString(result.text)
    }

    suspend fun download(id: String, to: File) {
        val response = call("GET", "$API/files/${enc(id)}?alt=media", stream = to)
        if (response.status !in 200..299) throw DriveHttpException(response.status, response.text.take(300))
    }

    /** Best effort: tells Google the token is done with. Failure is not an error for the caller. */
    suspend fun revoke(token: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val connection = (URL("https://oauth2.googleapis.com/revoke?token=${enc(token)}").openConnection() as HttpURLConnection)
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                try { connection.responseCode } finally { connection.disconnect() }
            }
        }
    }

    // ---- plumbing -------------------------------------------------------------------------------

    private suspend fun call(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        extra: Map<String, String> = emptyMap(),
        allow404: Boolean = false,
        stream: File? = null,
    ): Response {
        var response = send(method, url, body, contentType, extra, tokens.token(false), stream)
        if (response.status == 401) {
            response = send(method, url, body, contentType, extra, tokens.token(true), stream)
        }
        if (response.status == 404 && allow404) return response
        if (response.status !in 200..299) throw DriveHttpException(response.status, errorMessage(response))
        return response
    }

    private suspend fun send(
        method: String, url: String, body: ByteArray?, contentType: String?,
        extra: Map<String, String>, token: String, stream: File?,
    ): Response = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = if (stream != null) UPLOAD_TIMEOUT_MS else READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            extra.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null) {
                doOutput = true
                contentType?.let { setRequestProperty("Content-Type", it) }
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            body?.let { connection.outputStream.use { out -> out.write(it) } }
            if (stream != null && connection.responseCode in 200..299) {
                connection.inputStream.use { input -> stream.outputStream().use { input.copyTo(it, 256 * 1024) } }
                Response(connection.responseCode, ByteArray(0), connection.headerFields.orEmpty())
            } else read(connection)
        } finally { connection.disconnect() }
    }

    private fun read(connection: HttpURLConnection): Response {
        val status = connection.responseCode
        val bytes = runCatching {
            (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() }
        }.getOrNull() ?: ByteArray(0)
        return Response(status, bytes, connection.headerFields.orEmpty())
    }

    private fun errorMessage(response: Response): String = runCatching {
        val root = json.parseToJsonElement(response.text) as JsonObject
        ((root["error"] as? JsonObject)?.get("message") as? JsonPrimitive)?.content
    }.getOrNull() ?: response.text.take(200).ifBlank { "HTTP ${response.status}" }

    private fun metadata(
        name: String? = null,
        mimeType: String? = null,
        parents: List<String> = emptyList(),
        appProperties: Map<String, String>? = null,
        trashed: Boolean? = null,
    ): ByteArray = buildJsonObject {
        name?.let { put("name", it) }
        mimeType?.let { put("mimeType", it) }
        if (parents.isNotEmpty()) put("parents", kotlinx.serialization.json.JsonArray(parents.map { JsonPrimitive(it) }))
        appProperties?.let { props -> put("appProperties", JsonObject(props.mapValues { JsonPrimitive(it.value) })) }
        trashed?.let { put("trashed", it) }
    }.toString().toByteArray()

    companion object {
        const val MIME_FOLDER = "application/vnd.google-apps.folder"
        const val MIME_PDF = "application/pdf"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FIELDS = "id,name,mimeType,parents,appProperties,trashed,modifiedTime"
        private const val JSON = "application/json; charset=UTF-8"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val UPLOAD_TIMEOUT_MS = 10 * 60_000
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** Escapes a value for a Drive `q` string literal. */
        fun q(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

        private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
