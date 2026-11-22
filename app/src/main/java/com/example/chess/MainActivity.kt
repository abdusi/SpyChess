package com.example.chess

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.chess.recording.RECORDINGS_FOLDER
import com.example.chess.recording.RecordingService
import com.example.chess.recording.SavedRecording
import com.example.chess.ui.AudioPermissionRequest
import com.example.chess.ui.ChessApp
import com.example.chess.ui.ChessScreenModel

class MainActivity : ComponentActivity() {
    private lateinit var screenModel: ChessScreenModel
    private var resumed = false

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = screenModel.permissionRequest ?: return@registerForActivityResult
        screenModel.permissionRequest = null
        if (granted) {
            screenModel.audioMessage = null
            if (request != AudioPermissionRequest.INITIAL_MICROPHONE) continueConfirmedRecording()
        } else {
            screenModel.confirmedStartPending = false
            screenModel.audioMessage = request.denialMessage
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val barStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, 0xFFE9DDC2.toInt())
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        screenModel = ViewModelProvider(this)[ChessScreenModel::class.java]

        setContent {
            val recording by RecordingService.status
            ChessApp(
                game = screenModel.game,
                isRecording = recording.isRecording,
                isStartingRecording = recording.isStarting,
                audioMessage = screenModel.audioMessage ?: recording.message,
                lastRecording = recording.lastRecording,
                onPlay = screenModel::play,
                onClaimDraw = screenModel::claimDraw,
                onRestart = screenModel::restart,
                onConfirmRecording = ::confirmRecording,
                onSaveRecording = { RecordingService.stop(this) },
                onOpenRecording = ::openRecording,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (screenModel.confirmedStartPending) {
            continueConfirmedRecording()
        } else if (screenModel.permissionRequest == null && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            val preferences = getPreferences(MODE_PRIVATE)
            if (!preferences.getBoolean(ASKED_ABOUT_MICROPHONE, false)) {
                launchPermissionRequest(AudioPermissionRequest.INITIAL_MICROPHONE)
            }
        }
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    private fun confirmRecording() {
        val recording = RecordingService.status.value
        if (!resumed || screenModel.permissionRequest != null || recording.isRecording || recording.isStarting) return

        screenModel.audioMessage = null
        screenModel.confirmedStartPending = true
        continueConfirmedRecording()
    }

    private fun continueConfirmedRecording() {
        if (!resumed || !screenModel.confirmedStartPending || screenModel.permissionRequest != null) return

        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            launchPermissionRequest(AudioPermissionRequest.RECORD_MICROPHONE)
            return
        }

        screenModel.confirmedStartPending = false
        RecordingService.notificationsAvailable(this) // создаёт канал уведомлений для работы сервиса
        RecordingService.start(this)
    }

    private fun launchPermissionRequest(request: AudioPermissionRequest) {
        if (request.permission == Manifest.permission.RECORD_AUDIO) {
            getPreferences(MODE_PRIVATE).edit().putBoolean(ASKED_ABOUT_MICROPHONE, true).apply()
        }
        screenModel.permissionRequest = request
        requestPermission.launch(request.permission)
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun openRecording(recording: SavedRecording) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(recording.uri, "audio/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            screenModel.audioMessage = "Откройте ${recording.name} в папке $RECORDINGS_FOLDER."
        } catch (_: SecurityException) {
            screenModel.audioMessage = "Проверьте, что файл ${recording.name} есть в папке $RECORDINGS_FOLDER."
        }
    }

    private companion object {
        const val ASKED_ABOUT_MICROPHONE = "asked_about_microphone"
    }
}
