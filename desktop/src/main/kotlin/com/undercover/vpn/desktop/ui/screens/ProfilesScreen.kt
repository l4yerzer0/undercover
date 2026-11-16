package com.undercover.vpn.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.ProfileType
import com.undercover.vpn.desktop.ui.DossierMint
import com.undercover.vpn.desktop.ui.DossierRed
import com.undercover.vpn.shared.ui.components.DossierButton

@Composable
fun ProfilesScreen(
    profiles: List<DesktopProfile>,
    selectedProfileId: String?,
    onSelectProfile: (DesktopProfile) -> Unit,
    onSelectNode: (DesktopProfile, String) -> Unit,
    onPingProfile: (DesktopProfile) -> Unit,
    onSyncProfile: (DesktopProfile) -> Unit,
    onDeleteProfile: (DesktopProfile) -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONFIGURED PROFILES (${profiles.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        )

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NO PROFILES FOUND",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import a subscription URL or Happ / VLESS link",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(profiles, key = { it.id }) { prof ->
                    val isSelected = prof.id == selectedProfileId

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProfile(prof) }
                            .padding(vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prof.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) DossierMint else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val subtitle = when (prof.type) {
                                    ProfileType.REMOTE_SUBSCRIPTION -> "SUBSCRIPTION · ${prof.outbounds.size} NODES"
                                    ProfileType.SINGLE_LINK -> "DIRECT NODE · ${prof.outbounds.firstOrNull()?.type ?: "PROXY"}"
                                    ProfileType.CUSTOM_CONFIG -> "CUSTOM CONFIG"
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    letterSpacing = 1.sp
                                )
                            }

                            // Actions row
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (prof.latencyMs != null) {
                                    Text(
                                        text = "${prof.latencyMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DossierMint,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                }

                                Text(
                                    text = "PING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier
                                        .clickable { onPingProfile(prof) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )

                                if (prof.type == ProfileType.REMOTE_SUBSCRIPTION) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SYNC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier
                                            .clickable { onSyncProfile(prof) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "DEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DossierRed,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier
                                        .clickable { onDeleteProfile(prof) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Active node tag if subscription
                        if (prof.selectedOutboundTag != null && prof.outbounds.size > 1) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ACTIVE NODE: ${prof.selectedOutboundTag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DossierButton(
            text = "+ IMPORT PROFILE",
            isOutlined = true,
            onClick = onOpenImport
        )
    }
}
