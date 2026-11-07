package com.themis.dns_blocker

import android.Manifest
import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
        setContent {
            Dns_blockerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VpnToggle(modifier = Modifier.padding(innerPadding))
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (running) "DNS blocking is on" else "DNS blocking is off")
        Button(onClick = {
            if (running) DnsVpnService.stop(context)
            else notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }) {
            Text(if (running) "Stop" else "Start")
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
