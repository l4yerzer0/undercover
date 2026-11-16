package com.undercover.vpn.compose.screen.log

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.undercover.vpn.Application
import com.undercover.vpn.R
import com.undercover.vpn.compat.WindowSizeClassCompat
import com.undercover.vpn.compat.isWidthAtLeastBreakpointCompat
import com.undercover.vpn.compose.component.RemoteControlMenuItems
import com.undercover.vpn.compose.component.rememberRemoteServers
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.constant.Status
import com.undercover.vpn.utils.RemoteControlManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    title: String? = null,
    viewModel: LogViewerViewModel? = null,
    showPause: Boolean = true,
    showClear: Boolean = true,
    showStatusInfo: Boolean = true,
    emptyMessage: String? = null,
    saveFilePrefix: String = "logs",
    onBack: (() -> Unit)? = null,
) {
    val resolvedViewModel = viewModel ?: viewModel<LogViewModel>()
    val uiState by resolvedViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isTablet = windowSizeClass.isWidthAtLeastBreakpointCompat(WindowSizeClassCompat.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val resolvedTitle = title ?: stringResource(R.string.title_log)
    val remoteServer by RemoteControlManager.remoteServer.collectAsState()
    val remoteServers by rememberRemoteServers()
    val emptyStateMessage = emptyMessage ?: stringResource(R.string.privilege_settings_hook_logs_empty)

    OverrideTopBar {
        // Dossier tactical UI replaces standard TopAppBar
    }

    // Handle back press in selection mode
    androidx.activity.compose.BackHandler(enabled = uiState.isSelectionMode) {
        resolvedViewModel.clearSelection()
    }

    // Track if user is at the bottom of the list
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
        }
    }

    // Re-enable auto-scroll when user reaches bottom
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            resolvedViewModel.setAutoScrollEnabled(true)
        }
    }

    // Detect user manual scroll to disable auto-scroll
    LaunchedEffect(listState) {
        var dragStartIndex: Int? = null
        var dragStartOffset: Int? = null

        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    dragStartIndex = listState.firstVisibleItemIndex
                    dragStartOffset = listState.firstVisibleItemScrollOffset
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    if (dragStartIndex != null && dragStartOffset != null) {
                        val currentIndex = listState.firstVisibleItemIndex
                        val currentOffset = listState.firstVisibleItemScrollOffset

                        val scrolledUp =
                            if (dragStartIndex != currentIndex) {
                                dragStartIndex!! > currentIndex
                            } else {
                                dragStartOffset!! > currentOffset
                            }

                        if (scrolledUp) {
                            resolvedViewModel.setAutoScrollEnabled(false)
                        }

                        dragStartIndex = null
                        dragStartOffset = null
                    }
                }
            }
        }
    }

    // Handle scroll to bottom requests from ViewModel
    val scrollToBottomTrigger by resolvedViewModel.scrollToBottomTrigger.collectAsState()
    LaunchedEffect(scrollToBottomTrigger) {
        if (scrollToBottomTrigger > 0 && uiState.logs.isNotEmpty()) {
            listState.animateScrollToItem(uiState.logs.size - 1)
        }
    }

    // Update service status in ViewModel
    LaunchedEffect(serviceStatus) {
        if (showStatusInfo) {
            resolvedViewModel.updateServiceStatus(serviceStatus)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Dossier Header Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Top Meta Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.content_description_back),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "BACK // LOGS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    } else {
                        Text(
                            text = "UNDERCOVER // SYSTEM LOGS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }

                    Text(
                        text = if (uiState.isPaused) "● PAUSED" else "● LIVE STREAM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (uiState.isPaused) Color(0xFFFFC107) else MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title and Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = resolvedTitle.uppercase(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )

                    // Tactical Action Buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showPause) {
                            IconButton(onClick = { resolvedViewModel.togglePause() }) {
                                Icon(
                                    imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (uiState.isPaused) "Resume" else "Pause",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        IconButton(onClick = { resolvedViewModel.toggleSearch() }) {
                            Icon(
                                imageVector = if (uiState.isSearchActive) Icons.Default.ExpandLess else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (uiState.isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }

                        IconButton(onClick = { resolvedViewModel.toggleOptionsMenu() }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
            }

            // Selection Mode Bar
            if (uiState.isSelectionMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { resolvedViewModel.clearSelection() }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Exit selection",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ВЫБРАНО: ${uiState.selectedLogIndices.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        IconButton(
                            onClick = {
                                val selectedText = resolvedViewModel.getSelectedLogsText()
                                if (selectedText.isNotEmpty()) {
                                    val clip = ClipData.newPlainText(resolvedTitle, selectedText)
                                    Application.clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                    resolvedViewModel.clearSelection()
                                }
                            },
                            enabled = uiState.selectedLogIndices.isNotEmpty(),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // Active Filter Indicator
            if (uiState.filterLogLevel != LogLevel.Default && !uiState.isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ФИЛЬТР УРОВНЯ: ${uiState.filterLogLevel.label.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "[СБРОСИТЬ]",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable { resolvedViewModel.setLogLevel(LogLevel.Default) },
                    )
                }
            }

            // Tactical Search Bar
            AnimatedVisibility(
                visible = uiState.isSearchActive,
                enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(250)) + fadeOut(tween(250)),
            ) {
                val focusRequester = remember { FocusRequester() }
                val focusManager = LocalFocusManager.current

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(0.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(0.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = { resolvedViewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            decorationBox = { innerTextField ->
                                if (uiState.searchQuery.isEmpty()) {
                                    Text(
                                        text = "Поиск по строкам лога...",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                }
                                innerTextField()
                            },
                        )
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { resolvedViewModel.updateSearchQuery("") },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            // Main Log Stream Content
            if (uiState.errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = uiState.errorTitle ?: "ERROR",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }
            } else if (uiState.logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "NO ACTIVE ENTRIES",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            text = if (showStatusInfo) {
                                when {
                                    remoteServer != null && !uiState.isConnected -> stringResource(R.string.remote_connecting)
                                    remoteServer != null -> stringResource(R.string.status_started)
                                    serviceStatus == Status.Started -> "Служба активна // Ожидание событий туннеля"
                                    serviceStatus == Status.Starting -> stringResource(R.string.status_starting)
                                    serviceStatus == Status.Stopping -> stringResource(R.string.status_stopping)
                                    else -> "Служба остановлена. Запустите подключение для просмотра логов."
                                }
                            } else {
                                emptyStateMessage
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                val bottomPadding = when {
                    showStartFab -> 88.dp
                    showStatusBar -> 74.dp
                    else -> 16.dp
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    itemsIndexed(
                        items = uiState.logs,
                        key = { _, log -> log.id },
                    ) { index, log ->
                        LogItem(
                            annotatedString = log.annotatedString,
                            index = index,
                            isSelected = uiState.selectedLogIndices.contains(index),
                            isSelectionMode = uiState.isSelectionMode,
                            onLongClick = {
                                if (!uiState.isSelectionMode) {
                                    resolvedViewModel.toggleSelectionMode()
                                    resolvedViewModel.toggleLogSelection(index)
                                }
                            },
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    resolvedViewModel.toggleLogSelection(index)
                                }
                            },
                        )
                    }
                }
            }
        } // Close Column

        // Options Menu
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 60.dp),
        ) {
            var expandedLogLevel by remember { mutableStateOf(false) }
            var expandedSave by remember { mutableStateOf(false) }

            val saveFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("text/plain"),
                onResult = { uri ->
                    uri?.let {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                val logsText = resolvedViewModel.getAllLogsText()
                                outputStream.write(logsText.toByteArray())
                                outputStream.flush()
                                Toast.makeText(context, R.string.success_logs_saved, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.failed_save_logs, e.message), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )

            DropdownMenu(
                expanded = uiState.isOptionsMenuOpen,
                onDismissRequest = {
                    resolvedViewModel.toggleOptionsMenu()
                    expandedLogLevel = false
                    expandedSave = false
                },
                modifier = Modifier
                    .widthIn(min = 220.dp)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
            ) {
                // Log Level section
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.log_level), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { expandedLogLevel = !expandedLogLevel },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { Icon(if (expandedLogLevel) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                )

                if (expandedLogLevel) {
                    LogLevel.entries.filter { it.priority > 1 }.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(text = level.label, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                resolvedViewModel.setLogLevel(level)
                                resolvedViewModel.toggleOptionsMenu()
                                expandedLogLevel = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (uiState.filterLogLevel == level) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (uiState.filterLogLevel == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                }

                // Save section
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.save), color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { expandedSave = !expandedSave },
                    leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { Icon(if (expandedSave) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                )

                if (expandedSave) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_to_clipboard), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            val logsText = resolvedViewModel.getAllLogsText()
                            if (logsText.isNotEmpty()) {
                                val clip = ClipData.newPlainText(resolvedTitle, logsText)
                                Application.clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                            }
                            resolvedViewModel.toggleOptionsMenu()
                            expandedSave = false
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp)) },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.save_to_file), color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val timestamp = dateFormat.format(Date())
                            saveFileLauncher.launch("${saveFilePrefix}_$timestamp.txt")
                            resolvedViewModel.toggleOptionsMenu()
                            expandedSave = false
                        },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp)) },
                    )

                    DropdownMenuItem(
                        text = { Text("Поделиться", color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            val logsText = resolvedViewModel.getAllLogsText()
                            if (logsText.isNotEmpty()) {
                                try {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, logsText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Поделиться"))
                                } catch (_: Exception) {
                                }
                            }
                            resolvedViewModel.toggleOptionsMenu()
                            expandedSave = false
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp)) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                }

                if (showClear) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clear_logs), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            resolvedViewModel.requestClearLogs()
                            resolvedViewModel.toggleOptionsMenu()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    )
                }

                if (showStatusInfo) {
                    RemoteControlMenuItems(
                        servers = remoteServers,
                        onAction = { resolvedViewModel.toggleOptionsMenu() },
                    )
                }
            }
        }

        // Scroll to bottom FAB
        val padFabVisible = isTablet && (showStartFab || showStatusBar)
        val fabBottomPadding = when {
            padFabVisible -> 20.dp + 64.dp + 16.dp
            showStartFab -> 88.dp
            showStatusBar -> 74.dp
            else -> 16.dp
        }
        val fabEndPadding = if (isTablet) 24.dp else 20.dp

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = fabBottomPadding, end = fabEndPadding),
        ) {
            AnimatedVisibility(
                visible = !isAtBottom && !uiState.isSelectionMode && uiState.logs.isNotEmpty(),
                enter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) scaleIn() else fadeIn(),
                exit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) scaleOut() else fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { resolvedViewModel.scrollToBottom() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.content_description_scroll_to_bottom),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogItem(
    annotatedString: androidx.compose.ui.text.AnnotatedString,
    index: Int,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent,
            )
            .padding(horizontal = 24.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp, top = 2.dp).size(16.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
            }
            Text(
                text = annotatedString,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
