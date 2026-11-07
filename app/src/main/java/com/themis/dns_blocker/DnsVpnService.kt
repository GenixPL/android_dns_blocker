package com.themis.dns_blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileInputStream
import java.io.IOException

class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        start()
        return START_STICKY
    }

    private fun start() {
        if (tun != null) return
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )

        tun = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUN_ADDRESS, 24)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
            .setBlocking(true)
            .establish()

        val fd = tun
        if (fd == null) {
            Log.e(TAG, "establish() returned null: VPN permission not granted")
            stop()
            return
        }

        reader = Thread({ readLoop(fd) }, "tun-reader").also { it.start() }
        _running.value = true
    }

    private fun readLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val buf = ByteArray(MTU)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf)
                if (n <= 0) continue
                parseDnsQueryName(buf, n)?.let { Log.d(TAG, "DNS query: $it") }
            }
        } catch (e: IOException) {
            Log.d(TAG, "tun reader stopped: ${e.message}")
        }
    }

    private fun stop() {
        reader?.interrupt()
        reader = null
        tun?.close()
        tun = null
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = stop()

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("DNS blocking is active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.themis.dns_blocker.STOP"

        private const val TAG = "DnsVpnService"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1
        private const val TUN_ADDRESS = "10.0.0.1"
        private const val FAKE_DNS = "10.0.0.2"
        private const val MTU = 32767

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DnsVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
