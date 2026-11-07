package com.themis.dns_blocker

/** Blocks a domain and all of its subdomains. */
class Blocklist(domains: Collection<String>) {

    private val blocked = domains.map(::normalize).filter { it.isNotEmpty() }.toSet()

    fun isBlocked(name: String): Boolean {
        var candidate = normalize(name)
        while (candidate.isNotEmpty()) {
            if (candidate in blocked) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
        return false
    }

    private fun normalize(name: String) = name.trim().trimEnd('.').lowercase()

    companion object {
        // Not googleapis.com as a whole: other Google services depend on it.
        val DEFAULT_DOMAINS = listOf(
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "googlevideo.com",
            "ytimg.com",
            "youtubei.googleapis.com",
        )
    }
}
