package com.themis.dns_blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.RemoteViews
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val doh = DohClient()
    private var forwarder: ExecutorService = Executors.newFixedThreadPool(FORWARDER_THREADS)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> turnOff()
            ACTION_IDLE -> if (tun == null) showIdle()
            ACTION_TOGGLE -> toggle()
            ACTION_RELOAD -> {
                teardown()
                start()
            }
            else -> start()
        }
        // A killed idle service must not come back and start the VPN on its own.
        return if (tun != null) START_STICKY else START_NOT_STICKY
    }

    private fun start() {
        if (tun != null) return
        BlocklistStore.init(this)
        startForeground(
            NOTIFICATION_ID,
            buildNotification(on = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUN_ADDRESS, 24)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
            .setBlocking(true)
        for (pkg in BlocklistStore.allowedApps.value) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.d(TAG, "$pkg not installed, nothing to exclude")
            }
        }
        tun = builder.establish()

        val fd = tun
        if (fd == null) {
            Log.e(TAG, "establish() returned null: VPN permission not granted")
            turnOff()
            return
        }

        forwarder = Executors.newFixedThreadPool(FORWARDER_THREADS)
        reader = Thread({ readLoop(fd) }, "tun-reader").also { it.start() }
        setRunning(true)
    }

    private fun setRunning(value: Boolean) {
        _running.value = value
        CoroutineScope(Dispatchers.Default).launch { VpnWidget().updateAll(applicationContext) }
    }

    private fun readLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(MTU)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf)
                if (n <= 0) continue
                val query = parseDnsQuery(buf, n) ?: continue
                if (BlocklistStore.blocklist.isBlocked(query.name)) {
                    Log.d(TAG, "DNS query: ${query.name} [blocked]")
                    reply(output, query, buildDnsResponse(query, RCODE_NXDOMAIN))
                } else {
                    Log.d(TAG, "DNS query: ${query.name}")
                    // Upstream lookups block, so run them off the reader thread.
                    forwarder.execute {
                        val answer = doh.query(query.payload) ?: buildDnsResponse(query, RCODE_SERVFAIL)
                        reply(output, query, answer)
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "tun reader stopped: ${e.message}")
        }
    }

    private fun reply(output: FileOutputStream, query: DnsQuery, dns: ByteArray) {
        val packet = buildUdpReply(query, dns)
        try {
            synchronized(output) { output.write(packet) }
        } catch (e: IOException) {
            Log.d(TAG, "tun write failed: ${e.message}")
        }
    }

    private fun teardown() {
        reader?.interrupt()
        reader = null
        forwarder.shutdownNow()
        tun?.close()
        tun = null
    }

    /** Stops blocking but keeps the service in the foreground so the notification can turn it back on. */
    private fun turnOff() {
        teardown()
        setRunning(false)
        showIdle()
    }

    private fun showIdle() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(on = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )
    }

    private fun toggle() {
        when {
            tun != null -> turnOff()
            VpnService.prepare(this) == null -> start()
            else -> {
                // Only an activity can ask for VPN consent.
                showIdle()
                startActivity(vpnConsentIntent(this))
            }
        }
    }

    override fun onRevoke() = turnOff()

    override fun onDestroy() {
        teardown()
        setRunning(false)
        super.onDestroy()
    }

    private fun buildNotification(on: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getForegroundService(
            this, 1, Intent(this, DnsVpnService::class.java).setAction(ACTION_TOGGLE), PendingIntent.FLAG_IMMUTABLE,
        )
        val content = RemoteViews(packageName, R.layout.notification_toggle).apply {
            setTextViewText(R.id.notification_status, if (on) "DNS blocking is active" else "DNS blocking is off")
            setTextViewText(R.id.notification_toggle, if (on) "Turn off" else "Turn on")
            setOnClickPendingIntent(R.id.notification_toggle, toggle)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(open)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.themis.dns_blocker.STOP"
        private const val ACTION_RELOAD = "com.themis.dns_blocker.RELOAD"
        private const val ACTION_IDLE = "com.themis.dns_blocker.IDLE"
        private const val ACTION_TOGGLE = "com.themis.dns_blocker.TOGGLE"

        private const val TAG = "DnsVpnService"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1
        private const val TUN_ADDRESS = "10.0.0.1"
        private const val FAKE_DNS = "10.0.0.2"
        private const val MTU = 32767
        private const val FORWARDER_THREADS = 8

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(context: Context) {
            context.startForegroundService(Intent(context, DnsVpnService::class.java))
        }

        /** Posts the notification while the VPN is off, so it can be turned on from there. */
        fun showIdle(context: Context) {
            context.startForegroundService(Intent(context, DnsVpnService::class.java).setAction(ACTION_IDLE))
        }

        /** Re-establishes the tunnel so a changed allowed-apps list takes effect. */
        fun reload(context: Context) {
            context.startForegroundService(Intent(context, DnsVpnService::class.java).setAction(ACTION_RELOAD))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
