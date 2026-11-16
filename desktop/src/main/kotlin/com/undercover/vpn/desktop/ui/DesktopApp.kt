package com.undercover.vpn.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.undercover.vpn.desktop.core.PingManager
import com.undercover.vpn.desktop.core.ProfileImporter
import com.undercover.vpn.desktop.core.SingBoxCoreManager
import com.undercover.vpn.desktop.core.StorageManager
import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.VpnState
import com.undercover.vpn.desktop.ui.components.ImportDialog
import com.undercover.vpn.desktop.ui.screens.DashboardScreen
import com.undercover.vpn.desktop.ui.screens.LogsScreen
import com.undercover.vpn.desktop.ui.screens.ProfilesScreen
import com.undercover.vpn.desktop.ui.screens.SettingsScreen
import com.undercover.vpn.shared.ui.components.DossierBottomNav
import com.undercover.vpn.shared.ui.components.DossierNavItem
import kotlinx.coroutines.launch

@Composable
fun DesktopApp() {
    UndercoverDesktopTheme {
        var settings by remember { mutableStateOf(StorageManager.loadSettings()) }
        val profiles = remember { mutableStateListOf<DesktopProfile>() }
        var selectedProfileId by remember { mutableStateOf(settings.selectedProfileId) }
        var currentTabIndex by remember { mutableIntStateOf(0) }
        var showImportDialog by remember { mutableStateOf(false) }

        val vpnState by SingBoxCoreManager.vpnState.collectAsState()
        val scope = rememberCoroutineScope()

        // Load initial profiles & ensure sing-box binary on startup
        LaunchedEffect(Unit) {
            profiles.clear()
            profiles.addAll(StorageManager.loadProfiles())
            if (selectedProfileId == null && profiles.isNotEmpty()) {
                selectedProfileId = profiles.first().id
            }
            // Silently ensure embedded sing-box is extracted and ready
            SingBoxCoreManager.ensureSingBoxBinary(settings.customSingBoxPath)
        }

        val selectedProfile = remember(profiles, selectedProfileId) {
            profiles.firstOrNull { it.id == selectedProfileId }
        }

        val toggleConnection: () -> Unit = {
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

        val navItems = listOf(
            DossierNavItem("DASHBOARD"),
            DossierNavItem("PROFILES"),
            DossierNavItem("LOGS"),
            DossierNavItem("SETTINGS")
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Active Screen Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentTabIndex) {
                        0 -> DashboardScreen(
                            vpnState = vpnState,
                            selectedProfile = selectedProfile,
                            profiles = profiles,
                            onSelectProfile = { prof ->
                                selectedProfileId = prof.id
                                settings = settings.copy(selectedProfileId = prof.id)
                                StorageManager.saveSettings(settings)
                            },
                            onToggleConnection = toggleConnection,
                            onOpenImport = { showImportDialog = true }
                        )

                        1 -> ProfilesScreen(
                            profiles = profiles,
                            selectedProfileId = selectedProfileId,
                            onSelectProfile = { prof ->
                                selectedProfileId = prof.id
                                settings = settings.copy(selectedProfileId = prof.id)
                                StorageManager.saveSettings(settings)
                            },
                            onSelectNode = { prof, nodeTag ->
                                val idx = profiles.indexOfFirst { it.id == prof.id }
                                if (idx >= 0) {
                                    profiles[idx] = prof.copy(selectedOutboundTag = nodeTag)
                                    StorageManager.saveProfiles(profiles)
                                }
                            },
                            onPingProfile = { prof ->
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
                            onSyncProfile = { prof ->
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
                            onDeleteProfile = { prof ->
                                profiles.remove(prof)
                                StorageManager.saveProfiles(profiles)
                                if (selectedProfileId == prof.id) {
                                    selectedProfileId = profiles.firstOrNull()?.id
                                    settings = settings.copy(selectedProfileId = selectedProfileId)
                                    StorageManager.saveSettings(settings)
                                }
                            },
                            onOpenImport = { showImportDialog = true }
                        )

                        2 -> LogsScreen()

                        3 -> SettingsScreen(
                            settings = settings,
                            onSaveSettings = { updated ->
                                settings = updated
                                StorageManager.saveSettings(updated)
                            }
                        )
                    }
                }

                // Bottom Nav Bar matching mobile app
                DossierBottomNav(
                    items = navItems,
                    selectedIndex = currentTabIndex,
                    onItemSelected = { currentTabIndex = it }
                )
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
                        currentTabIndex = 0
                    }
                )
            }
        }
    }
}
