package com.themis.dns_blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsQueryTest {

    private fun udpPacket(name: String, dstPort: Int = 53, protocol: Int = 17): ByteArray {
        val qname = name.split('.').flatMap { label -> listOf(label.length.toByte()) + label.toByteArray().toList() } + 0.toByte()
        val dns = ByteArray(12) + qname.toByteArray() + byteArrayOf(0, 1, 0, 1)
        val ip = ByteArray(20).also {
            it[0] = 0x45
            it[9] = protocol.toByte()
        }
        val udp = ByteArray(8).also {
            it[2] = (dstPort shr 8).toByte()
            it[3] = dstPort.toByte()
        }
        return ip + udp + dns
    }

    @Test
    fun parsesQueryName() {
        val p = udpPacket("www.youtube.com")
        assertEquals("www.youtube.com", parseDnsQueryName(p, p.size))
    }

    @Test
    fun ignoresNonDnsPort() {
        val p = udpPacket("youtube.com", dstPort = 443)
        assertNull(parseDnsQueryName(p, p.size))
    }

    @Test
    fun ignoresNonUdp() {
        val p = udpPacket("youtube.com", protocol = 6)
        assertNull(parseDnsQueryName(p, p.size))
    }

    @Test
    fun ignoresTruncatedPacket() {
        val p = udpPacket("youtube.com")
        assertNull(parseDnsQueryName(p, 25))
    }
}
