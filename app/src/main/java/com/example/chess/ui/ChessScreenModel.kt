package com.example.chess.ui

import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.chess.game.ChessGame
import com.example.chess.game.ChessMove

internal class ChessScreenModel : ViewModel() {
    var game by mutableStateOf(ChessGame.initial())
        private set
    var audioMessage by mutableStateOf<String?>(null)
    var permissionRequest: AudioPermissionRequest? = null
    var confirmedStartPending = false

    fun play(move: ChessMove) {
        game = game.play(move)
    }

    fun claimDraw() {
        game = game.claimDraw()
    }

    fun restart() {
        game = ChessGame.initial()
    }
}

internal enum class AudioPermissionRequest(val permission: String, val denialMessage: String) {
    INITIAL_MICROPHONE(Manifest.permission.RECORD_AUDIO, "Доступ к микрофону не предоставлен."),
    RECORD_MICROPHONE(Manifest.permission.RECORD_AUDIO, "Разрешите доступ к микрофону, чтобы начать запись."),
    RECORD_NOTIFICATIONS(Manifest.permission.POST_NOTIFICATIONS, "Разрешите уведомления о записи в настройках приложения."),
}
