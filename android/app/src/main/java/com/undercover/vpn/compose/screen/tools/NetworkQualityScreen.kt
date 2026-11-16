package com.undercover.vpn.compose.screen.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.undercover.vpn.shared.ui.components.DossierRadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.undercover.vpn.R
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.constant.Status
import com.undercover.vpn.shared.ui.components.DossierButton
import com.undercover.vpn.utils.RemoteControlManager
import io.nekohasekai.libbox.Libbox

@Composable
fun NetworkQualityScreen(
    navController: NavController,
    serviceStatus: Status = Status.Stopped,
    viewModel: NetworkQualityViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val remoteServer by RemoteControlManager.remoteServer.collectAsState()
    val remoteConnected by RemoteControlManager.isConnected.collectAsState()
    val serviceAvailable = remoteServer != null || serviceStatus == Status.Started
    val vpnRunning = if (remoteServer != null) remoteConnected else serviceStatus == Status.Started
    val context = LocalContext.current

    var showConfigURLDialog by remember { mutableStateOf(false) }
    var showMaxRuntimeDialog by remember { mutableStateOf(false) }
    var showTechDetails by remember { mutableStateOf(false) }

    OverrideTopBar {
        // Dossier tactical UI replaces standard TopAppBar
    }

    LaunchedEffect(vpnRunning) {
        if (!vpnRunning) {
            viewModel.onVpnDisconnected()
        }
    }

    val selectedOutboundResult = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("selected_outbound", state.selectedOutbound)
        ?.collectAsState()
    LaunchedEffect(selectedOutboundResult?.value) {
        selectedOutboundResult?.value?.let { viewModel.selectOutbound(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (state.isRunning) {
                viewModel.cancelTest()
            }
        }
    }

    if (state.showMeteredWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMeteredWarning() },
            title = { Text(stringResource(R.string.network_quality_metered_title)) },
            text = { Text(stringResource(R.string.network_quality_metered_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmMeteredStart(serviceAvailable) }) {
                    Text(stringResource(R.string.network_quality_metered_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMeteredWarning() }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showConfigURLDialog) {
        ConfigURLDialog(
            currentURL = state.configURL,
            onURLChanged = { viewModel.updateConfigURL(it) },
            onDismiss = { showConfigURLDialog = false },
        )
    }

    if (showMaxRuntimeDialog) {
        MaxRuntimeDialog(
            currentOption = state.maxRuntime,
            onOptionSelected = {
                viewModel.setMaxRuntime(it)
                showMaxRuntimeDialog = false
            },
            onDismiss = { showMaxRuntimeDialog = false },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Back button and Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { navController.navigateUp() },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.previous),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "UNDERCOVER // SPEEDTEST",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "КАЧЕСТВО СЕТИ",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Speed & Assessment Box (в стиле Яндекс Интернетометра)
            val phaseDownload = Libbox.NetworkQualityPhaseDownload.toInt()
            val phaseUpload = Libbox.NetworkQualityPhaseUpload.toInt()
            val done = state.phase == Libbox.NetworkQualityPhaseDone.toInt()

            val downloadSpeedFormatted = if (state.downloadCapacity > 0) {
                Libbox.formatBitrate(state.downloadCapacity)
            } else {
                "—"
            }
            val uploadSpeedFormatted = if (state.uploadCapacity > 0) {
                Libbox.formatBitrate(state.uploadCapacity)
            } else {
                "—"
            }
            val pingFormatted = if (state.idleLatencyMs > 0) {
                "${state.idleLatencyMs} мс"
            } else {
                "—"
            }

            // Tactical Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    .padding(20.dp),
            ) {
                Column {
                    // Header inside card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (state.isRunning) "ИДЁТ ТЕСТИРОВАНИЕ..." else if (done) "ТЕСТ ЗАВЕРШЁН" else "ИНТЕРНЕТОМЕТР",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        if (state.isRunning) {
                            val infiniteTransition = rememberInfiniteTransition()
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speed numbers: 3 columns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "СКАЧИВАНИЕ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = downloadSpeedFormatted,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ОТДАЧА",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uploadSpeedFormatted,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Column(modifier = Modifier.weight(0.7f)) {
                            Text(
                                text = "ПИНГ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pingFormatted,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Human-readable Verdict
                    val (verdictBadge, verdictText) = calculateVerdict(
                        downloadBps = state.downloadCapacity,
                        isRunning = state.isRunning,
                        phase = state.phase,
                        done = done,
                    )

                    Text(
                        text = "ВЕРДИКТ СОЕДИНЕНИЯ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = verdictBadge,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = verdictText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            if (state.isRunning) {
                DossierButton(
                    text = "ОСТАНОВИТЬ ТЕСТ",
                    isOutlined = true,
                    onClick = { viewModel.cancelTest() },
                )
            } else {
                DossierButton(
                    text = if (done) "ПОВТОРИТЬ ТЕСТ" else "ЗАПУСТИТЬ ТЕСТ",
                    isOutlined = false,
                    onClick = { viewModel.requestStartTest(context, serviceAvailable) },
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Accordion for Technical Details (Раскрывается по нажатию)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTechDetails = !showTechDetails }
                    .padding(vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (showTechDetails) "[-] СКРЫТЬ ТЕХНИЧЕСКИЕ ДАННЫЕ" else "[+] ТЕХНИЧЕСКИЕ ПОДРОБНОСТИ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "RPM, задержка под нагрузкой, протокол и точность",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    Text(
                        text = if (showTechDetails) "▲" else "▼",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))

            AnimatedVisibility(visible = showTechDetails) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    TechDetailRow("IDLE LATENCY", if (state.idleLatencyMs > 0) "${state.idleLatencyMs} ms" else "—")
                    TechDetailRow("DOWNLOAD RPM", if (state.downloadRPM > 0) "${state.downloadRPM} RPM" else "—")
                    TechDetailRow("UPLOAD RPM", if (state.uploadRPM > 0) "${state.uploadRPM} RPM" else "—")

                    if (done) {
                        val (accText, _) = accuracyLabel(state.downloadCapacityAccuracy)
                        TechDetailRow("ACCURACY", accText)
                    }

                    TechDetailRow("PROTOCOL", if (state.http3) "HTTP/3 (QUIC)" else "HTTP/2")
                    TechDetailRow("OUTBOUND", state.selectedOutbound.ifEmpty { "Default (in-tunnel)" })

                    // Clickable Settings Rows
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.isRunning) { showMaxRuntimeDialog = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("MAX DURATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("${state.maxRuntime.seconds}s ⚙", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showConfigURLDialog = true }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("TEST ENDPOINT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("Apple CDN ⚙", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun TechDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
    )
}

private fun calculateVerdict(
    downloadBps: Long,
    isRunning: Boolean,
    phase: Int,
    done: Boolean,
): Pair<String, String> {
    if (isRunning) {
        val phaseDownload = Libbox.NetworkQualityPhaseDownload.toInt()
        val phaseUpload = Libbox.NetworkQualityPhaseUpload.toInt()
        val phaseText = when (phase) {
            Libbox.NetworkQualityPhaseIdle.toInt() -> "Шаг 1 из 3: Измерение задержки сети (Ping)..."
            phaseDownload -> "Шаг 2 из 3: Тестирование скорости скачивания..."
            phaseUpload -> "Шаг 3 из 3: Тестирование скорости отдачи..."
            else -> "Подготовка потоков данных..."
        }
        return "ИДЁТ АНАЛИЗ СОЕДИНЕНИЯ" to phaseText
    }

    if (!done && downloadBps == 0L) {
        return "ГОТОВ К ПРОВЕРКЕ" to "Запустите тест, чтобы измерить реальную пропускную способность туннеля и оценить качество соединения."
    }

    val mbps = downloadBps / 1_000_000.0
    return when {
        mbps >= 100.0 -> {
            "🚀 ОТЛИЧНОЕ СОЕДИНЕНИЕ" to "Сверхбыстрый интернет. Мгновенная загрузка файлов, просмотр видео в 4K Ultra HD на нескольких устройствах одновременно и комфортный гейминг."
        }
        mbps >= 50.0 -> {
            "✨ ВЫСОКАЯ СКОРОСТЬ" to "Отличная скорость для любых задач. Можно без задержек смотреть Full HD (1080p) и 4K, проводить стримы и участвовать в видеоконференциях."
        }
        mbps >= 25.0 -> {
            "👍 ХОРОШИЙ ИНТЕРНЕТ" to "Хороший интернет: можно комфортно смотреть видео в Full HD (1080p), слушать музыку без сжатия и работать с облачными сервисами."
        }
        mbps >= 10.0 -> {
            "👌 НОРМАЛЬНАЯ СКОРОСТЬ" to "Нормальная скорость: хватит для онлайн-видео в 720p/1080p, серфинга в сети, соцсетей и звонков в мессенджерах."
        }
        mbps >= 3.0 -> {
            "⚠️ БАЗОВАЯ СКОРОСТЬ" to "Базовый уровень: подходит для мессенджеров, веб-страниц и музыки. Видео высокой четкости может периодически буферизироваться."
        }
        else -> {
            "🐢 МЕДЛЕННЫЙ ИНТЕРНЕТ" to "Низкая скорость соединения: возможны долгие загрузки страниц и задержки при воспроизведении видео. Рекомендуется сменить сервер."
        }
    }
}

@Composable
private fun accuracyLabel(value: Int): Pair<String, Color> = when (value) {
    Libbox.NetworkQualityAccuracyHigh -> "Высокая (High)" to Color.Green
    Libbox.NetworkQualityAccuracyMedium -> "Средняя (Medium)" to Color.Yellow
    else -> "Базовая (Low)" to Color.Red
}

@Composable
private fun ConfigURLDialog(
    currentURL: String,
    onURLChanged: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentURL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_quality_url)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onURLChanged(text)
                onDismiss()
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MaxRuntimeDialog(
    currentOption: MaxRuntimeOption,
    onOptionSelected: (MaxRuntimeOption) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.network_quality_max_runtime)) },
        text = {
            Column {
                MaxRuntimeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(0.dp))
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DossierRadioButton(
                            selected = currentOption == option,
                            onClick = { onOptionSelected(option) },
                        )
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
