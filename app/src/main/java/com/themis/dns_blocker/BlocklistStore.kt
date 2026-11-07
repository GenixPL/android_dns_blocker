package com.themis.dns_blocker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's editable blocklist, persisted in SharedPreferences. [blocklist] is what the VPN service
 * matches against (user domains plus the fixed DoH-bypass domains) and updates as soon as the list is edited.
 */
object BlocklistStore {
    private const val PREFS = "blocklist"
    private const val KEY = "domains"

    private lateinit var prefs: SharedPreferences
    private val _domains = MutableStateFlow<List<String>>(emptyList())

    /** User-editable domains, sorted. */
    val domains: StateFlow<List<String>> = _domains

    @Volatile
    var blocklist = Blocklist(Blocklist.DOH_DOMAINS)
        private set

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        apply(prefs.getStringSet(KEY, null) ?: Blocklist.DEFAULT_DOMAINS.toSet())
    }

    /** Returns false if [input] isn't a valid domain. */
    @Synchronized
    fun add(input: String): Boolean {
        val domain = Blocklist.parseDomain(input) ?: return false
        apply(_domains.value.toSet() + domain)
        return true
    }

    @Synchronized
    fun remove(domain: String) = apply(_domains.value.toSet() - domain)

    private fun apply(domains: Set<String>) {
        prefs.edit().putStringSet(KEY, domains).apply()
        _domains.value = domains.sorted()
        blocklist = Blocklist(domains + Blocklist.DOH_DOMAINS)
    }
}
