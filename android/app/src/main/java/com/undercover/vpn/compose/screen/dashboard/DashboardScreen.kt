package com.undercover.vpn.compose.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.undercover.vpn.R
import com.undercover.vpn.compose.component.RemoteControlMenuItems
import com.undercover.vpn.compose.component.rememberRemoteServers
import com.undercover.vpn.compose.navigation.NewProfileArgs
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.constant.Status
import com.undercover.vpn.utils.RemoteControlManager
import kotlinx.coroutines.launch

data class CardRenderItem(val cards: List<CardGroup>, val isRow: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
    onToggleConnection: () -> Unit = {},
    onProfileOpen: (com.undercover.vpn.database.Profile) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val remoteServer by RemoteControlManager.remoteServer.collectAsState()
    val remoteConnected by RemoteControlManager.isConnected.collectAsState()
    val isRemote = remoteServer != null
    val remoteServers by rememberRemoteServers()
    var showOthersMenu by remember { mutableStateOf(false) }

    OverrideTopBar {
        // Empty to hide the standard top bar
    }

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Show dashboard settings bottom sheet
    if (uiState.showCardSettingsDialog) {
        DashboardSettingsBottomSheet(
            sheetState = sheetState,
            visibleCards = uiState.visibleCards,
            cardOrder = uiState.cardOrder,
            onToggleCard = viewModel::toggleCardVisibility,
            onReorderCards = viewModel::reorderCards,
            onResetOrder = viewModel::resetCardOrder,
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    viewModel.closeCardSettingsDialog()
                }
            },
        )
    }

    val importHandler = remember { com.undercover.vpn.compose.screen.configuration.ProfileImportHandler(context) }
    var showAddMenu by remember { mutableStateOf(false) }

    if (isRemote && !remoteConnected) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        val isConnected = serviceStatus == Status.Started || serviceStatus == Status.Starting
        
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isConnected) "IN COVER" else "UNDERCOVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = if (isConnected) "DOSSIER No. 0447" else "NEW DOSSIER",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            if (isConnected) {
                // Connected State
                Text(
                    text = "YOU ARE CURRENTLY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = uiState.selectedProfileName ?: "The Mapmaker",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                val locationSubtitle = remember(uiState.exitCity, uiState.exitCountry, uiState.ping, uiState.exitTimezone, uiState.exitIp) {
                    when {
                        uiState.exitCity.isNotEmpty() && uiState.exitCountry.isNotEmpty() ->
                            "${uiState.exitCity}, ${uiState.exitCountry} · ${uiState.ping} · ${uiState.exitTimezone}"
                        uiState.exitCity.isNotEmpty() ->
                            "${uiState.exitCity} · ${uiState.ping} · ${uiState.exitTimezone}"
                        uiState.exitIp.isNotEmpty() && uiState.exitIp != "Acquiring..." ->
                            "${uiState.exitIp} · ${uiState.ping} · ${uiState.exitTimezone}"
                        else ->
                            "Securing connection · ${uiState.ping}"
                    }
                }
                Text(
                    text = locationSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(32.dp))
                
                // Table Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("DETAIL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                    Text("REAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                    Text("SEEN AS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1.3f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
                
                // Table Rows
                val speedText = "↓ ${uiState.downlink}  ↑ ${uiState.uplink}"
                val rows = listOf(
                    Triple("EXIT IP", uiState.realIp, uiState.exitIp),
                    Triple("PING", "Direct", uiState.ping),
                    Triple("TIME ZONE", uiState.realTimezone, uiState.exitTimezone),
                    Triple("SPEED", "0 B/s", speedText),
                    Triple("LOCALE", uiState.realLocale, uiState.exitLocale)
                )
                
                rows.forEach { (detail, real, cover) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                        var isRevealed by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isRevealed = !isRevealed }
                        ) {
                            com.undercover.vpn.shared.ui.components.RedactedText(
                                text = real,
                                isRedacted = !isRevealed
                            )
                        }
                        Text(
                            text = cover,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1.3f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))
                }
                
            } else {
                // Disconnected State
                Spacer(modifier = Modifier.height(64.dp))
                Text(
                    text = stringResource(R.string.dossier_cover_established),
                    style = MaterialTheme.typography.headlineMedium.copy(
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
                        .clickable { viewModel.showProfilePickerSheet() }
                        .padding(vertical = 16.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.dossier_select_profile),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = uiState.selectedProfileName ?: "No Profile Selected",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    androidx.compose.material3.IconButton(onClick = { showAddMenu = true }) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Profile",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = showAddMenu,
                                        onDismissRequest = { showAddMenu = false }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Import from Clipboard", color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                showAddMenu = false
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                                if (!text.isNullOrBlank()) {
                                                    scope.launch {
                                                        when (val parseResult = importHandler.parseQRCode(text)) {
                                                            is com.undercover.vpn.compose.screen.configuration.ProfileImportHandler.QRCodeParseResult.RemoteProfile -> {
                                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                    onOpenNewProfile(
                                                                        com.undercover.vpn.compose.navigation.NewProfileArgs(
                                                                            importName = parseResult.name,
                                                                            importUrl = parseResult.url,
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                            is com.undercover.vpn.compose.screen.configuration.ProfileImportHandler.QRCodeParseResult.LocalProfile -> {
                                                                when (val result = importHandler.importFromQRCode(text)) {
                                                                    is com.undercover.vpn.compose.screen.configuration.ProfileImportHandler.ImportResult.Success -> {
                                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                            onProfileOpen(result.profile)
                                                                        }
                                                                    }
                                                                    is com.undercover.vpn.compose.screen.configuration.ProfileImportHandler.ImportResult.Error -> {
                                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                            android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            is com.undercover.vpn.compose.screen.configuration.ProfileImportHandler.QRCodeParseResult.Error -> {
                                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                    android.widget.Toast.makeText(context, parseResult.message, android.widget.Toast.LENGTH_LONG).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    android.widget.Toast.makeText(context, "Clipboard is empty", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Create Manually", color = MaterialTheme.colorScheme.onSurface) },
                                            onClick = {
                                                showAddMenu = false
                                                onOpenNewProfile(com.undercover.vpn.compose.navigation.NewProfileArgs())
                                            }
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Select",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (isConnected) {
                com.undercover.vpn.shared.ui.components.DossierButton(
                    text = stringResource(R.string.dossier_drop_cover),
                    isOutlined = false,
                    onClick = onToggleConnection
                )
            } else {
                com.undercover.vpn.shared.ui.components.DossierButton(
                    text = stringResource(R.string.dossier_go_undercover),
                    isOutlined = false,
                    onClick = {
                        // Open profile picker if none selected, else connect
                        if (uiState.selectedProfileId == null || uiState.selectedProfileId == 0L) {
                            viewModel.showProfilePickerSheet()
                        } else {
                            onToggleConnection()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "NO EMAIL · NO NAME · NO LOGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Process cards for rendering, grouping consecutive half-width cards into rows
 */
fun processCardsForRendering(
    cardOrder: List<CardGroup>,
    visibleCards: Set<CardGroup>,
    cardWidths: Map<CardGroup, CardWidth>,
): List<CardRenderItem> {
    val renderItems = mutableListOf<CardRenderItem>()
    val visibleOrderedCards = cardOrder.filter { visibleCards.contains(it) }

    var i = 0
    while (i < visibleOrderedCards.size) {
        val currentCard = visibleOrderedCards[i]
        val currentWidth = cardWidths[currentCard] ?: CardWidth.Full

        if (currentWidth == CardWidth.Half) {
            // Check if next card is also half-width
            if (i + 1 < visibleOrderedCards.size) {
                val nextCard = visibleOrderedCards[i + 1]
                val nextWidth = cardWidths[nextCard] ?: CardWidth.Full

                if (nextWidth == CardWidth.Half) {
                    // Group two half-width cards together
                    renderItems.add(
                        CardRenderItem(
                            cards = listOf(currentCard, nextCard),
                            isRow = true,
                        ),
                    )
                    i += 2
                    continue
                }
            }
            // Single half-width card
            renderItems.add(
                CardRenderItem(
                    cards = listOf(currentCard),
                    isRow = false,
                ),
            )
        } else {
            // Full-width card
            renderItems.add(
                CardRenderItem(
                    cards = listOf(currentCard),
                    isRow = false,
                ),
            )
        }
        i++
    }

    return renderItems
}

/**
 * Determine if a service-dependent card has data available to display.
 * This function is only relevant when the service is running.
 * Note: Profiles card is always available and should not use this function.
 */
fun isCardAvailableWhenServiceRunning(cardGroup: CardGroup, uiState: DashboardUiState): Boolean = when (cardGroup) {
    CardGroup.ClashMode -> uiState.clashModeVisible
    CardGroup.UploadTraffic -> uiState.trafficVisible
    CardGroup.DownloadTraffic -> uiState.trafficVisible
    CardGroup.Debug -> true // Debug info is always available when service is running
    CardGroup.Connections -> uiState.trafficVisible
    CardGroup.SystemProxy -> uiState.systemProxyVisible
    CardGroup.Profiles -> true // This shouldn't be called for Profiles, but return true for safety
}
