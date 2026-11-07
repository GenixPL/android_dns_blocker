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

        // Public DoH/DoT resolvers, so browsers and apps can't bypass us with their own encrypted DNS.
        // Our own upstream (DohClient) uses an IP literal, so it is unaffected.
        val DOH_DOMAINS = listOf(
            "dns.google",
            "dns.google.com",
            "cloudflare-dns.com",
            "one.one.one.one",
            "dns.cloudflare.com",
            "dns.quad9.net",
            "dns.nextdns.io",
            "doh.opendns.com",
            "dns.adguard.com",
            "dns.adguard-dns.com",
            "doh.cleanbrowsing.org",
            "dns.sb",
            "doh.libredns.gr",
            // Firefox disables its automatic DoH when this canary domain returns NXDOMAIN.
            "use-application-dns.net",
        )
    }
}
