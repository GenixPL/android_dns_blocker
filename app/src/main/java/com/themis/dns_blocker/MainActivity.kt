package com.themis.dns_blocker

import android.Manifest
import android.app.Activity
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        VpnToggle()
                        BlocklistEditor(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

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
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Blocked domains (subdomains included)")
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
                if (BlocklistStore.add(input)) input = "" else invalid = true
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
                    TextButton(onClick = { BlocklistStore.remove(domain) }) { Text("Remove") }
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
