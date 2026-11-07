package com.themis.dns_blocker

private const val IPV4_MIN_HEADER = 20
private const val UDP_HEADER = 8
private const val DNS_HEADER = 12
private const val PROTO_UDP = 17
private const val DNS_PORT = 53

/** Returns the queried name if [packet] is an IPv4/UDP datagram to port 53 with a DNS question, else null. */
fun parseDnsQueryName(packet: ByteArray, length: Int): String? {
    if (length < IPV4_MIN_HEADER) return null
    if (packet[0].toInt() shr 4 != 4) return null
    if (packet[9].toInt() != PROTO_UDP) return null

    val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
    val dnsStart = ipHeaderLen + UDP_HEADER
    if (length < dnsStart + DNS_HEADER) return null

    val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
    if (dstPort != DNS_PORT) return null

    val labels = ArrayList<String>()
    var pos = dnsStart + DNS_HEADER
    while (pos < length) {
        val labelLen = packet[pos].toInt() and 0xFF
        if (labelLen == 0) return labels.joinToString(".")
        if (labelLen > 63 || pos + 1 + labelLen > length) return null
        labels += String(packet, pos + 1, labelLen, Charsets.US_ASCII)
        pos += 1 + labelLen
    }
    return null
}
