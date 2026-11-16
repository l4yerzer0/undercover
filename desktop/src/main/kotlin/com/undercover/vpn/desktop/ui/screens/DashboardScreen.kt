package com.undercover.vpn.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.VpnState
import com.undercover.vpn.desktop.ui.DossierMint
import com.undercover.vpn.shared.ui.components.DossierButton
import com.undercover.vpn.shared.ui.components.RedactedText

@Composable
fun DashboardScreen(
    vpnState: VpnState,
    selectedProfile: DesktopProfile?,
    profiles: List<DesktopProfile>,
    onSelectProfile: (DesktopProfile) -> Unit,
    onToggleConnection: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting
    var showProfileDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Status Top Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isConnected) "CONNECTED" else if (isConnecting) "CONNECTING..." else "UNDERCOVER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        if (vpnState is VpnState.Connected) {
            // Connected State
            Text(
                text = "COVER ESTABLISHED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = vpnState.profileName,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${vpnState.nodeName} · SOCKS 127.0.0.1:${vpnState.localSocksPort} · HTTP 127.0.0.1:${vpnState.localHttpPort}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Dossier Detail Table
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "DETAIL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "REAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1.2f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "SEEN AS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1.5f),
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )

            val tableRows = listOf(
                Triple("EXIT IP", "192.168.1.1", selectedProfile?.outbounds?.firstOrNull()?.server?.ifEmpty { "185.220.101.4" } ?: "Encrypted"),
                Triple("PING", "Direct", if (selectedProfile?.latencyMs != null) "${selectedProfile.latencyMs} ms" else "Active"),
                Triple("TIME ZONE", "Local Time", "UTC / Europe"),
                Triple("TUNNEL", "Direct ISP", "sing-box (TLS Reality/Hysteria2)"),
                Triple("PROTECTION", "Unprotected", "ENCRYPTED & OBFUSCATED")
            )

            tableRows.forEach { (detail, real, cover) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                        letterSpacing = 1.sp
                    )

                    var isRevealed by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .clickable { isRevealed = !isRevealed }
                    ) {
                        RedactedText(
                            text = real,
                            isRedacted = !isRevealed
                        )
                    }

                    Text(
                        text = cover,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                )
            }

        } else {
            // Disconnected State
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "DISCONNECTED",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Profile Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showProfileDropdown = true }
                    .padding(vertical = 16.dp)
            ) {
                Column {
                    Text(
                        text = "SELECT PROFILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = selectedProfile?.name ?: "No Profile Selected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenImport) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Profile",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    )
                }

                DropdownMenu(
                    expanded = showProfileDropdown,
                    onDismissRequest = { showProfileDropdown = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    profiles.forEach { prof ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    prof.name,
                                    color = if (prof.id == selectedProfile?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontWeight = if (prof.id == selectedProfile?.id) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectProfile(prof)
                                showProfileDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Main Bottom Action Button
        if (isConnected) {
            DossierButton(
                text = "DROP COVER",
                isOutlined = false,
                onClick = onToggleConnection
            )
        } else {
            DossierButton(
                text = if (isConnecting) "ESTABLISHING COVER..." else "GO UNDERCOVER",
                isOutlined = false,
                onClick = {
                    if (selectedProfile == null && profiles.isNotEmpty()) {
                        onSelectProfile(profiles.first())
                    }
                    onToggleConnection()
                }
            )
        }
    }
}
