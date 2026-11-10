package com.themis.dns_blocker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's editable lists, persisted in SharedPreferences: blocked domains, excluded domains (carved out of
 * the blocked ones) and allowed apps (bypass the VPN entirely). [blocklist] is what the VPN service
 * matches against (user domains plus the fixed DoH-bypass domains) and updates as soon as a list is edited.
 * The allowed apps are only read when the VPN is established, so changing them needs a restart.
 */
object BlocklistStore {
    private const val PREFS = "blocklist"
    private const val KEY = "domains"
    private const val KEY_EXCLUDED = "excluded_domains"
    private const val KEY_APPS = "allowed_apps"
    private val DEFAULT_ALLOWED_APPS = setOf("com.google.android.apps.youtube.music")

    private lateinit var prefs: SharedPreferences
    private val _domains = MutableStateFlow<List<String>>(emptyList())

    /** User-editable domains, sorted. */
    val domains: StateFlow<List<String>> = _domains

    private val _excluded = MutableStateFlow<List<String>>(emptyList())

    /** Domains let through even when a blocked parent domain matches, sorted. */
    val excludedDomains: StateFlow<List<String>> = _excluded

    private val _apps = MutableStateFlow<Set<String>>(emptySet())

    /** Package names whose traffic bypasses the VPN. */
    val allowedApps: StateFlow<Set<String>> = _apps

    @Volatile
    var blocklist = Blocklist(Blocklist.DOH_DOMAINS)
        private set

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _apps.value = prefs.getStringSet(KEY_APPS, null) ?: DEFAULT_ALLOWED_APPS
        _excluded.value = (prefs.getStringSet(KEY_EXCLUDED, null) ?: Blocklist.DEFAULT_ALLOWED.toSet()).sorted()
        apply(prefs.getStringSet(KEY, null) ?: Blocklist.DEFAULT_DOMAINS.toSet())
    }

    /** Returns false if [input] isn't a valid domain. */
    @Synchronized
    fun addExcluded(input: String): Boolean {
        val domain = Blocklist.parseDomain(input) ?: return false
        setExcluded(_excluded.value.toSet() + domain)
        return true
    }

    @Synchronized
    fun removeExcluded(domain: String) = setExcluded(_excluded.value.toSet() - domain)

    @Synchronized
    fun setAppAllowed(pkg: String, allowed: Boolean) {
        val apps = if (allowed) _apps.value + pkg else _apps.value - pkg
        prefs.edit().putStringSet(KEY_APPS, apps).apply()
        _apps.value = apps
    }

    private fun setExcluded(domains: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED, domains).apply()
        _excluded.value = domains.sorted()
        rebuild()
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
        rebuild()
    }

    private fun rebuild() {
        blocklist = Blocklist(_domains.value + Blocklist.DOH_DOMAINS, _excluded.value)
    }
}
