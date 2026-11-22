package com.example.chess.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.chess.MainActivity
import com.example.chess.R
import java.util.UUID

internal data class RecordingStatus(
    val isStarting: Boolean = false,
    val isRecording: Boolean = false,
    val lastRecording: SavedRecording? = null,
    val message: String? = null,
)

class RecordingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var recorder: ChessAudioRecorder
    private var activeToken: String? = null
    private var notificationDeadline = 0L
    private val checkNotification = Runnable(::verifyRecordingNotification)

    private val notificationSettings = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(checkNotification)
            verifyRecordingNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        recorder = ChessAudioRecorder(this) {
            finish("Запись прервалась. Попробуйте начать заново.")
        }
        val filter = IntentFilter(NotificationManager.ACTION_APP_BLOCK_STATE_CHANGED).apply {
            addAction(NotificationManager.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, notificationSettings, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        when {
            intent?.action == ACTION_START && token != null && token == requestedToken -> {
                if (activeToken == null) promote(token)
            }
            intent?.action == ACTION_STOP && token != null && token == activeToken -> finish()
            activeToken == null -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (activeToken != null) finish()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (activeToken != null) completeRecording()
        unregisterReceiver(notificationSettings)
        super.onDestroy()
    }

    private fun promote(token: String) {
        activeToken = token
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(token), type)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not promote recording service", error)
            finish(START_FAILED)
            return
        }

        notificationDeadline = SystemClock.uptimeMillis() + NOTIFICATION_TIMEOUT
        handler.post(checkNotification)
    }

    private fun buildNotification(token: String): Notification {
        val openApp = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openAppAction = PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE)
        val stopRecording = command(this, ACTION_STOP, token)
            .setData(Uri.fromParts("chess-recording", token, null))
        val stopAction = PendingIntent.getService(this, 0, stopRecording, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recording_notification)
            .setContentTitle("Игра идет")
            .setContentText("Нажмите, чтобы открыть шахматы.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addExtras(Bundle().apply { putString(EXTRA_TOKEN, token) })
            .setContentIntent(openAppAction)
            .setDeleteIntent(stopAction)
            .addAction(0, "Прервать игру", stopAction)
            .build()
    }

    private fun verifyRecordingNotification() {
        if (activeToken == null) return
        val current = mutableStatus.value

        if (current.isStarting) {
            if (!hasMicrophonePermission(this)) {
                finish(MIC_PERMISSION_MISSING)
                return
            }
            if (!recorder.start()) {
                finish("Не удалось включить микрофон.")
                return
            }
            mutableStatus.value = current.copy(isStarting = false, isRecording = true)
        }
    }

    private fun finish(reason: String? = null) {
        completeRecording(reason)
        stopSelf()
    }

    private fun completeRecording(reason: String? = null) {
        val token = activeToken ?: return
        activeToken = null
        handler.removeCallbacks(checkNotification)
        val saved = recorder.stopAndSave()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (requestedToken != token) return

        requestedToken = null
        val current = mutableStatus.value
        val message = if (saved != null) {
            listOfNotNull(reason, "Отличная игра! повторите схватку?").joinToString(" ")
        } else {
            reason ?: if (current.isRecording) {
                "Не удалось сохранить запись. Возможно, она была слишком короткой."
            } else {
                "Запуск записи отменён."
            }
        }
        mutableStatus.value = RecordingStatus(lastRecording = saved ?: current.lastRecording, message = message)
    }

    companion object {
        private const val TAG = "ChessRecordingService"
        private const val CHANNEL_ID = "microphone_recording"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_TIMEOUT = 1_500L
        private const val NOTIFICATION_RETRY = 100L
        private const val NOTIFICATION_CHECK_INTERVAL = 2_000L
        private const val ACTION_START = "com.example.chess.action.START_RECORDING"
        private const val ACTION_STOP = "com.example.chess.action.STOP_RECORDING"
        private const val EXTRA_TOKEN = "recordingToken"
        private const val START_FAILED = "Не удалось запустить игру."
        private const val MICROPHONE_CHANNEL_NAME = "Запись с микрофона"
        private const val MIC_PERMISSION_MISSING = "Предоставьте доступ для начала игры."
        private const val NOTIFICATION_UNAVAILABLE = "Включите уведомление."

        private var requestedToken: String? = null
        private val mutableStatus = mutableStateOf(RecordingStatus())
        internal val status: State<RecordingStatus> = mutableStatus

        internal fun notificationsAvailable(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return true
            return try {
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, MICROPHONE_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
                            .apply {
                                description = "Показывает, когда работает микрофон, и позволяет остановить запись."
                                setShowBadge(true)
                            },
                    )
                }
                canShowNotification(context, manager)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not check notification settings", error)
                true
            }
        }

        @MainThread
        internal fun start(context: Context) {
            if (requestedToken != null) return
            val current = mutableStatus.value
            val blocker = when {
                !hasMicrophonePermission(context) -> MIC_PERMISSION_MISSING
                !notificationsAvailable(context) -> NOTIFICATION_UNAVAILABLE
                else -> null
            }
            if (blocker != null) {
                mutableStatus.value = current.copy(message = blocker)
                return
            }

            val token = UUID.randomUUID().toString()
            requestedToken = token
            mutableStatus.value = current.copy(isStarting = true, message = null)
            try {
                checkNotNull(context.startForegroundService(command(context, ACTION_START, token)))
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not start recording service", error)
                requestedToken = null
                mutableStatus.value = current.copy(message = START_FAILED)
            }
        }

        @MainThread
        internal fun stop(context: Context) {
            val token = requestedToken ?: return
            val intent = command(context, ACTION_STOP, token)
            try {
                if (context.startService(intent) != null) return
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not deliver the stop command", error)
            }
            try {
                context.stopService(intent)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not stop the recording service", error)
            }
        }

        private fun command(context: Context, action: String, token: String): Intent =
            Intent(context, RecordingService::class.java).setAction(action).putExtra(EXTRA_TOKEN, token)

        private fun hasMicrophonePermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        private fun canShowNotification(context: Context, manager: NotificationManager): Boolean = true
        }
    }
