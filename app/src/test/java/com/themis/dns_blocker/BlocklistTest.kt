package com.themis.dns_blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistTest {

    private val list = Blocklist(listOf("youtube.com", "youtubei.googleapis.com"))

    @Test
    fun blocksExactAndSubdomains() {
        assertTrue(list.isBlocked("youtube.com"))
        assertTrue(list.isBlocked("www.youtube.com"))
        assertTrue(list.isBlocked("a.b.youtube.com"))
    }

    @Test
    fun ignoresCaseAndTrailingDot() {
        assertTrue(list.isBlocked("WWW.YouTube.COM."))
    }

    @Test
    fun doesNotBlockLookalikes() {
        assertFalse(list.isBlocked("notyoutube.com"))
        assertFalse(list.isBlocked("youtube.com.example.org"))
        assertFalse(list.isBlocked("com"))
    }

    @Test
    fun blocksOnlyListedGoogleapisHost() {
        assertTrue(list.isBlocked("youtubei.googleapis.com"))
        assertFalse(list.isBlocked("android.googleapis.com"))
        assertFalse(list.isBlocked("googleapis.com"))
    }

    @Test
    fun dohDomainsBlockResolversAndFirefoxCanary() {
        val l = Blocklist(Blocklist.DOH_DOMAINS)
        assertTrue(l.isBlocked("dns.google"))
        assertTrue(l.isBlocked("mozilla.cloudflare-dns.com"))
        assertTrue(l.isBlocked("use-application-dns.net"))
        assertFalse(l.isBlocked("google.com"))
        assertFalse(l.isBlocked("example.com"))
    }

    @Test
    fun parseDomainCleansInput() {
        assertEquals("foo.com", Blocklist.parseDomain("  https://Foo.com/some/path "))
        assertEquals("a.b.co.uk", Blocklist.parseDomain("a.b.co.uk."))
        assertEquals("foo.com", Blocklist.parseDomain("foo.com:8080"))
    }

    @Test
    fun parseDomainRejectsInvalid() {
        assertNull(Blocklist.parseDomain(""))
        assertNull(Blocklist.parseDomain("localhost"))
        assertNull(Blocklist.parseDomain("foo bar.com"))
        assertNull(Blocklist.parseDomain("-foo.com"))
        assertNull(Blocklist.parseDomain("foo..com"))
    }

    @Test
    fun emptyEntriesAndNamesNeverMatch() {
        val l = Blocklist(listOf("", " ", "."))
        assertFalse(l.isBlocked(""))
        assertFalse(l.isBlocked("example.com"))
    }
}
