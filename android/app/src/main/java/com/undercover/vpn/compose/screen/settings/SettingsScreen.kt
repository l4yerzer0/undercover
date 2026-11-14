package com.undercover.vpn.compose.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.undercover.vpn.BuildConfig
import com.undercover.vpn.R
import com.undercover.vpn.bg.CrashReportManager
import com.undercover.vpn.bg.OOMReportManager
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.shared.ui.theme.DossierCream
import com.undercover.vpn.shared.ui.theme.DossierInk
import com.undercover.vpn.update.UpdateState
import com.undercover.vpn.utils.HookModuleUpdateNotifier
import com.undercover.vpn.utils.HookStatusClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(navController: NavController) {
    OverrideTopBar {
        // Dossier tactical UI replaces standard TopAppBar
    }

    val context = LocalContext.current
    val hasUpdate by UpdateState.hasUpdate
    val hookStatus by HookStatusClient.status.collectAsState()
    val hasPendingPrivilegeDowngrade = HookModuleUpdateNotifier.isDowngrade(hookStatus)
    val hasPendingPrivilegeUpdate = HookModuleUpdateNotifier.isUpgrade(hookStatus)
    val crashUnreadCount by CrashReportManager.unreadCount.collectAsState()
    val oomUnreadCount by OOMReportManager.unreadCount.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { HookStatusClient.refresh() }
            runCatching { CrashReportManager.refresh() }
            runCatching { OOMReportManager.refresh() }
        }
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
            Spacer(modifier = Modifier.height(32.dp))

            // Header Row (Matches Dashboard Dossier aesthetic)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DOSSIER // SETTINGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = "CONFIG No. 0447",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logo and Title Block
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_undercover_logo_light),
                    contentDescription = "Undercover Logo",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(72.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "UNDERCOVER",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Undercover Version", BuildConfig.VERSION_NAME)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "VERSION ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )

                    if (hasUpdate) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[UPDATE AVAILABLE]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Section 1: Configuration
            DossierSectionHeader(title = "CONFIGURATION")

            DossierSettingRow(
                title = stringResource(R.string.title_app_settings),
                subtitle = "Общие параметры и обновления",
                badgeText = if (hasUpdate) "NEW" else null,
                onClick = { navController.navigate("settings/app") },
            )

            DossierSettingRow(
                title = stringResource(R.string.service),
                subtitle = "Режим работы VPN и автозапуск",
                onClick = { navController.navigate("settings/service") },
            )

            DossierSettingRow(
                title = stringResource(R.string.routing),
                subtitle = "Правила маршрутизации и обход сайтов",
                onClick = { navController.navigate("settings/routing") },
            )

            DossierSettingRow(
                title = stringResource(R.string.byedpi),
                subtitle = "Обход замедления и DPI",
                onClick = { navController.navigate("settings/byedpi") },
            )

            // Section 2: For Developers (Collapsible, closed by default)
            var isDeveloperExpanded by remember { mutableStateOf(false) }

            val privilegeBadgeText = when {
                hasPendingPrivilegeDowngrade -> "DOWNGRADE"
                hasPendingPrivilegeUpdate -> "UPDATE"
                else -> null
            }
            val privilegeBadgeColor = if (hasPendingPrivilegeDowngrade) MaterialTheme.colorScheme.error else Color(0xFFFFC107)

            DossierCollapsibleSectionHeader(
                title = "DEVELOPER // ROOT & DIAGNOSTICS",
                isExpanded = isDeveloperExpanded,
                onToggle = { isDeveloperExpanded = !isDeveloperExpanded },
            )

            AnimatedVisibility(
                visible = isDeveloperExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    DossierSettingRow(
                        title = stringResource(R.string.title_log),
                        subtitle = "Системный журнал работы и соединений",
                        onClick = { navController.navigate("settings/log") },
                    )

                    DossierSettingRow(
                        title = stringResource(R.string.profile_override),
                        subtitle = "DNS, MTU и перезапись профиля",
                        onClick = { navController.navigate("settings/profile_override") },
                    )

                    DossierSettingRow(
                        title = stringResource(R.string.privilege_settings),
                        subtitle = "Привилегированное расширение и хуки",
                        badgeText = privilegeBadgeText,
                        badgeColor = privilegeBadgeColor,
                        onClick = { navController.navigate("settings/privilege") },
                    )

                    DossierSettingRow(
                        title = stringResource(R.string.core),
                        subtitle = "Параметры ядра sing-box",
                        onClick = { navController.navigate("settings/core") },
                    )

                    DossierSettingRow(
                        title = stringResource(R.string.crash_report),
                        subtitle = "Журнал неожиданных падений",
                        badgeText = if (crashUnreadCount > 0) "$crashUnreadCount" else null,
                        onClick = { navController.navigate("tools/crash_report") },
                    )

                    DossierSettingRow(
                        title = stringResource(R.string.oom_report),
                        subtitle = "Отчеты о нехватке памяти",
                        badgeText = if (oomUnreadCount > 0) "$oomUnreadCount" else null,
                        onClick = { navController.navigate("tools/oom_report") },
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun DossierCollapsibleSectionHeader(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Spacer(modifier = Modifier.height(36.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            text = if (isExpanded) "[ − СВЕРНУТЬ ]" else "[ + РАЗВЕРНУТЬ ]",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
    )
}

@Composable
private fun DossierSectionHeader(title: String) {
    Spacer(modifier = Modifier.height(36.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)),
    )
}

@Composable
private fun DossierSettingRow(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
    )
}
