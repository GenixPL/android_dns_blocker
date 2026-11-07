package com.themis.dns_blocker

import org.junit.Assert.assertFalse
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
    fun emptyEntriesAndNamesNeverMatch() {
        val l = Blocklist(listOf("", " ", "."))
        assertFalse(l.isBlocked(""))
        assertFalse(l.isBlocked("example.com"))
    }
}
