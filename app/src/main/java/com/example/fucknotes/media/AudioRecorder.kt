package com.example.fucknotes.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records voice notes to m4a in the app's private media folder. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean = runCatching {
        val file = MediaStore.newFile(context, "m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(96_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        output = file
        startedAt = System.currentTimeMillis()
        true
    }.getOrElse {
        cleanup()
        false
    }

    /** Stops recording and returns the file plus its length, or null if nothing usable was captured. */
    fun stop(): Recording? {
        val rec = recorder ?: return null
        val file = output
        val elapsed = System.currentTimeMillis() - startedAt
        runCatching { rec.stop() }
        cleanup()
        if (file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }
        return Recording(file, durationOf(file).takeIf { it > 0 } ?: elapsed)
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        output?.delete()
        cleanup()
    }

    private fun cleanup() {
        runCatching { recorder?.release() }
        recorder = null
        output = null
        startedAt = 0L
    }

    data class Recording(val file: File, val durationMs: Long)

    companion object {
        fun durationOf(file: File): Long {
            // MediaMetadataRetriever only became AutoCloseable in API 29, so release by hand.
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            } catch (_: Exception) {
                0L
            } finally {
                runCatching { retriever.release() }
            }
        }

        fun formatDuration(millis: Long): String {
            val totalSeconds = millis / 1000
            return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }
    }
}

/** Minimal single-track player used by the attachment row in the editor. */
class AudioPlayer {

    private var player: MediaPlayer? = null
    var playingPath: String? = null
        private set

    fun toggle(path: String, onComplete: () -> Unit) {
        if (playingPath == path) {
            stop()
            return
        }
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener {
                    stop()
                    onComplete()
                }
                prepare()
                start()
            }
            playingPath = path
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingPath = null
    }
}
