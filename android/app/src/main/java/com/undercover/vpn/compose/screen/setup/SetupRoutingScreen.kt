package com.undercover.vpn.compose.screen.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.byedpi.ByeDpiStrategyTester
import com.undercover.vpn.database.Settings
import com.undercover.vpn.database.UserProfileManager
import com.undercover.vpn.routing.RuleSetManager
import com.undercover.vpn.shared.ui.components.DossierButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SetupRoutingScreen(
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rulesUpdated by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(true) }
    var testedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(13) }
    var currentCommand by remember { mutableStateOf("") }
    var bestResult by remember { mutableStateOf<ByeDpiStrategyTester.StrategyResult?>(null) }
    var currentResult by remember { mutableStateOf<ByeDpiStrategyTester.StrategyResult?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { RuleSetManager.updateAll(force = false) }
        }
        rulesUpdated = true

        // Step 2: Test ByeDPI strategies
        val strategies = ByeDpiStrategyTester.loadStrategies(context)
        totalCount = strategies.size

        withContext(Dispatchers.IO) {
            val best = ByeDpiStrategyTester.runProbes(
                strategies = strategies,
                earlyStopOnPerfect = true,
            ) { index, result ->
                testedCount = index + 1
                currentCommand = result.command
                currentResult = result
            }

            bestResult = best
            isTesting = false

            // Save results
            val activeProf = UserProfileManager.getActiveProfile()
            if (best != null && best.score > 0) {
                Settings.byeDpiEnabled = true
                Settings.byeDpiStrategy = best.command
                Settings.routingByeDpiYouTube = best.isYouTubeWorking
                Settings.routingByeDpiDiscord = best.isDiscordWorking

                activeProf.routingByeDpiYouTube = best.isYouTubeWorking
                activeProf.routingByeDpiDiscord = best.isDiscordWorking
            } else {
                // Total fallback to VPN if ISP blocks all ByeDPI strategies
                Settings.byeDpiEnabled = false
                Settings.routingByeDpiYouTube = false
                Settings.routingByeDpiDiscord = false

                activeProf.routingByeDpiYouTube = false
                activeProf.routingByeDpiDiscord = false
            }
            UserProfileManager.update(activeProf)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                DossierButton(
                    text = if (isTesting) "ПРОПУСТИТЬ" else "ПРОДОЛЖИТЬ",
                    isOutlined = isTesting,
                    onClick = onFinish,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "[ АВТОМАТИЧЕСКАЯ НАСТРОЙКА ]",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Оптимизация подключения под провайдера",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                if (isTesting) {
                    TextButton(onClick = onFinish) {
                        Text(
                            text = "Пропустить",
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 1: Rule sets
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (rulesUpdated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(0.dp),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rulesUpdated) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "[ 1/2 ] МАРШРУТИЗАЦИЯ СЕРВИСОВ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (rulesUpdated) {
                                "Списки RU-ресурсов синхронизированы. Российские сервисы, банки и госуслуги работают напрямую."
                            } else {
                                "Синхронизация списков прямого доступа..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: ByeDPI strategies tuning
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        when {
                            isTesting -> MaterialTheme.colorScheme.primary
                            bestResult != null && bestResult!!.score > 0 -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        RoundedCornerShape(0.dp),
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(16.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isTesting) Icons.Default.Tune else if (bestResult != null && bestResult!!.score > 0) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (bestResult == null || bestResult!!.score == 0 && !isTesting) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "[ 2/2 ] ПОДБОР СТРАТЕГИИ ОБХОДА",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isTesting) {
                                    "Тестирование на провайдере ($testedCount из $totalCount)"
                                } else if (bestResult != null && bestResult!!.score > 0) {
                                    "Стратегия подобрана (${bestResult!!.score}%)"
                                } else {
                                    "Включен режим VPN-фоллбека"
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    if (isTesting) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { if (totalCount > 0) testedCount.toFloat() / totalCount else 0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Проверка: ciadpi ${currentCommand.take(50)}...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                        )

                        currentResult?.let { res ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatusTag(label = "YouTube TLS", ok = res.ytClientHello)
                                StatusTag(label = "YouTube CDN", ok = res.ytMediaDownload)
                                StatusTag(label = "Discord", ok = res.discordClientHello)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (bestResult != null && bestResult!!.score > 0) {
                            Text(
                                text = "YouTube и Discord будут работать напрямую через локальный модуль ByeDPI. Видео загружаются на максимальной скорости вашего интернета без нагрузки на VPN-сервер.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    .padding(8.dp),
                            ) {
                                Text(
                                    text = "ciadpi ${bestResult!!.command}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            Text(
                                text = "Ваш провайдер полностью блокирует прямой обход DPI. Трафик YouTube и Discord автоматически перенаправлен через VPN-сервер, чтобы доступ не прерывался.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Explanation note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Параметры маршрутизации и стратегии ByeDPI можно в любой момент изменить или протестировать вручную в разделе «Настройки».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusTag(label: String, ok: Boolean) {
    val color = if (ok) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "$label: ${if (ok) "OK" else "..."}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = color,
        )
    }
}
