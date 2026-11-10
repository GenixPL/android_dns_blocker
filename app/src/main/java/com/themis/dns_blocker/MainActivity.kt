package com.themis.dns_blocker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.themis.dns_blocker.ui.theme.Dns_blockerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BlocklistStore.init(this)
        setContent {
            Dns_blockerTheme {
                var screen by remember { mutableStateOf(Screen.Blocked) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            Screen.entries.forEach {
                                NavigationBarItem(
                                    selected = screen == it,
                                    onClick = { screen = it },
                                    icon = {},
                                    label = { Text(it.label) },
                                    alwaysShowLabel = true,
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        VpnToggle()
                        when (screen) {
                            Screen.Blocked -> BlocklistEditor(modifier = Modifier.weight(1f))
                            Screen.Apps -> AllowedAppsScreen(modifier = Modifier.weight(1f))
                            Screen.Domains -> ExcludedDomainsScreen(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private enum class Screen(val label: String) {
    Blocked("Blocked"),
    Apps("Allowed apps"),
    Domains("Excluded domains"),
}

private const val PRECEDENCE_NOTE =
    "Allowed apps are checked first: they bypass the VPN entirely, so neither the blocklist nor " +
        "the excluded domains apply to them. Excluded domains only matter for all other apps."

@Composable
fun VpnToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val running by DnsVpnService.running.collectAsState()

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) DnsVpnService.start(context)
    }
    // Notifications are optional: the foreground service runs even if the user denies this.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val intent = VpnService.prepare(context)
        if (intent == null) DnsVpnService.start(context) else consent.launch(intent)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (running) "DNS blocking is on" else "DNS blocking is off")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (running) DnsVpnService.stop(context)
                else notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) {
                Text(if (running) "Stop" else "Start")
            }
            // Always-on VPN is a per-VPN setting in system settings; it restarts us after reboot.
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }) {
                Text("Always-on VPN")
            }
        }
    }
}

@Composable
fun BlocklistEditor(modifier: Modifier = Modifier) {
    val domains by BlocklistStore.domains.collectAsState()
    DomainEditor(
        title = "Blocked domains (subdomains included)",
        domains = domains,
        onAdd = BlocklistStore::add,
        onRemove = BlocklistStore::remove,
        modifier = modifier,
    )
}

@Composable
fun ExcludedDomainsScreen(modifier: Modifier = Modifier) {
    val domains by BlocklistStore.excludedDomains.collectAsState()
    DomainEditor(
        title = "Excluded domains: never blocked, even if a parent domain is (e.g. music.youtube.com)",
        note = PRECEDENCE_NOTE,
        domains = domains,
        onAdd = BlocklistStore::addExcluded,
        onRemove = BlocklistStore::removeExcluded,
        modifier = modifier,
    )
}

@Composable
private fun DomainEditor(
    title: String,
    domains: List<String>,
    onAdd: (String) -> Boolean,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title)
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; invalid = false },
                label = { Text("example.com") },
                isError = invalid,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                if (onAdd(input)) input = "" else invalid = true
            }) {
                Text("Add")
            }
        }
        LazyColumn {
            items(domains, key = { it }) { domain ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(domain)
                    TextButton(onClick = { onRemove(domain) }) { Text("Remove") }
                }
            }
        }
    }
}

private data class AppEntry(val pkg: String, val label: String)

private fun launchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != context.packageName }
        .distinctBy { it.pkg }
}

@Composable
fun AllowedAppsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val running by DnsVpnService.running.collectAsState()
    val allowed by BlocklistStore.allowedApps.collectAsState()
    val installed = remember { launchableApps(context) }
    // Allowed apps that are no longer installed stay listed (by package name) so they can be removed.
    val entries = remember(installed, allowed) {
        (installed + allowed.filter { pkg -> installed.none { it.pkg == pkg } }.map { AppEntry(it, it) })
            .sortedWith(compareByDescending<AppEntry> { it.pkg in allowed }.thenBy { it.label.lowercase() })
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Allowed apps: their traffic bypasses the VPN and is never blocked")
        Text(PRECEDENCE_NOTE, style = MaterialTheme.typography.bodySmall)
        if (running) Text("Changes restart the VPN.", style = MaterialTheme.typography.bodySmall)
        LazyColumn {
            items(entries, key = { it.pkg }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(
                        checked = app.pkg in allowed,
                        onCheckedChange = {
                            BlocklistStore.setAppAllowed(app.pkg, it)
                            if (running) DnsVpnService.reload(context)
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VpnTogglePreview() {
    Dns_blockerTheme {
        VpnToggle()
    }
}
