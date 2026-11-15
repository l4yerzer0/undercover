package com.undercover.vpn.compose.screen.settings

import android.app.LocaleConfig
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import com.undercover.vpn.shared.ui.components.DossierListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import io.nekohasekai.libbox.Libbox
import com.undercover.vpn.Application
import com.undercover.vpn.BuildConfig
import com.undercover.vpn.R
import com.undercover.vpn.compose.base.UiEvent
import com.undercover.vpn.compose.base.rememberApplyServiceChangeNotifier
import com.undercover.vpn.compose.component.UpdateAvailableDialog
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.constant.Status
import com.undercover.vpn.database.Settings
import com.undercover.vpn.ktx.clipboardText
import com.undercover.vpn.update.UpdateCheckException
import com.undercover.vpn.update.UpdateSource
import com.undercover.vpn.update.UpdateState
import com.undercover.vpn.update.UpdateTrack
import com.undercover.vpn.utils.HookStatusClient
import com.undercover.vpn.vendor.Vendor
import com.undercover.vpn.xposed.XposedActivation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Locale
import android.provider.Settings as AndroidSettings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppSettingsScreen(
    navController: NavController,
    serviceStatus: Status = Status.Stopped,
) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.title_app_settings)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasUpdate by UpdateState.hasUpdate
    val updateInfo by UpdateState.updateInfo
    val isChecking by UpdateState.isChecking
    var showSourceDialog by remember { mutableStateOf(false) }
    var currentSource by remember { mutableStateOf(Settings.updateSource) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var currentTrack by remember { mutableStateOf(Settings.updateTrack) }
    var showGitHubTokenDialog by remember { mutableStateOf(false) }
    var githubToken by remember { mutableStateOf(Settings.githubToken) }
    var checkUpdateEnabled by remember { mutableStateOf(Settings.checkUpdateEnabled) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }

    var silentInstallEnabled by remember { mutableStateOf(Settings.silentInstallEnabled) }
    var silentInstallMethod by remember { mutableStateOf(Settings.silentInstallMethod) }
    val systemHookStatus by HookStatusClient.status.collectAsState()
    val xposedActivated = systemHookStatus?.active == true || XposedActivation.isActivated(context)
    var isMethodAvailable by remember { mutableStateOf(true) }
    var autoUpdateEnabled by remember { mutableStateOf(Settings.autoUpdateEnabled) }
    var showInstallMethodMenu by remember { mutableStateOf(false) }
    var isVerifyingMethod by remember { mutableStateOf(false) }
    var silentInstallError by remember { mutableStateOf<String?>(null) }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var showUpdateAvailableDialog by remember { mutableStateOf(false) }
    var showVersionMenu by remember { mutableStateOf(false) }

    var notificationEnabled by remember { mutableStateOf(true) }
    var dynamicNotification by remember { mutableStateOf(Settings.dynamicNotification) }
    var showDisableNotificationDialog by remember { mutableStateOf(false) }
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var showLanguageDialog by remember { mutableStateOf(false) }
    val availableLocales = remember { getSupportedLocales(context) }
    var currentLocaleTag by remember {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        mutableStateOf(if (appLocales.isEmpty) "" else appLocales.toLanguageTags())
    }

    var cacheSize by remember { mutableStateOf(0L) }
    var cacheSizeText by remember { mutableStateOf("") }

    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(PowerManager::class.java)
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
        )
    }

    val requestBatteryOptimizationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { _ ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(PowerManager::class.java)
                isBatteryOptimizationIgnored =
                    pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }

    fun refreshCacheSize() {
        scope.launch(Dispatchers.IO) {
            val size = calculateDirSize(context.cacheDir)
            withContext(Dispatchers.Main) {
                cacheSize = size
                cacheSizeText = Formatter.formatFileSize(context, size)
            }
        }
    }

    LaunchedEffect(Unit) {
        HookStatusClient.refresh()
        refreshCacheSize()
    }

    // Re-check states when returning from background (e.g., after granting permission)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        HookStatusClient.refresh()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(PowerManager::class.java)
            isBatteryOptimizationIgnored =
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Application.notification.createNotificationChannel(
                NotificationChannel(
                    "service",
                    "Service Notifications",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
            val channel = Application.notification.getNotificationChannel("service")
            notificationEnabled = channel?.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            notificationEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        if (silentInstallEnabled) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    Vendor.verifySilentInstallMethod(silentInstallMethod)
                }
                isMethodAvailable = success
                silentInstallError = if (success) {
                    null
                } else {
                    when (silentInstallMethod) {
                        "PACKAGE_INSTALLER" -> context.getString(R.string.package_installer_not_available)
                        "SHIZUKU" -> context.getString(R.string.shizuku_not_available)
                        else -> context.getString(R.string.silent_install_verify_failed, silentInstallMethod)
                    }
                }
            }
        }
    }

    if (showSourceDialog) {
        UpdateSourceDialog(
            currentSource = currentSource,
            onSourceSelected = { source ->
                currentSource = source
                UpdateState.clear()
                scope.launch(Dispatchers.IO) {
                    Settings.updateSource = source
                }
                showSourceDialog = false
            },
            onDismiss = { showSourceDialog = false },
        )
    }

    if (showTrackDialog) {
        UpdateTrackDialog(
            currentTrack = currentTrack,
            onTrackSelected = { track ->
                currentTrack = track
                UpdateState.clear()
                scope.launch(Dispatchers.IO) {
                    Settings.updateTrack = track
                }
                showTrackDialog = false
            },
            onDismiss = { showTrackDialog = false },
        )
    }

    if (showGitHubTokenDialog) {
        GitHubTokenDialog(
            currentToken = githubToken,
            onSave = { token ->
                githubToken = token
                scope.launch(Dispatchers.IO) {
                    Settings.githubToken = token
                }
                showGitHubTokenDialog = false
            },
            onDismiss = { showGitHubTokenDialog = false },
        )
    }

    showErrorDialog?.let { message ->
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            title = { Text(stringResource(R.string.check_update)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text(stringResource(R.string.ok))
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

    if (showInstallMethodMenu) {
        InstallMethodDialog(
            currentMethod = silentInstallMethod,
            onMethodSelected = { method ->
                showInstallMethodMenu = false
                if (silentInstallMethod == method) return@InstallMethodDialog
                silentInstallMethod = method
                Settings.silentInstallMethod = method
                isVerifyingMethod = true
                scope.launch {
                    val success = withContext(Dispatchers.IO) {
                        Vendor.verifySilentInstallMethod(method)
                    }
                    isVerifyingMethod = false
                    isMethodAvailable = success
                    silentInstallError = if (success) {
                        null
                    } else {
                        when (method) {
                            "PACKAGE_INSTALLER" -> context.getString(R.string.package_installer_not_available)
                            "SHIZUKU" -> context.getString(R.string.shizuku_not_available)
                            else -> context.getString(R.string.silent_install_verify_failed, method)
                        }
                    }
                }
            },
            onDismiss = { showInstallMethodMenu = false },
        )
    }

    if (showDisableNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showDisableNotificationDialog = false },
            title = { Text(stringResource(R.string.enable_notification)) },
            text = {
                Text(
                    stringResource(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            R.string.disable_notification_description
                        } else {
                            R.string.disable_notification_description_legacy
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisableNotificationDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(AndroidSettings.EXTRA_CHANNEL_ID, "service")
                            },
                        )
                    } else {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableNotificationDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
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
                        Log.e("AppSettingsScreen", "Error downloading update", e)
                        downloadError = e.message
                    }
                }
            },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentTag = currentLocaleTag,
            availableLocales = availableLocales,
            onLocaleSelected = { tag ->
                currentLocaleTag = tag
                val localeList = if (tag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        // Info Card
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        ) {
            Column {
                Box {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_version_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            if (hasUpdate) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("New") }
                            }
                        },
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showVersionMenu = true },
                            ),
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        DropdownMenu(
                            expanded = showVersionMenu,
                            onDismissRequest = { showVersionMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.per_app_proxy_action_copy)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    clipboardText = BuildConfig.VERSION_NAME
                                    Toast.makeText(
                                        context,
                                        R.string.copied_to_clipboard,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    showVersionMenu = false
                                },
                            )
                        }
                    }
                }

                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.language),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        val displayName = if (currentLocaleTag.isEmpty()) {
                            stringResource(R.string.system_default)
                        } else {
                            val locale = Locale.forLanguageTag(currentLocaleTag)
                            locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
                        }
                        Text(displayName, style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier =
                    Modifier
                        .clickable { showLanguageDialog = true },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
                
                // App Theme
                var showThemeDialog by remember { mutableStateOf(false) }
                val currentTheme by com.undercover.vpn.database.ThemeState.themeFlow.collectAsState()
                
                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.app_theme),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        val themeName = when (currentTheme) {
                            1 -> stringResource(R.string.app_theme_light)
                            2 -> stringResource(R.string.app_theme_dark)
                            else -> stringResource(R.string.system_default)
                        }
                        Text(themeName, style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable { showThemeDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                
                if (showThemeDialog) {
                    val systemDefaultStr = stringResource(R.string.system_default)
                    val lightStr = stringResource(R.string.app_theme_light)
                    val darkStr = stringResource(R.string.app_theme_dark)
                    AlertDialog(
                        onDismissRequest = { showThemeDialog = false },
                        title = { Text(stringResource(R.string.app_theme)) },
                        text = {
                            Column {
                                listOf(0 to systemDefaultStr, 1 to lightStr, 2 to darkStr).forEach { (value, name) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                com.undercover.vpn.database.ThemeState.themeFlow.value = value
                                                scope.launch(Dispatchers.IO) { Settings.appTheme = value }
                                                showThemeDialog = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = currentTheme == value,
                                            onClick = null
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(name, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showThemeDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }

                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.cache_size),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        if (cacheSizeText.isNotEmpty()) {
                            Text(cacheSizeText, style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier =
                    Modifier
                        .clip(
                            if (cacheSize > 0L) {
                                RoundedCornerShape(0.dp)
                            } else {
                                RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                            },
                        ),
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                if (cacheSize > 0L) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.clear_cache),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        Modifier
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    context.cacheDir?.listFiles()?.forEach { it.deleteRecursively() }
                                    withContext(Dispatchers.Main) {
                                        cacheSize = 0L
                                        cacheSizeText = Formatter.formatFileSize(context, 0L)
                                    }
                                }
                            },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "СИСТЕМА И БАТАРЕЯ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        ) {
            Column {
                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.background_permission),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (isBatteryOptimizationIgnored) {
                                "Оптимизация батареи отключена. Система не будет завершать работу службы в фоне."
                            } else {
                                stringResource(R.string.background_permission_description)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        if (isBatteryOptimizationIgnored) {
                            Text(
                                text = "[ OK ]",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Text(
                                text = "[ ОТКЛЮЧИТЬ ]",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    modifier =
                    Modifier
                        .clickable {
                            if (!isBatteryOptimizationIgnored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent =
                                    Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                requestBatteryOptimizationLauncher.launch(intent)
                            }
                        },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.notification_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        ) {
            Column {
                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.enable_notification),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        com.undercover.vpn.shared.ui.components.DossierSwitch(
                            checked = notificationEnabled,
                            onCheckedChange = null,
                        )
                    },
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .clickable { showDisableNotificationDialog = true },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.dynamic_notification),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        com.undercover.vpn.shared.ui.components.DossierSwitch(
                            checked = dynamicNotification,
                            onCheckedChange = { checked ->
                                dynamicNotification = checked
                                scope.launch(Dispatchers.IO) {
                                    Settings.dynamicNotification = checked
                                    withContext(Dispatchers.Main) {
                                        notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Restart)
                                    }
                                }
                            },
                        )
                    },
                    modifier =
                    Modifier
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.update_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        ) {
            Column {
                val isFDroid = UpdateSource.fromString(currentSource) == UpdateSource.FDROID
                val updateItemCount =
                    run {
                        var count = 0
                        if (Vendor.updateSources.size > 1) {
                            count += 1
                        }
                        if (Vendor.hasCustomUpdate) {
                            count += 1
                        }
                        if (Vendor.hasCustomUpdate && !isFDroid) {
                            count += 1
                        }
                        if (isFDroid) {
                            count += 1
                        }
                        count += 1
                        if (Vendor.hasCustomUpdate) {
                            count += 1
                            if (silentInstallEnabled) {
                                count += 1
                                if (silentInstallMethod == "SHIZUKU" && !isMethodAvailable) {
                                    count += 1
                                }
                                if (silentInstallMethod == "PACKAGE_INSTALLER" && !isMethodAvailable) {
                                    count += 1
                                }
                            }
                        }
                        if (Vendor.hasCustomUpdate) {
                            count += 1
                        }
                        count
                    }

                fun updateItemModifier(): Modifier = Modifier

                if (Vendor.updateSources.size > 1) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.update_source),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            val sourceName = when (UpdateSource.fromString(currentSource)) {
                                UpdateSource.GITHUB -> stringResource(R.string.update_source_github)
                                UpdateSource.FDROID -> stringResource(R.string.update_source_fdroid)
                            }
                            Text(sourceName, style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        updateItemModifier()
                            .clickable { showSourceDialog = true },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }

                if (Vendor.hasCustomUpdate) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.update_track),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            val trackName = if (isFDroid) {
                                stringResource(R.string.update_track_stable)
                            } else {
                                when (UpdateTrack.fromString(currentTrack)) {
                                    UpdateTrack.STABLE -> stringResource(R.string.update_track_stable)
                                    UpdateTrack.BETA -> stringResource(R.string.update_track_beta)
                                }
                            }
                            Text(trackName, style = MaterialTheme.typography.bodyMedium)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        updateItemModifier().let {
                            if (isFDroid) it.alpha(0.38f) else it.clickable { showTrackDialog = true }
                        },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }

                if (Vendor.hasCustomUpdate && !isFDroid) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.github_token),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.github_token_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        updateItemModifier()
                            .clickable { showGitHubTokenDialog = true },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }

                if (isFDroid) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.fdroid_mirror),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            val mirrorUrl = Settings.fdroidMirrorUrl
                            val mirrorName = remember(mirrorUrl) {
                                val iter = Libbox.getFDroidMirrors()
                                var name: String? = null
                                while (iter.hasNext()) {
                                    val m = iter.next()
                                    if (m.url == mirrorUrl) {
                                        name = m.name
                                        break
                                    }
                                }
                                if (name == null) {
                                    val customMirrors = Settings.fdroidCustomMirrors
                                    for (entry in customMirrors) {
                                        val parts = entry.split("|", limit = 2)
                                        if (parts.size == 2 && parts[1] == mirrorUrl) {
                                            name = parts[0]
                                            break
                                        }
                                    }
                                }
                                name ?: mirrorUrl
                            }
                            Text(
                                mirrorName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        updateItemModifier()
                            .clickable { navController.navigate("settings/fdroid_mirror") },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }

                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.check_update_automatic),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Autorenew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        com.undercover.vpn.shared.ui.components.DossierSwitch(
                            checked = checkUpdateEnabled,
                            onCheckedChange = { checked ->
                                checkUpdateEnabled = checked
                                scope.launch(Dispatchers.IO) {
                                    Settings.checkUpdateEnabled = checked
                                }
                            },
                        )
                    },
                    modifier = updateItemModifier(),
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                if (Vendor.hasCustomUpdate) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.silent_install),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                silentInstallError ?: stringResource(R.string.silent_install_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (silentInstallError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            if (isVerifyingMethod) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                com.undercover.vpn.shared.ui.components.DossierSwitch(
                                    checked = silentInstallEnabled,
                                    onCheckedChange = { checked ->
                                        silentInstallEnabled = checked
                                        Settings.silentInstallEnabled = checked
                                        if (checked) {
                                            isVerifyingMethod = true
                                            scope.launch {
                                                val success = withContext(Dispatchers.IO) {
                                                    Vendor.verifySilentInstallMethod(silentInstallMethod)
                                                }
                                                isVerifyingMethod = false
                                                isMethodAvailable = success
                                                silentInstallError = if (success) {
                                                    null
                                                } else {
                                                    when (silentInstallMethod) {
                                                        "PACKAGE_INSTALLER" -> context.getString(R.string.package_installer_not_available)
                                                        "SHIZUKU" -> context.getString(R.string.shizuku_not_available)
                                                        else -> context.getString(R.string.silent_install_verify_failed, silentInstallMethod)
                                                    }
                                                }
                                            }
                                        } else {
                                            silentInstallError = null
                                        }
                                    },
                                )
                            }
                        },
                        modifier = updateItemModifier(),
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )

                    if (silentInstallEnabled) {
                        DossierListItem(
                            headlineContent = {
                                Text(
                                    stringResource(R.string.silent_install_method),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            supportingContent = {
                                Text(
                                    if (xposedActivated) {
                                        stringResource(R.string.install_method_root)
                                    } else {
                                        when (silentInstallMethod) {
                                            "PACKAGE_INSTALLER" -> stringResource(R.string.install_method_package_installer)
                                            "SHIZUKU" -> stringResource(R.string.install_method_shizuku)
                                            "ROOT" -> stringResource(R.string.install_method_root)
                                            else -> silentInstallMethod
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            modifier =
                            updateItemModifier()
                                .let { if (!xposedActivated) it.clickable { showInstallMethodMenu = true } else it },
                            colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                        )

                        if (silentInstallMethod == "SHIZUKU" && !isMethodAvailable) {
                            DossierListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.get_shizuku),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.shizuku_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier =
                                updateItemModifier()
                                    .clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
                                        context.startActivity(intent)
                                    },
                                colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                            )
                        }

                        if (silentInstallMethod == "PACKAGE_INSTALLER" && !isMethodAvailable) {
                            DossierListItem(
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.grant_install_permission),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.grant_install_permission_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier =
                                updateItemModifier()
                                    .clickable {
                                        val intent = Intent(
                                            AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                        context.startActivity(intent)
                                    },
                                colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }

                if (Vendor.hasCustomUpdate) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.auto_update),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.auto_update_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.SystemUpdateAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            com.undercover.vpn.shared.ui.components.DossierSwitch(
                                checked = autoUpdateEnabled,
                                onCheckedChange = { checked ->
                                    autoUpdateEnabled = checked
                                    scope.launch(Dispatchers.IO) {
                                        Settings.autoUpdateEnabled = checked
                                        Vendor.scheduleAutoUpdate()
                                    }
                                },
                            )
                        },
                        modifier = updateItemModifier(),
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Section
        Text(
            text = stringResource(R.string.action),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )

        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors =
            CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
        ) {
            Column {
                DossierListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.check_update),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                    modifier =
                    Modifier
                        .clickable(enabled = !isChecking) {
                            scope.launch {
                                UpdateState.isChecking.value = true
                                withContext(Dispatchers.IO) {
                                    try {
                                        val result = Vendor.checkUpdateAsync()
                                        UpdateState.setUpdate(result)
                                        if (result == null) {
                                            showErrorDialog = context.getString(R.string.no_updates_available)
                                        } else {
                                            showUpdateAvailableDialog = true
                                        }
                                    } catch (_: UpdateCheckException.TrackNotSupported) {
                                        UpdateState.setUpdate(null)
                                        showErrorDialog = context.getString(R.string.update_track_not_supported)
                                    } catch (e: Exception) {
                                        Log.e("AppSettingsScreen", "checkUpdateAsync failed", e)
                                        UpdateState.setUpdate(null)
                                        showErrorDialog = e.message
                                    }
                                }
                                UpdateState.isChecking.value = false
                            }
                        },
                    colors =
                    ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                    ),
                )

                if (hasUpdate && updateInfo != null) {
                    DossierListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.update),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                updateInfo!!.versionName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier =
                        Modifier
                            .clickable {
                                showUpdateAvailableDialog = true
                            },
                        colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateSourceDialog(
    currentSource: String,
    onSourceSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sources = listOf(
        "github" to stringResource(R.string.update_source_github),
        "fdroid" to stringResource(R.string.update_source_fdroid),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        title = {
            Column {
                Text(
                    text = "UNDERCOVER // UPDATE SOURCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_source),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column {
                sources.forEach { (value, label) ->
                    val isSelected = currentSource == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSourceSelected(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isSelected) "[ X ]" else "[   ]",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "[ ${stringResource(android.R.string.cancel).uppercase()} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
    )
}

@Composable
private fun UpdateTrackDialog(
    currentTrack: String,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tracks = listOf(
        "stable" to stringResource(R.string.update_track_stable),
        "beta" to stringResource(R.string.update_track_beta),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        title = {
            Column {
                Text(
                    text = "UNDERCOVER // UPDATE TRACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_track),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column {
                tracks.forEach { (value, label) ->
                    val isSelected = currentTrack == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackSelected(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isSelected) "[ X ]" else "[   ]",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "[ ${stringResource(android.R.string.cancel).uppercase()} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
    )
}

@Composable
private fun GitHubTokenDialog(
    currentToken: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var token by remember(currentToken) { mutableStateOf(currentToken) }
    var tokenVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        title = {
            Column {
                Text(
                    text = "UNDERCOVER // ACCESS TOKEN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.github_token),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(token.trim()) }) {
                Text(
                    text = "[ ${stringResource(R.string.save).uppercase()} ]",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "[ ${stringResource(R.string.cancel).uppercase()} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        },
    )
}

@Composable
private fun LanguageDialog(
    currentTag: String,
    availableLocales: List<Locale>,
    onLocaleSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLocaleSelected("") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentTag.isEmpty(),
                        onClick = { onLocaleSelected("") },
                    )
                    Text(
                        text = stringResource(R.string.system_default),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                availableLocales.forEach { locale ->
                    val tag = locale.toLanguageTag()
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLocaleSelected(tag) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentTag == tag,
                            onClick = { onLocaleSelected(tag) },
                        )
                        Text(
                            text = locale.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) },
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

private fun calculateDirSize(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) calculateDirSize(file) else file.length()
    }
    return size
}

private fun getSupportedLocales(context: Context): List<Locale> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val localeConfig = LocaleConfig(context)
        val localeList = localeConfig.supportedLocales ?: return emptyList()
        return (0 until localeList.size()).map { localeList.get(it) }
    }
    return parseLocalesConfig(context)
}

private fun parseLocalesConfig(context: Context): List<Locale> {
    val locales = mutableListOf<Locale>()
    try {
        val resId = context.resources.getIdentifier(
            "_generated_res_locale_config",
            "xml",
            context.packageName,
        )
        if (resId == 0) return emptyList()
        val parser = context.resources.getXml(resId)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                val name = parser.getAttributeValue(
                    "http://schemas.android.com/apk/res/android",
                    "name",
                )
                if (name != null) {
                    locales.add(Locale.forLanguageTag(name))
                }
            }
        }
    } catch (_: Exception) {
    }
    return locales
}

@Composable
private fun InstallMethodDialog(
    currentMethod: String,
    onMethodSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val methods = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("PACKAGE_INSTALLER" to stringResource(R.string.install_method_package_installer))
        }
        add("SHIZUKU" to stringResource(R.string.install_method_shizuku))
        add("ROOT" to stringResource(R.string.install_method_root))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.silent_install_method)) },
        text = {
            Column {
                methods.forEach { (value, label) ->
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onMethodSelected(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentMethod == value,
                            onClick = { onMethodSelected(value) },
                        )
                        Text(
                            text = label,
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
