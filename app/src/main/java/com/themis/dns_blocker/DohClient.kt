package com.themis.dns_blocker

import java.net.HttpURLConnection
import java.net.URL

/**
 * Forwards wire-format DNS messages to a DoH endpoint (RFC 8484, POST).
 *
 * The default endpoint uses an IP literal so resolving its hostname never goes back through our own
 * fake DNS server. Its traffic isn't routed into the TUN either (only the fake DNS /32 is), so
 * the sockets need no VpnService.protect().
 */
class DohClient(private val endpoint: String = DEFAULT_ENDPOINT) {

    /** Returns the upstream response, or null on any failure. */
    fun query(dns: ByteArray): ByteArray? {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/dns-message")
            conn.setRequestProperty("Accept", "application/dns-message")
            conn.outputStream.use { it.write(dns) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else conn.inputStream.use { it.readBytes() }
        } catch (e: java.io.IOException) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://1.1.1.1/dns-query"
        private const val TIMEOUT_MS = 3000
    }
}
