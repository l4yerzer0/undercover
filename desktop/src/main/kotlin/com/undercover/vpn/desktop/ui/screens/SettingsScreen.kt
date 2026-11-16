package com.undercover.vpn.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.core.SingBoxCoreManager
import com.undercover.vpn.desktop.model.DesktopSettings
import com.undercover.vpn.desktop.model.ProxyMode
import com.undercover.vpn.desktop.ui.DossierMint
import com.undercover.vpn.shared.ui.components.DossierSwitch

@Composable
fun SettingsScreen(
    settings: DesktopSettings,
    onSaveSettings: (DesktopSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember(settings) { mutableStateOf(settings.proxyMode) }
    var socksPortStr by remember(settings) { mutableStateOf(settings.socksPort.toString()) }
    var httpPortStr by remember(settings) { mutableStateOf(settings.httpPort.toString()) }
    var minimizeToTray by remember(settings) { mutableStateOf(settings.minimizeToTray) }
    var autoConnect by remember(settings) { mutableStateOf(settings.autoConnect) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Header
        Text(
            text = "SETTINGS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Routing Mode Item
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ROUTING MODE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (mode == ProxyMode.SYSTEM_PROXY) "System Proxy (SOCKS / HTTP)" else "TUN Virtual Adapter (Full VPN)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (mode == ProxyMode.SYSTEM_PROXY) "Zero sudo/admin rights required. Instant configuration." else "Full system tunnel. Requires root privileges.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Text(
                    text = "TOGGLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clickable {
                            val next = if (mode == ProxyMode.SYSTEM_PROXY) ProxyMode.TUN_VPN else ProxyMode.SYSTEM_PROXY
                            mode = next
                            onSaveSettings(settings.copy(proxyMode = next))
                        }
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Minimize to Tray
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "MINIMIZE TO SYSTEM TRAY",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Keep running in background when window is closed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            DossierSwitch(
                checked = minimizeToTray,
                onCheckedChange = {
                    minimizeToTray = it
                    onSaveSettings(settings.copy(minimizeToTray = it))
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Ports row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LOCAL SOCKS5 PORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = socksPortStr,
                    onValueChange = {
                        socksPortStr = it
                        val p = it.toIntOrNull()
                        if (p != null && p in 1024..65535) {
                            onSaveSettings(settings.copy(socksPort = p))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LOCAL HTTP PROXY PORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = httpPortStr,
                    onValueChange = {
                        httpPortStr = it
                        val p = it.toIntOrNull()
                        if (p != null && p in 1024..65535) {
                            onSaveSettings(settings.copy(httpPort = p))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Core status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ENGINE CORE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Embedded sing-box (v1.14.4)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "READY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 1.sp
            )
        }
    }
}
