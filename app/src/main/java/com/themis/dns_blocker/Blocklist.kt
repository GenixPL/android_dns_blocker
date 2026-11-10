package com.themis.dns_blocker

/** Blocks a domain and all of its subdomains, except those covered by a more specific [allowed] entry. */
class Blocklist(domains: Collection<String>, allowed: Collection<String> = emptyList()) {

    private val blocked = domains.map(::normalize).filter { it.isNotEmpty() }.toSet()
    private val allowed = allowed.map(::normalize).filter { it.isNotEmpty() }.toSet()

    fun isBlocked(name: String): Boolean {
        var candidate = normalize(name)
        while (candidate.isNotEmpty()) {
            if (candidate in allowed) return false
            if (candidate in blocked) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
        return false
    }

    private fun normalize(name: String) = name.trim().trimEnd('.').lowercase()

    companion object {
        private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

        /** Cleans up user input ("https://Foo.com/path" -> "foo.com"); null if it isn't a valid domain. */
        fun parseDomain(input: String): String? {
            val host = input.trim().lowercase()
                .removePrefix("https://").removePrefix("http://")
                .substringBefore('/').substringBefore(':')
                .trimEnd('.')
            return host.takeIf { DOMAIN.matches(it) }
        }

        // Not googleapis.com as a whole: other Google services depend on it.
        val DEFAULT_DOMAINS = listOf(
            "youtube.com",
            "youtu.be",
            "youtube-nocookie.com",
            "googlevideo.com",
            "ytimg.com",
            "youtubei.googleapis.com",
        )

        // Carved out of the blocked domains above.
        val DEFAULT_ALLOWED = listOf(
            "music.youtube.com",
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
