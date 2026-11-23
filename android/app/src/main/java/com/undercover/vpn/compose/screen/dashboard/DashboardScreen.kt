package com.undercover.vpn.compose.screen.dashboard

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.undercover.vpn.compose.component.UpdateAvailableDialog
import com.undercover.vpn.compose.component.rememberRemoteServers
import com.undercover.vpn.compose.navigation.NewProfileArgs
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.constant.Status
import com.undercover.vpn.update.UpdateCheckException
import com.undercover.vpn.update.UpdateState
import com.undercover.vpn.utils.RemoteControlManager
import com.undercover.vpn.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var showNodePickerSheet by remember { mutableStateOf(false) }

    val hasUpdate by UpdateState.hasUpdate
    val updateInfo by UpdateState.updateInfo
    val isChecking by UpdateState.isChecking
    var showUpdateAvailableDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConnected) "CONNECTED" else "UNDERCOVER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        letterSpacing = 1.5.sp
                    )
                }

                // Update Icon Button
                IconButton(
                    onClick = {
                        if (hasUpdate && updateInfo != null) {
                            showUpdateAvailableDialog = true
                        } else if (!isChecking) {
                            scope.launch {
                                UpdateState.isChecking.value = true
                                try {
                                    val result = withContext(Dispatchers.IO) { Vendor.checkUpdateAsync() }
                                    UpdateState.setUpdate(result)
                                    if (result == null) {
                                        Toast.makeText(context, context.getString(R.string.no_updates_available), Toast.LENGTH_SHORT).show()
                                    } else {
                                        showUpdateAvailableDialog = true
                                    }
                                } catch (_: UpdateCheckException.TrackNotSupported) {
                                    UpdateState.setUpdate(null)
                                    Toast.makeText(context, context.getString(R.string.update_track_not_supported), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    UpdateState.setUpdate(null)
                                    Toast.makeText(context, e.message ?: "Update check failed", Toast.LENGTH_SHORT).show()
                                } finally {
                                    UpdateState.isChecking.value = false
                                }
                            }
                        }
                    }
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (hasUpdate) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = stringResource(R.string.update),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = stringResource(R.string.check_update),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
            if (isConnected) {
                // Connected State
                Text(
                    text = stringResource(R.string.dossier_cover_established),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = uiState.selectedProfileName ?: "Default",
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
                    text = stringResource(R.string.dossier_disconnected),
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

                val activeNodesInfo = uiState.profileNodesMap[uiState.selectedProfileId]
                if (activeNodesInfo != null && activeNodesInfo.nodes.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNodePickerSheet = true }
                            .padding(vertical = 12.dp)
                    ) {
                        Column {
                            Text(
                                text = "АКТИВНЫЙ СЕРВЕР (${activeNodesInfo.nodes.size})",
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
                                    text = activeNodesInfo.selectedNodeTag ?: activeNodesInfo.nodes.first().tag,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Select Server",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                        }
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
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    val activeNodesForSheet = uiState.profileNodesMap[uiState.selectedProfileId]
    if (showNodePickerSheet && activeNodesForSheet != null && activeNodesForSheet.nodes.isNotEmpty()) {
        val nodeSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showNodePickerSheet = false },
            sheetState = nodeSheetState,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "ВЫБОР СЕРВЕРА (${activeNodesForSheet.nodes.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = uiState.selectedProfileName ?: "ПОДПИСКА",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(activeNodesForSheet.nodes.size) { index ->
                        val node = activeNodesForSheet.nodes[index]
                        val isNodeSelected = node.tag == activeNodesForSheet.selectedNodeTag
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isNodeSelected) 1.5.dp else 1.dp,
                                    color = if (isNodeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                )
                                .background(
                                    if (isNodeSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                )
                                .clickable {
                                    viewModel.selectProfileNode(uiState.selectedProfileId, node.tag)
                                    showNodePickerSheet = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (isNodeSelected) "[ X ]" else "[   ]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = if (isNodeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = node.tag,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isNodeSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                        ),
                                        color = if (isNodeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    if (node.server.isNotEmpty() && node.port > 0) {
                                        Text(
                                            text = "${node.server}:${node.port}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = node.type,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showUpdateAvailableDialog && updateInfo != null) {
        UpdateAvailableDialog(
            updateInfo = updateInfo!!,
            onDismiss = { showUpdateAvailableDialog = false },
            onUpdate = {
                showDownloadDialog = true
                downloadError = null
                downloadJob = scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            Vendor.downloadAndInstall(context, updateInfo!!.downloadUrl)
                        }
                        showDownloadDialog = false
                    } catch (e: Exception) {
                        downloadError = e.message
                    }
                }
            },
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update)) },
            text = {
                Column {
                    if (downloadError != null) {
                        Text(
                            downloadError!!,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        val progress by UpdateState.downloadProgress
                        val progressValue = progress
                        Column {
                            if (progressValue != null) {
                                Text("${stringResource(R.string.downloading)} ${(progressValue * 100).toInt()}%")
                            } else {
                                Text(stringResource(R.string.downloading))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (progressValue != null) {
                                LinearProgressIndicator(
                                    progress = { progressValue },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadJob?.cancel()
                        downloadJob = null
                        showDownloadDialog = false
                        downloadError = null
                        UpdateState.downloadProgress.value = null
                    },
                ) {
                    Text(stringResource(if (downloadError != null) R.string.ok else android.R.string.cancel))
                }
            },
        )
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
