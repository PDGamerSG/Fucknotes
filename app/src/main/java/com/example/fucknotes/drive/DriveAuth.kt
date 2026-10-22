package com.example.fucknotes.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * The Google account's permission to touch Drive, through Play services.
 *
 * Only the `drive.file` scope is asked for: the app sees the files it made and nothing else in
 * the user's Drive. The first request shows Google's account and consent sheet, handed back as a
 * [PendingIntent] for the screen to launch; afterwards tokens come silently and are kept in
 * memory only. Google keeps the grant, so "log out" here means forgetting the account.
 *
 * Needs an Android OAuth client for this package and signing key in the Google Cloud project;
 * see docs/drive-sync.md.
 */
class DriveAuth(private val context: Context) {

    sealed class Access {
        data class Granted(val token: String) : Access()
        data class NeedsConsent(val intent: PendingIntent) : Access()
    }

    class ConsentRequiredException(val intent: PendingIntent) : Exception("Google Drive needs the account's permission")

    @Volatile private var cached: String? = null

    private val client get() = Identity.getAuthorizationClient(context)

    private val request: AuthorizationRequest
        get() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_DRIVE_FILE))).build()

    /** Asks Play services; silent when the account already agreed, otherwise a sheet to show. */
    suspend fun request(): Access {
        val result = client.authorize(request).await()
        val intent = result.pendingIntent
        if (result.hasResolution() && intent != null) return Access.NeedsConsent(intent)
        val token = result.accessToken ?: throw IllegalStateException("Google returned no access token")
        cached = token
        return Access.Granted(token)
    }

    /** The token from the consent sheet's result, or null when the user backed out. */
    fun fromConsent(data: Intent?): String? = runCatching {
        client.getAuthorizationResultFromIntent(data).accessToken
    }.getOrNull()?.also { cached = it }

    /** A token for a call; throws [ConsentRequiredException] when a sheet would be needed. */
    suspend fun token(forceRefresh: Boolean): String {
        if (!forceRefresh) cached?.let { return it }
        cached = null
        return when (val access = request()) {
            is Access.Granted -> access.token
            is Access.NeedsConsent -> throw ConsentRequiredException(access.intent)
        }
    }

    fun forget(): String? = cached.also { cached = null }

    companion object {
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }
}
