package com.undercover.vpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.core.PingManager
import com.undercover.vpn.desktop.core.ProfileImporter
import com.undercover.vpn.desktop.core.SingBoxCoreManager
import com.undercover.vpn.desktop.core.StorageManager
import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.DesktopSettings
import com.undercover.vpn.desktop.model.VpnState
import com.undercover.vpn.desktop.ui.components.*
import kotlinx.coroutines.launch

enum class DesktopTab(val label: String) {
    PROTECTION("PROTECTION"),
    SERVERS("SERVERS & SUBSCRIPTIONS"),
    LOGS("TERMINAL LOGS"),
    SETTINGS("SETTINGS")
}

@Composable
fun DesktopApp() {
    var settings by remember { mutableStateOf(StorageManager.loadSettings()) }
    val profiles = remember { mutableStateListOf<DesktopProfile>() }
    var selectedProfileId by remember { mutableStateOf(settings.selectedProfileId) }
    var currentTab by remember { mutableStateOf(DesktopTab.PROTECTION) }
    var showImportDialog by remember { mutableStateOf(false) }

    val vpnState by SingBoxCoreManager.vpnState.collectAsState()
    val scope = rememberCoroutineScope()

    // Load initial profiles
    LaunchedEffect(Unit) {
        profiles.clear()
        profiles.addAll(StorageManager.loadProfiles())
        if (selectedProfileId == null && profiles.isNotEmpty()) {
            selectedProfileId = profiles.first().id
        }
    }

    val selectedProfile = remember(profiles, selectedProfileId) {
        profiles.firstOrNull { it.id == selectedProfileId }
    }

    // Toggle connection function
    val toggleConnection = {
        scope.launch {
            if (vpnState is VpnState.Connected || vpnState is VpnState.Connecting) {
                SingBoxCoreManager.stop()
            } else {
                selectedProfile?.let { prof ->
                    SingBoxCoreManager.start(prof, settings)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D0F))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
                    .background(Color(0xFF161B22), RoundedCornerShape(6.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "UNDERCOVER",
                        color = Color(0xFF00FF66),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DESKTOP v0.3",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Nav Tabs
                Row {
                    DesktopTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        val tabColor = if (isSelected) Color(0xFF00FF66) else Color(0xFF8B949E)
                        Box(
                            modifier = Modifier
                                .clickable { currentTab = tab }
                                .then(
                                    if (isSelected) Modifier
                                        .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .background(Color(0xFF00FF66).copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                    else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tab.label,
                                color = tabColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Body Content based on active tab
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentTab) {
                    DesktopTab.PROTECTION -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            HeroConnectionCard(
                                vpnState = vpnState,
                                selectedProfileName = selectedProfile?.name,
                                proxyMode = settings.proxyMode,
                                onToggleConnection = { toggleConnection() }
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Recent Logs preview
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                LogsView()
                            }
                        }
                    }

                    DesktopTab.SERVERS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Action bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CONFIGURED PROFILES (${profiles.size})",
                                    color = Color(0xFF8B949E),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Row {
                                    // Ping all button
                                    Text(
                                        text = "⚡ PING ALL",
                                        color = Color(0xFF58A6FF),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .clickable {
                                                profiles.forEachIndexed { idx, p ->
                                                    scope.launch {
                                                        val targetNode = p.outbounds.firstOrNull()
                                                        if (targetNode != null && targetNode.server.isNotEmpty()) {
                                                            val lat = PingManager.ping(targetNode.server, targetNode.port)
                                                            profiles[idx] = p.copy(latencyMs = lat)
                                                            StorageManager.saveProfiles(profiles)
                                                        }
                                                    }
                                                }
                                            }
                                            .border(1.dp, Color(0xFF58A6FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Import button
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFF00FF66), RoundedCornerShape(4.dp))
                                            .background(Color(0xFF00FF66).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .clickable { showImportDialog = true }
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "+ IMPORT LINK / SUBSCRIPTION",
                                            color = Color(0xFF00FF66),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (profiles.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
                                        .background(Color(0xFF161B22), RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "NO PROFILES CONFIGURED",
                                            color = Color(0xFF8B949E),
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Click '+ IMPORT LINK / SUBSCRIPTION' to add your keys or subscription",
                                            color = Color(0xFF6E7681),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(profiles, key = { it.id }) { prof ->
                                        ProfileListCard(
                                            profile = prof,
                                            isSelected = prof.id == selectedProfileId,
                                            onSelect = {
                                                selectedProfileId = prof.id
                                                settings = settings.copy(selectedProfileId = prof.id)
                                                StorageManager.saveSettings(settings)
                                            },
                                            onSelectNode = { nodeTag ->
                                                val idx = profiles.indexOfFirst { it.id == prof.id }
                                                if (idx >= 0) {
                                                    profiles[idx] = prof.copy(selectedOutboundTag = nodeTag)
                                                    StorageManager.saveProfiles(profiles)
                                                }
                                            },
                                            onPing = {
                                                scope.launch {
                                                    val node = prof.outbounds.firstOrNull { it.tag == prof.selectedOutboundTag }
                                                        ?: prof.outbounds.firstOrNull()
                                                    if (node != null && node.server.isNotEmpty()) {
                                                        val lat = PingManager.ping(node.server, node.port)
                                                        val idx = profiles.indexOfFirst { it.id == prof.id }
                                                        if (idx >= 0) {
                                                            profiles[idx] = prof.copy(latencyMs = lat)
                                                            StorageManager.saveProfiles(profiles)
                                                        }
                                                    }
                                                }
                                            },
                                            onUpdate = {
                                                scope.launch {
                                                    try {
                                                        val updated = ProfileImporter.updateSubscription(prof)
                                                        val idx = profiles.indexOfFirst { it.id == prof.id }
                                                        if (idx >= 0) {
                                                            profiles[idx] = updated
                                                            StorageManager.saveProfiles(profiles)
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            },
                                            onDelete = {
                                                profiles.remove(prof)
                                                StorageManager.saveProfiles(profiles)
                                                if (selectedProfileId == prof.id) {
                                                    selectedProfileId = profiles.firstOrNull()?.id
                                                    settings = settings.copy(selectedProfileId = selectedProfileId)
                                                    StorageManager.saveSettings(settings)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DesktopTab.LOGS -> {
                        LogsView()
                    }

                    DesktopTab.SETTINGS -> {
                        SettingsView(
                            settings = settings,
                            onSaveSettings = { updated ->
                                settings = updated
                                StorageManager.saveSettings(updated)
                            }
                        )
                    }
                }
            }
        }

        // Import Dialog
        if (showImportDialog) {
            ImportDialog(
                onDismiss = { showImportDialog = false },
                onProfileImported = { newProf ->
                    profiles.add(0, newProf)
                    StorageManager.saveProfiles(profiles)
                    selectedProfileId = newProf.id
                    settings = settings.copy(selectedProfileId = newProf.id)
                    StorageManager.saveSettings(settings)
                    showImportDialog = false
                    currentTab = DesktopTab.PROTECTION
                }
            )
        }
    }
}
