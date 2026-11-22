package com.example.chess.recording

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val RECORDINGS_FOLDER = "Music/Chess"

internal data class SavedRecording(val uri: Uri, val name: String)

/** The foreground service starts and stops capture on the main thread. */
internal class ChessAudioRecorder(
    private val context: Context,
    private val onRecordingError: () -> Unit,
) {
    private data class Session(val recorder: MediaRecorder, val recording: SavedRecording)

    private val resolver = context.contentResolver
    private var session: Session? = null

    fun start(): Boolean {
        if (session != null) return false

        val timestamp = LocalDateTime.now().format(fileTimestamp)
        val name = "Chess_$timestamp.m4a"
        var outputUri: Uri? = null
        var openedRecorder: MediaRecorder? = null

        return try {
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Chess/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }) ?: throw IOException("MediaStore did not create an audio file")
            outputUri = uri

            val recorder = createRecorder()
            openedRecorder = recorder
            recorder.setOnErrorListener { source, what, extra ->
                val active = session
                if (active != null && active.recorder === source) {
                    Log.w(TAG, "Recording error ($what, $extra)")
                    session = null
                    release(active.recorder)
                    discard(active.recording.uri)
                    onRecordingError()
                }
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            val descriptor = resolver.openFileDescriptor(uri, "rw")
                ?: throw IOException("Could not open the audio file")
            descriptor.use {
                recorder.setOutputFile(it.fileDescriptor)
                recorder.prepare()
            }
            recorder.start()
            session = Session(recorder, SavedRecording(uri, name))
            true
        } catch (error: Exception) {
            Log.w(TAG, "Could not start recording", error)
            openedRecorder?.let(::release)
            outputUri?.let(::discard)
            false
        }
    }

    fun stopAndSave(): SavedRecording? {
        val active = session ?: return null
        session = null

        val stopped = try {
            active.recorder.stop()
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not finish recording", error)
            false
        } finally {
            release(active.recorder)
        }

        val uri = active.recording.uri
        if (stopped) {
            try {
                val updated = resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }, null, null)
                if (updated > 0) return active.recording
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not publish recording", error)
            }
        }
        discard(uri)
        return null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

    private fun release(recorder: MediaRecorder) {
        try {
            recorder.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not release recorder", error)
        }
    }

    private fun discard(uri: Uri) {
        try {
            resolver.delete(uri, null, null)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not remove unfinished recording", error)
        }
    }

    private companion object {
        const val TAG = "ChessAudioRecorder"
        val fileTimestamp: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss-SSS", Locale.ROOT)
    }
}
