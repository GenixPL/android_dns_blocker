package com.themis.dns_blocker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsQueryTest {

    private val client = byteArrayOf(10, 0, 0, 1)
    private val dnsServer = byteArrayOf(10, 0, 0, 2)

    private fun udpPacket(name: String, dstPort: Int = 53, protocol: Int = 17): ByteArray {
        val qname = name.split('.').flatMap { label -> listOf(label.length.toByte()) + label.toByteArray().toList() } + 0.toByte()
        val header = ByteArray(12).also {
            it[0] = 0x12; it[1] = 0x34 // id
            it[2] = 0x01 // RD
            it[5] = 1 // QDCOUNT
        }
        val dns = header + qname.toByteArray() + byteArrayOf(0, 1, 0, 1)
        val ip = ByteArray(20).also {
            it[0] = 0x45
            it[9] = protocol.toByte()
            client.copyInto(it, 12)
            dnsServer.copyInto(it, 16)
        }
        val udp = ByteArray(8).also {
            it[0] = 0x9C.toByte(); it[1] = 0x40 // src port 40000
            it[2] = (dstPort shr 8).toByte()
            it[3] = dstPort.toByte()
        }
        return ip + udp + dns
    }

    @Test
    fun parsesQuery() {
        val p = udpPacket("www.youtube.com")
        val q = parseDnsQuery(p, p.size)!!
        assertEquals("www.youtube.com", q.name)
        assertEquals(40000, q.srcPort)
        assertArrayEquals(client, q.srcAddr)
        assertArrayEquals(dnsServer, q.dstAddr)
    }

    @Test
    fun ignoresNonDnsPort() {
        val p = udpPacket("youtube.com", dstPort = 443)
        assertNull(parseDnsQuery(p, p.size))
    }

    @Test
    fun ignoresNonUdp() {
        val p = udpPacket("youtube.com", protocol = 6)
        assertNull(parseDnsQuery(p, p.size))
    }

    @Test
    fun ignoresTruncatedPacket() {
        val p = udpPacket("youtube.com")
        assertNull(parseDnsQuery(p, 25))
        assertNull(parseDnsQuery(p, p.size - 1))
    }

    @Test
    fun responseHasFlagsAndKeepsQuestion() {
        val p = udpPacket("youtube.com")
        val q = parseDnsQuery(p, p.size)!!
        val r = buildDnsResponse(q, RCODE_NXDOMAIN)
        assertEquals(q.dns.size, r.size)
        assertEquals(0x12, r[0].toInt()); assertEquals(0x34, r[1].toInt()) // id preserved
        assertEquals(0x81, r[2].toInt() and 0xFF) // QR + RD
        assertEquals(0x83, r[3].toInt() and 0xFF) // RA + NXDOMAIN
        assertEquals(1, r[5].toInt()) // QDCOUNT
        assertEquals(0, r[7].toInt()) // ANCOUNT
    }

    @Test
    fun replySwapsEndpointsAndHasValidChecksums() {
        val p = udpPacket("youtube.com")
        val q = parseDnsQuery(p, p.size)!!
        val reply = buildUdpReply(q, buildDnsResponse(q, RCODE_NXDOMAIN))

        assertEquals(reply.size, ((reply[2].toInt() and 0xFF) shl 8) or (reply[3].toInt() and 0xFF))
        assertArrayEquals(dnsServer, reply.copyOfRange(12, 16))
        assertArrayEquals(client, reply.copyOfRange(16, 20))
        assertEquals(53, ((reply[20].toInt() and 0xFF) shl 8) or (reply[21].toInt() and 0xFF))
        assertEquals(40000, ((reply[22].toInt() and 0xFF) shl 8) or (reply[23].toInt() and 0xFF))

        // Summing a header that includes its checksum yields 0.
        assertEquals(0, checksum(reply, 0, 20))

        val udpLen = reply.size - 20
        val pseudo = ByteArray(12 + udpLen)
        reply.copyInto(pseudo, 0, 12, 20)
        pseudo[9] = 17
        pseudo[10] = (udpLen shr 8).toByte(); pseudo[11] = udpLen.toByte()
        reply.copyInto(pseudo, 12, 20, reply.size)
        assertEquals(0, checksum(pseudo, 0, pseudo.size))
    }

    @Test
    fun checksumMatchesRfc1071Example() {
        val data = byteArrayOf(0x00, 0x01, 0xf2.toByte(), 0x03, 0xf4.toByte(), 0xf5.toByte(), 0xf6.toByte(), 0xf7.toByte())
        assertEquals(0x220d, checksum(data, 0, data.size))
    }
}
