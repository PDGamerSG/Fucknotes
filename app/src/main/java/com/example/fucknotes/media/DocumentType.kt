package com.example.fucknotes.media

/**
 * What an incoming file is, from the little a sender tells us.
 *
 * A VIEW or SEND intent may carry the right type, a legacy PowerPoint type for a modern deck, an
 * `application/octet-stream` from a download, or nothing at all; the file name is the second
 * witness. Pure Kotlin so the rules are checked on the JVM.
 */
object DocumentType {
    const val MIME_PDF = "application/pdf"
    const val MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    const val MIME_PPT = "application/vnd.ms-powerpoint"

    /** Every type seen in the wild for a PowerPoint deck, modern or legacy. */
    val slideTypes: Set<String> = setOf(
        MIME_PPTX,
        MIME_PPT,
        "application/mspowerpoint",
        "application/x-mspowerpoint",
        "application/powerpoint",
    )

    val pdfTypes: Set<String> = setOf(MIME_PDF, "application/x-pdf")

    enum class Kind { PDF, SLIDES }

    /** PDF or slides, or null when neither the type nor the name says so. */
    fun kindOf(type: String?, name: String?): Kind? {
        val mime = type.orEmpty().substringBefore(';').trim().lowercase()
        val file = name.orEmpty().trim().lowercase()
        return when {
            mime in slideTypes || file.endsWith(".pptx") || file.endsWith(".ppt") -> Kind.SLIDES
            mime in pdfTypes || file.endsWith(".pdf") -> Kind.PDF
            else -> null
        }
    }
}
