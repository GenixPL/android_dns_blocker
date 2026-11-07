package com.themis.dns_blocker

private const val IPV4_MIN_HEADER = 20
private const val UDP_HEADER = 8
private const val DNS_HEADER = 12
private const val PROTO_UDP = 17
private const val DNS_PORT = 53
private const val QUESTION_TAIL = 4 // QTYPE + QCLASS

const val RCODE_NOERROR = 0
const val RCODE_NXDOMAIN = 3

/** A DNS question received over IPv4/UDP. [dns] is the UDP payload up to the end of the first question. */
class DnsQuery(
    val name: String,
    val srcAddr: ByteArray,
    val dstAddr: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val dns: ByteArray,
)

/** Returns the query if [packet] is an IPv4/UDP datagram to port 53 with a DNS question, else null. */
fun parseDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
    if (length < IPV4_MIN_HEADER) return null
    if (packet[0].toInt() shr 4 != 4) return null
    if (packet[9].toInt() != PROTO_UDP) return null

    val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
    val dnsStart = ipHeaderLen + UDP_HEADER
    if (length < dnsStart + DNS_HEADER) return null

    val dstPort = u16(packet, ipHeaderLen + 2)
    if (dstPort != DNS_PORT) return null

    val labels = ArrayList<String>()
    var pos = dnsStart + DNS_HEADER
    while (pos < length) {
        val labelLen = packet[pos].toInt() and 0xFF
        if (labelLen == 0) {
            val questionEnd = pos + 1 + QUESTION_TAIL
            if (questionEnd > length) return null
            return DnsQuery(
                name = labels.joinToString("."),
                srcAddr = packet.copyOfRange(12, 16),
                dstAddr = packet.copyOfRange(16, 20),
                srcPort = u16(packet, ipHeaderLen),
                dstPort = dstPort,
                dns = packet.copyOfRange(dnsStart, questionEnd),
            )
        }
        if (labelLen > 63 || pos + 1 + labelLen > length) return null
        labels += String(packet, pos + 1, labelLen, Charsets.US_ASCII)
        pos += 1 + labelLen
    }
    return null
}

/** Turns [query] into an empty response with [rcode]: header flags set, answer/authority/additional counts zeroed. */
fun buildDnsResponse(query: DnsQuery, rcode: Int): ByteArray {
    val out = query.dns.copyOf()
    val rd = out[2].toInt() and 0x01
    val opcode = out[2].toInt() and 0x78
    out[2] = (0x80 or opcode or rd).toByte() // QR=1
    out[3] = (0x80 or rcode).toByte() // RA=1
    for (i in 6..11) out[i] = 0
    return out
}

/** Wraps [dns] in an IPv4/UDP datagram going back to the sender of [query]. */
fun buildUdpReply(query: DnsQuery, dns: ByteArray): ByteArray {
    val udpLen = UDP_HEADER + dns.size
    val out = ByteArray(IPV4_MIN_HEADER + udpLen)

    out[0] = 0x45
    put16(out, 2, out.size)
    out[6] = 0x40 // DF
    out[8] = 64 // TTL
    out[9] = PROTO_UDP.toByte()
    query.dstAddr.copyInto(out, 12)
    query.srcAddr.copyInto(out, 16)
    put16(out, 10, checksum(out, 0, IPV4_MIN_HEADER))

    put16(out, IPV4_MIN_HEADER, query.dstPort)
    put16(out, IPV4_MIN_HEADER + 2, query.srcPort)
    put16(out, IPV4_MIN_HEADER + 4, udpLen)
    dns.copyInto(out, IPV4_MIN_HEADER + UDP_HEADER)

    val pseudo = ByteArray(12 + udpLen)
    out.copyInto(pseudo, 0, 12, 20)
    pseudo[9] = PROTO_UDP.toByte()
    put16(pseudo, 10, udpLen)
    out.copyInto(pseudo, 12, IPV4_MIN_HEADER, out.size)
    val udpSum = checksum(pseudo, 0, pseudo.size)
    put16(out, IPV4_MIN_HEADER + 6, if (udpSum == 0) 0xFFFF else udpSum)
    return out
}

/** Internet checksum (RFC 1071) over [len] bytes of [b] starting at [off]. */
fun checksum(b: ByteArray, off: Int, len: Int): Int {
    var sum = 0
    var i = off
    while (i + 1 < off + len) {
        sum += u16(b, i)
        i += 2
    }
    if (len % 2 == 1) sum += (b[off + len - 1].toInt() and 0xFF) shl 8
    while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
    return sum.inv() and 0xFFFF
}

private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

private fun put16(b: ByteArray, i: Int, v: Int) {
    b[i] = (v shr 8).toByte()
    b[i + 1] = v.toByte()
}
