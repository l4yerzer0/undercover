package com.undercover.vpn.compose

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.nekohasekai.libbox.Libbox
import com.undercover.vpn.Application
import com.undercover.vpn.BuildConfig
import com.undercover.vpn.R
import com.undercover.vpn.bg.BoxService
import com.undercover.vpn.bg.CrashReportManager
import com.undercover.vpn.bg.OOMReportManager
import com.undercover.vpn.bg.ServiceConnection
import com.undercover.vpn.bg.ServiceNotification
import com.undercover.vpn.compat.WindowSizeClassCompat
import com.undercover.vpn.compat.isWidthAtLeastBreakpointCompat
import com.undercover.vpn.compose.base.GlobalEventBus
import com.undercover.vpn.compose.base.SelectableMessageDialog
import com.undercover.vpn.compose.base.UiEvent
import com.undercover.vpn.compose.component.RemoteStatusBar
import com.undercover.vpn.compose.component.ServiceStatusBar
import com.undercover.vpn.compose.component.UpdateAvailableDialog
import com.undercover.vpn.compose.component.UptimeText
import com.undercover.vpn.compose.model.Connection
import com.undercover.vpn.compose.navigation.NewProfileArgs
import com.undercover.vpn.compose.navigation.ProfileRoutes
import com.undercover.vpn.compose.navigation.SFANavHost
import com.undercover.vpn.compose.navigation.Screen
import com.undercover.vpn.compose.navigation.bottomNavigationScreens
import com.undercover.vpn.compose.screen.configuration.ProfileImportHandler
import com.undercover.vpn.compose.screen.connections.ConnectionDetailsScreen
import com.undercover.vpn.compose.screen.connections.ConnectionsPage
import com.undercover.vpn.compose.screen.connections.ConnectionsViewModel
import com.undercover.vpn.compose.screen.dashboard.DashboardViewModel
import com.undercover.vpn.compose.screen.dashboard.GroupsCard
import com.undercover.vpn.compose.screen.dashboard.groups.GroupsViewModel
import com.undercover.vpn.compose.screen.log.LogViewModel
import com.undercover.vpn.compose.theme.SFATheme
import com.undercover.vpn.compose.topbar.LocalTopBarController
import com.undercover.vpn.compose.topbar.TopBarController
import com.undercover.vpn.compose.topbar.TopBarEntry
import com.undercover.vpn.constant.Action
import com.undercover.vpn.constant.Alert
import com.undercover.vpn.constant.ServiceMode
import com.undercover.vpn.constant.Status
import com.undercover.vpn.database.Settings
import com.undercover.vpn.ktx.hasPermission
import com.undercover.vpn.ktx.launchCustomTab
import com.undercover.vpn.update.UpdateState
import com.undercover.vpn.utils.RemoteControlManager
import com.undercover.vpn.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity :
    AppCompatActivity(),
    ServiceConnection.Callback {
    private val connection = ServiceConnection(this, this)
    private lateinit var dashboardViewModel: DashboardViewModel
    private var currentServiceStatus by mutableStateOf(Status.Stopped)
    private var currentAlert by mutableStateOf<Pair<Alert, String?>?>(null)
    private var showLocationPermissionDialog by mutableStateOf(false)
    private var showBackgroundLocationDialog by mutableStateOf(false)
    private var showLocalNetworkPermissionDialog by mutableStateOf(false)
    private var notificationPermissionRequested = false
    private var localNetworkPermissionRequested = false
    private var showImportProfileDialog by mutableStateOf(false)
    private var pendingImportProfile by mutableStateOf<Triple<String, String, String>?>(null)
    private var showImportLocalProfileDialog by mutableStateOf(false)
    private var pendingImportLocalProfileName by mutableStateOf<String?>(null)
    private var pendingImportLocalProfileUri by mutableStateOf<Uri?>(null)
    private var showImportLinkDialog by mutableStateOf(false)
    private var pendingImportLink by mutableStateOf<Pair<String, String>?>(null)
    private var newProfileArgs by mutableStateOf(NewProfileArgs())
    private var parseImportLocalProfileJob: Job? = null
    private var pendingIntentErrorMessage by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            startService()
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    requestBackgroundLocationPermission()
                } else {
                    startService()
                }
            }
        }

    private val backgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                startService()
            }
        }

    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startService()
        }

    private val prepareLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startService0()
            } else {
                onServiceAlert(Alert.RequestVPNPermission, null)
            }
        }
    private val pendingNavigationRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigurationCompat.getLocales(resources.configuration)[0]?.let { locale ->
            runCatching {
                Libbox.setLocale(locale.toLanguageTag())
            }.onFailure {
                Log.d("MainActivity", "set locale: ${it.message}")
            }
        }
        enableEdgeToEdge()

        connection.reconnect()
        RemoteControlManager.restore()

        UpdateState.loadFromCache()
        if (Settings.checkUpdateEnabled) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val updateInfo = Vendor.checkUpdateAsync()
                    UpdateState.setUpdate(updateInfo)
                } catch (_: Exception) {
                    UpdateState.setUpdate(null)
                }
            }
        }

        handleIntent(intent)

        setContent {
            val themeValue by com.undercover.vpn.database.ThemeState.themeFlow.collectAsState()
            val darkTheme = when (themeValue) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            com.undercover.vpn.shared.ui.theme.DossierTheme(darkTheme = darkTheme) {
                SFAApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        if (intent.categories?.contains("de.robv.android.xposed.category.MODULE_SETTINGS") == true) {
            pendingNavigationRoute.value = "settings/privilege"
        }
        val uri = intent.data ?: return
        val uriString = intent.dataString ?: uri.toString()
        if (intent.action == Action.OPEN_URL) {
            launchCustomTab(uriString)
            return
        }
        if ((uri.scheme == "sing-box" || uri.scheme == "undercover") && (uri.host == "import-remote-profile" || uri.host == "import")) {
            try {
                val profile = Libbox.parseRemoteProfileImportLink(uriString)
                pendingImportProfile = Triple(profile.name, profile.host, profile.url)
                showImportProfileDialog = true
            } catch (e: Exception) {
                pendingIntentErrorMessage = e.message ?: "Failed to parse profile link"
            }
            return
        }

        if (intent.action == Intent.ACTION_VIEW) {
            var rawConfigString = uriString
            if (uri.scheme == "undercover") {
                val stripped = uriString.removePrefix("undercover://").removePrefix("undercover:")
                if (com.undercover.vpn.shared.parser.ConfigParser.isSupportedUri(stripped)) {
                    rawConfigString = stripped
                } else if (uri.getQueryParameter("url") != null) {
                    rawConfigString = uri.getQueryParameter("url")!!
                }
            }

            if (com.undercover.vpn.shared.parser.ConfigParser.isSupportedUri(rawConfigString)) {
                val name = com.undercover.vpn.shared.parser.ConfigParser.parseUriToName(rawConfigString) ?: "Imported Proxy"
                pendingImportLink = Pair(name, rawConfigString)
                showImportLinkDialog = true
                return
            }

            if (uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == ContentResolver.SCHEME_FILE) {
                parseImportLocalProfileJob?.cancel()
                parseImportLocalProfileJob =
                    lifecycleScope.launch(Dispatchers.IO) {
                        val importHandler = ProfileImportHandler(this@MainActivity)
                        when (val result = importHandler.parseUri(uri)) {
                            is ProfileImportHandler.UriParseResult.Success -> {
                                withContext(Dispatchers.Main) {
                                    pendingImportLocalProfileName = result.name
                                    pendingImportLocalProfileUri = uri
                                    showImportLocalProfileDialog = true
                                }
                            }

                            is ProfileImportHandler.UriParseResult.Error -> {
                                withContext(Dispatchers.Main) {
                                    pendingIntentErrorMessage = result.message
                                }
                            }
                        }
                    }
                return
            }
        }
    }

    @SuppressLint("NewApi")
    fun startService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ServiceNotification.checkPermission()) {
            if (!notificationPermissionRequested) {
                notificationPermissionRequested = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
            if (Settings.dynamicNotification) {
                onServiceAlert(Alert.RequestNotificationPermission, null)
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            !hasPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) &&
            !localNetworkPermissionRequested
        ) {
            localNetworkPermissionRequested = true
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_LOCAL_NETWORK)) {
                showLocalNetworkPermissionDialog = true
            } else {
                localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
            return
        }
        startService0()
    }

    private fun startService0() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (Settings.rebuildServiceMode()) {
                connection.reconnect()
            }
            if (Settings.serviceMode == ServiceMode.VPN) {
                if (prepare()) {
                    return@launch
                }
            }
            val intent = Intent(Application.application, Settings.serviceClass())
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
            Settings.startedByUser = true
        }
    }

    private suspend fun prepare() = withContext(Dispatchers.Main) {
        try {
            val intent = VpnService.prepare(this@MainActivity)
            if (intent != null) {
                prepareLauncher.launch(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            onServiceAlert(Alert.RequestVPNPermission, e.message)
            true
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SFAApp() {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRoute = currentDestination?.route
        val scope = rememberCoroutineScope()
        val importHandler = remember { ProfileImportHandler(this@MainActivity) }

        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val useNavigationRail =
            windowSizeClass.isWidthAtLeastBreakpointCompat(WindowSizeClassCompat.WIDTH_DP_MEDIUM_LOWER_BOUND)

        // Snackbar state
        val snackbarHostState = remember { SnackbarHostState() }
        // Error dialog state for UiEvent.ShowError
        var showErrorDialog by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf("") }
        var pendingApplyServiceChangeMode by remember { mutableStateOf<UiEvent.ApplyServiceChange.Mode?>(null) }
        var activeApplyServiceChangeMode by remember { mutableStateOf<UiEvent.ApplyServiceChange.Mode?>(null) }
        var applyServiceChangeJob by remember { mutableStateOf<Job?>(null) }

        fun mergeApplyServiceChangeMode(
            current: UiEvent.ApplyServiceChange.Mode?,
            incoming: UiEvent.ApplyServiceChange.Mode,
        ): UiEvent.ApplyServiceChange.Mode = when {
            current == UiEvent.ApplyServiceChange.Mode.Restart ||
                incoming == UiEvent.ApplyServiceChange.Mode.Restart -> {
                UiEvent.ApplyServiceChange.Mode.Restart
            }

            else -> incoming
        }

        fun enqueueApplyServiceChange(mode: UiEvent.ApplyServiceChange.Mode) {
            if (currentServiceStatus != Status.Started) {
                return
            }

            pendingApplyServiceChangeMode = mergeApplyServiceChangeMode(pendingApplyServiceChangeMode, mode)

            val activeMode = activeApplyServiceChangeMode
            if (activeMode != null &&
                mergeApplyServiceChangeMode(activeMode, mode) != activeMode
            ) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }

            if (applyServiceChangeJob?.isActive == true) {
                return
            }

            applyServiceChangeJob =
                scope.launch {
                    while (true) {
                        val modeToShow = pendingApplyServiceChangeMode ?: break
                        pendingApplyServiceChangeMode = null
                        activeApplyServiceChangeMode = modeToShow
                        val (message, actionLabel) =
                            when (modeToShow) {
                                UiEvent.ApplyServiceChange.Mode.Reload -> {
                                    getString(R.string.service_reload_required) to
                                        getString(R.string.action_reload)
                                }

                                UiEvent.ApplyServiceChange.Mode.Restart -> {
                                    getString(R.string.service_restart_required) to
                                        getString(R.string.action_restart)
                                }
                            }
                        val result =
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = actionLabel,
                                duration = androidx.compose.material3.SnackbarDuration.Short,
                            )
                        activeApplyServiceChangeMode = null
                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                            try {
                                when (modeToShow) {
                                    UiEvent.ApplyServiceChange.Mode.Reload -> {
                                        withContext(Dispatchers.IO) {
                                            Libbox.newStandaloneCommandClient().serviceReload()
                                        }
                                    }

                                    UiEvent.ApplyServiceChange.Mode.Restart -> {
                                        restartServiceForApplyChange()
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = e.message ?: e.toString()
                                showErrorDialog = true
                            }
                        }
                    }
                }
        }

        // Groups Sheet state
        var showGroupsSheet by remember { mutableStateOf(false) }

        // Connections Sheet state
        var showConnectionsSheet by remember { mutableStateOf(false) }

        // Error dialog state for UiEvent.ShowError
        val pendingIntentError = pendingIntentErrorMessage
        LaunchedEffect(pendingIntentError) {
            if (pendingIntentError != null) {
                errorMessage = pendingIntentError
                showErrorDialog = true
                pendingIntentErrorMessage = null
            }
        }
        val topBarState = remember { mutableStateOf(emptyList<TopBarEntry>()) }
        val topBarController = remember { TopBarController(topBarState) }
        val topBarOverride = topBarState.value.lastOrNull()?.content
        val openNewProfile: (NewProfileArgs) -> Unit = { args ->
            newProfileArgs = args
            navController.navigate(ProfileRoutes.NewProfile) {
                launchSingleTop = true
            }
        }

        // Handle service alerts
        currentAlert?.let { (alertType, message) ->
            ServiceAlertDialog(
                alertType = alertType,
                message = message,
                onDismiss = { currentAlert = null },
            )
        }

        // Handle UiEvent.ShowError dialog
        if (showErrorDialog) {
            SelectableMessageDialog(
                title = stringResource(R.string.error_title),
                message = errorMessage,
                onDismiss = { showErrorDialog = false },
            )
        }

        // Handle location permission dialogs
        if (showLocationPermissionDialog) {
            LocationPermissionDialog(onConfirm = {
                showLocationPermissionDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }, onDismiss = { showLocationPermissionDialog = false })
        }

        if (showBackgroundLocationDialog) {
            BackgroundLocationPermissionDialog(onConfirm = {
                showBackgroundLocationDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }, onDismiss = { showBackgroundLocationDialog = false })
        }

        if (showLocalNetworkPermissionDialog) {
            LocalNetworkPermissionDialog(onConfirm = {
                showLocalNetworkPermissionDialog = false
                localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }, onDismiss = {
                showLocalNetworkPermissionDialog = false
                startService()
            })
        }

        // Handle import remote profile dialog
        if (showImportProfileDialog && pendingImportProfile != null) {
            val (name, host, url) = pendingImportProfile!!
            AlertDialog(
                onDismissRequest = {
                    showImportProfileDialog = false
                    pendingImportProfile = null
                },
                title = { Text(stringResource(R.string.import_remote_profile)) },
                text = { Text(stringResource(R.string.import_remote_profile_message, name, host)) },
                confirmButton = {
                    TextButton(onClick = {
                        openNewProfile(
                            NewProfileArgs(
                                importName = name,
                                importUrl = url,
                            ),
                        )
                        showImportProfileDialog = false
                        pendingImportProfile = null
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImportProfileDialog = false
                        pendingImportProfile = null
                    }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }

        if (showImportLocalProfileDialog && pendingImportLocalProfileUri != null && pendingImportLocalProfileName != null) {
            val importName = pendingImportLocalProfileName!!
            val importUri = pendingImportLocalProfileUri!!
            AlertDialog(
                onDismissRequest = {
                    showImportLocalProfileDialog = false
                    pendingImportLocalProfileName = null
                    pendingImportLocalProfileUri = null
                },
                title = { Text(stringResource(R.string.import_profile_confirm_title)) },
                text = { Text(stringResource(R.string.import_profile_confirm_message, importName)) },
                confirmButton = {
                    TextButton(onClick = {
                        showImportLocalProfileDialog = false
                        pendingImportLocalProfileName = null
                        pendingImportLocalProfileUri = null
                        scope.launch {
                            when (val result = importHandler.importFromUri(importUri)) {
                                is ProfileImportHandler.ImportResult.Success -> {
                                    navController.navigate(ProfileRoutes.editProfile(result.profile.id)) {
                                        launchSingleTop = true
                                    }
                                }
                                is ProfileImportHandler.ImportResult.Error -> {
                                    errorMessage = result.message
                                    showErrorDialog = true
                                }
                            }
                        }
                    }) {
                        Text(stringResource(R.string.import_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImportLocalProfileDialog = false
                        pendingImportLocalProfileName = null
                        pendingImportLocalProfileUri = null
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (showImportLinkDialog && pendingImportLink != null) {
            val (importName, configString) = pendingImportLink!!
            AlertDialog(
                onDismissRequest = {
                    showImportLinkDialog = false
                    pendingImportLink = null
                },
                title = { Text(stringResource(R.string.import_profile_confirm_title)) },
                text = { Text(stringResource(R.string.import_profile_confirm_message, importName)) },
                confirmButton = {
                    TextButton(onClick = {
                        showImportLinkDialog = false
                        pendingImportLink = null
                        scope.launch {
                            when (val result = importHandler.importFromQRCode(configString)) {
                                is ProfileImportHandler.ImportResult.Success -> {
                                    Settings.hasSeenOnboarding = true
                                    Toast.makeText(this@MainActivity, R.string.onboarding_profile_imported, Toast.LENGTH_SHORT).show()
                                    navController.navigate(ProfileRoutes.editProfile(result.profile.id)) {
                                        launchSingleTop = true
                                    }
                                }
                                is ProfileImportHandler.ImportResult.Error -> {
                                    errorMessage = result.message
                                    showErrorDialog = true
                                }
                            }
                        }
                    }) {
                        Text(stringResource(R.string.import_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showImportLinkDialog = false
                        pendingImportLink = null
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Handle update check prompt dialog (shown only once on first launch)
        var showUpdateCheckPrompt by remember { mutableStateOf(!Settings.updateCheckPrompted) }
        if (showUpdateCheckPrompt) {
            AlertDialog(
                onDismissRequest = {
                    Settings.updateCheckPrompted = true
                    showUpdateCheckPrompt = false
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                title = {
                    Column {
                        Text(
                            text = "DOSSIER // UPDATE CHECK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            letterSpacing = 2.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.check_update),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                text = {
                    MarkdownText(
                        markdown = stringResource(
                            if (BuildConfig.FLAVOR == "play") {
                                R.string.check_update_prompt_play
                            } else {
                                R.string.check_update_prompt_github
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        Settings.updateCheckPrompted = true
                        Settings.checkUpdateEnabled = true
                        showUpdateCheckPrompt = false
                        scope.launch(Dispatchers.IO) {
                            try {
                                val result = Vendor.checkUpdateAsync()
                                UpdateState.setUpdate(result)
                            } catch (_: Exception) {
                                UpdateState.setUpdate(null)
                            }
                        }
                    }) {
                        Text(
                            text = "[ ${stringResource(R.string.ok).uppercase()} ]",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        Settings.updateCheckPrompted = true
                        showUpdateCheckPrompt = false
                    }) {
                        Text(
                            text = "[ ${stringResource(R.string.no_thanks).uppercase()} ]",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                },
            )
        }

        // Handle update available dialog
        val updateInfo by UpdateState.updateInfo
        val shouldShowUpdateDialog = updateInfo != null &&
            updateInfo!!.versionCode > Settings.lastShownUpdateVersion
        var showUpdateDialog by remember { mutableStateOf(true) }

        // Download dialog state
        var showDownloadDialog by remember { mutableStateOf(false) }
        var downloadJob by remember { mutableStateOf<Job?>(null) }
        var downloadError by remember { mutableStateOf<String?>(null) }

        if (showUpdateDialog && shouldShowUpdateDialog) {
            UpdateAvailableDialog(
                updateInfo = updateInfo!!,
                onDismiss = {
                    Settings.lastShownUpdateVersion = updateInfo!!.versionCode
                    showUpdateDialog = false
                },
                onUpdate = {
                    showDownloadDialog = true
                    downloadError = null
                    downloadJob = scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                Vendor.downloadAndInstall(
                                    this@MainActivity,
                                    updateInfo!!.downloadUrl,
                                )
                            }
                            showDownloadDialog = false
                        } catch (e: Exception) {
                            downloadError = e.message
                        }
                    }
                },
            )
        }

        // Download progress dialog
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

        val remoteServer by RemoteControlManager.remoteServer.collectAsState()
        val remoteConnected by RemoteControlManager.isConnected.collectAsState()
        val remoteStartedAt by RemoteControlManager.startedAt.collectAsState()
        val isRemote = remoteServer != null

        // Initialize the dashboard view model and store reference
        val dashboardViewModel: DashboardViewModel = viewModel()
        if (!::dashboardViewModel.isInitialized) {
            this.dashboardViewModel = dashboardViewModel
        }
        val dashboardUiState by dashboardViewModel.uiState.collectAsState()

        LaunchedEffect(currentServiceStatus) {
            dashboardViewModel.updateServiceStatus(currentServiceStatus)
        }

        if (dashboardUiState.showDeprecatedDialog && dashboardUiState.deprecatedNotes.isNotEmpty()) {
            val note = dashboardUiState.deprecatedNotes.first()
            AlertDialog(
                onDismissRequest = { },
                title = { Text(stringResource(R.string.error_deprecated_warning)) },
                text = { Text(note.message) },
                confirmButton = {
                    TextButton(onClick = { dashboardViewModel.dismissDeprecatedNote() }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton =
                if (!note.migrationLink.isNullOrBlank()) {
                    {
                        TextButton(onClick = {
                            dashboardViewModel.sendGlobalEvent(UiEvent.OpenUrl(note.migrationLink))
                            dashboardViewModel.dismissDeprecatedNote()
                        }) {
                            Text(stringResource(R.string.error_deprecated_documentation))
                        }
                    }
                } else {
                    null
                },
            )
        }

        val isSettingsSubScreen = currentRoute?.startsWith("settings/") == true
        val isToolsSubScreen = currentRoute?.startsWith("tools/") == true
        val isConnectionsDetail = currentRoute?.startsWith("connections/detail") == true
        val isProfileRoute = currentRoute?.startsWith("profile/") == true
        val isOnboardingRoute = currentRoute == "onboarding"
        val currentRootRoute =
            when {
                isSettingsSubScreen -> Screen.Settings.route
                isToolsSubScreen -> Screen.Tools.route
                currentRoute?.startsWith(Screen.Connections.route) == true -> Screen.Connections.route
                currentRoute?.startsWith(Screen.Log.route) == true -> Screen.Log.route
                isProfileRoute -> Screen.Dashboard.route
                else -> currentRoute
            }
        val isConnectionsRoute = currentRootRoute == Screen.Connections.route
        val isGroupsRoute = currentRootRoute == Screen.Groups.route
        val isLogRoute = currentRoute?.startsWith("settings/log") == true || currentRoute?.startsWith(Screen.Log.route) == true

        val isSubScreen = isSettingsSubScreen || isToolsSubScreen || isConnectionsDetail || isProfileRoute || isOnboardingRoute || currentRoute?.startsWith(Screen.Log.route) == true
        // Get LogViewModel instance if we're on the Log screen
        val logViewModel: LogViewModel? =
            if (isLogRoute) {
                viewModel()
            } else {
                null
            }

        val groupsViewModel: GroupsViewModel? =
            if (isGroupsRoute) {
                viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return GroupsViewModel(dashboardViewModel.commandClient) as T
                        }
                    },
                )
            } else {
                null
            }

        val connectionsViewModel: ConnectionsViewModel? =
            if (isConnectionsRoute) {
                viewModel()
            } else {
                null
            }


        val showGroupsInNav = dashboardUiState.hasGroups
        val showConnectionsInNav =
            if (isRemote) {
                remoteConnected
            } else {
                currentServiceStatus == Status.Started || currentServiceStatus == Status.Starting
            }

        val railScreens =
            buildList {
                add(Screen.Dashboard)
                add(Screen.Connections)
                if (showGroupsInNav) {
                    add(Screen.Groups)
                }
                add(Screen.Tools)
                add(Screen.Settings)
            }

        val allowedRoutes =
            buildList {
                add(Screen.Dashboard.route)
                add(Screen.Connections.route)
                add(Screen.Settings.route)
                add(Screen.Tools.route)
                add("onboarding")
                if (showGroupsInNav) {
                    add(Screen.Groups.route)
                }
                add(Screen.Log.route)
                add("settings/log")
            }

        val pendingRoute = pendingNavigationRoute.value
        LaunchedEffect(pendingRoute) {
            if (pendingRoute != null) {
                navController.navigate(pendingRoute) {
                    launchSingleTop = true
                }
                pendingNavigationRoute.value = null
            }
        }

        LaunchedEffect(allowedRoutes, currentRootRoute, useNavigationRail) {
            if (currentRootRoute != null && !allowedRoutes.contains(currentRootRoute)) {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }

        // Collect all UI events from GlobalEventBus
        LaunchedEffect(Unit) {
            GlobalEventBus.events.collect { event ->
                when (event) {
                    is UiEvent.ErrorMessage -> {
                        errorMessage = event.message
                        showErrorDialog = true
                    }

                    is UiEvent.OpenUrl -> {
                        this@MainActivity.launchCustomTab(event.url)
                    }

                    is UiEvent.RequestStartService -> {
                        startService()
                    }

                    is UiEvent.RequestReconnectService -> {
                        connection.reconnect()
                    }

                    is UiEvent.EditProfile -> {
                        navController.navigate(ProfileRoutes.editProfile(event.profileId)) {
                            launchSingleTop = true
                        }
                    }

                    is UiEvent.Navigate -> {
                        navController.navigate(event.route) {
                            launchSingleTop = true
                        }
                    }

                    is UiEvent.ApplyServiceChange -> enqueueApplyServiceChange(event.mode)
                }
            }
        }

        val topBarContent: @Composable () -> Unit = {
            topBarOverride?.invoke()
        }

        val scaffoldContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Service Status Bar (shown when service is running or stopping);
                // remote control replaces it with the remote session bar.
                val serviceRunning =
                    currentServiceStatus == Status.Started || currentServiceStatus == Status.Starting
                val showStatusBar = isRemote || serviceRunning || currentServiceStatus == Status.Stopping
                val showStartFab = !isRemote && !serviceRunning && dashboardUiState.selectedProfileId != -1L

                SFANavHost(
                    navController = navController,
                    serviceStatus = currentServiceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    newProfileArgs = newProfileArgs,
                    onClearNewProfileArgs = { newProfileArgs = NewProfileArgs() },
                    onOpenNewProfile = openNewProfile,
                    dashboardViewModel = dashboardViewModel,
                    logViewModel = logViewModel,
                    groupsViewModel = groupsViewModel,
                    connectionsViewModel = connectionsViewModel,
                    onToggleConnection = {
                        if (currentServiceStatus == Status.Started || currentServiceStatus == Status.Starting) {
                            dashboardViewModel.toggleService()
                        } else {
                            startService()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!useNavigationRail) {
                    // Removed ServiceStatusBar and RemoteStatusBar since we will use Dossier UI
                }

                val showPadFab = useNavigationRail && !isSubScreen && (showStartFab || showStatusBar)
                if (useNavigationRail) {
                    // Removed Pad FAB
                } else {
                    // Start FAB (shown when service is stopped and a profile is selected)
                    // Removed Start FAB
                }
            }
        }

        val crashReportUnreadCount by CrashReportManager.unreadCount.collectAsState()
        val oomReportUnreadCount by OOMReportManager.unreadCount.collectAsState()
        // The crash/OOM report entries are now in Settings under "For Developers"
        val settingsDebugUnreadCount = if (isRemote) 0 else crashReportUnreadCount + oomReportUnreadCount

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                CrashReportManager.refresh()
                OOMReportManager.refresh()
            }
        }

        CompositionLocalProvider(LocalTopBarController provides topBarController) {
            if (useNavigationRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Surface(tonalElevation = 1.dp) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            val hasUpdate by UpdateState.hasUpdate
                            railScreens.forEach { screen ->
                                val selected = currentRootRoute == screen.route

                                NavigationRailItem(
                                    icon = {
                                        if (screen == Screen.Settings && (hasUpdate || settingsDebugUnreadCount > 0)) {
                                            BadgedBox(
                                                badge = {
                                                    if (settingsDebugUnreadCount > 0) {
                                                        Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$settingsDebugUnreadCount") }
                                                    } else {
                                                        Badge(containerColor = MaterialTheme.colorScheme.primary)
                                                    }
                                                },
                                            ) {
                                                Icon(screen.icon, contentDescription = null)
                                            }
                                        } else {
                                            Icon(screen.icon, contentDescription = null)
                                        }
                                    },
                                    label = { Text(stringResource(screen.titleRes)) },
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        topBar = topBarContent,
                    ) { paddingValues ->
                        scaffoldContent(paddingValues)
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = topBarContent,
                    bottomBar = {
                        if (!isSubScreen) {
                            val hasUpdate by UpdateState.hasUpdate
                            val currentRoute = currentDestination?.route
                            val selectedIndex = bottomNavigationScreens.indexOfFirst { it.route == currentRoute }.takeIf { it >= 0 } ?: 0
                            
                            com.undercover.vpn.shared.ui.components.DossierBottomNav(
                                items = bottomNavigationScreens.map { stringResource(it.titleRes).uppercase() },
                                selectedIndex = selectedIndex,
                                onItemSelected = { index ->
                                    val screen = bottomNavigationScreens[index]
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    },
                ) { paddingValues ->
                    scaffoldContent(paddingValues)
                }
            }
        }

        LaunchedEffect(dashboardUiState.hasGroups) {
            if (!dashboardUiState.hasGroups) {
                showGroupsSheet = false
            }
        }
        val connectionsAvailable = if (isRemote) remoteConnected else currentServiceStatus == Status.Started
        LaunchedEffect(connectionsAvailable) {
            if (!connectionsAvailable) {
                showConnectionsSheet = false
            }
        }

        // Groups ModalBottomSheet
        if (showGroupsSheet && !useNavigationRail) {
            val groupsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val groupsViewModel: GroupsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GroupsViewModel(dashboardViewModel.commandClient) as T
                    }
                },
            )
            val groupsUiState by groupsViewModel.uiState.collectAsState()
            val allCollapsed = groupsUiState.expandedGroups.isEmpty()

            ModalBottomSheet(
                onDismissRequest = { showGroupsSheet = false },
                sheetState = groupsSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) {
                    // Groups content
                    GroupsCard(
                        serviceStatus = currentServiceStatus,
                        commandClient = dashboardViewModel.commandClient,
                        viewModel = groupsViewModel,
                        listHeaderContent = {
                            Row(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.title_groups),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                if (groupsUiState.groups.isNotEmpty()) {
                                    IconButton(onClick = { groupsViewModel.toggleAllGroups() }) {
                                        Icon(
                                            imageVector = if (allCollapsed) {
                                                Icons.Default.UnfoldMore
                                            } else {
                                                Icons.Default.UnfoldLess
                                            },
                                            contentDescription = if (allCollapsed) {
                                                stringResource(R.string.expand_all)
                                            } else {
                                                stringResource(R.string.collapse_all)
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        asSheet = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Connections ModalBottomSheet
        if (showConnectionsSheet && !useNavigationRail) {
            val connectionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val connectionsViewModel: ConnectionsViewModel = viewModel()
            val connectionsUiState by connectionsViewModel.uiState.collectAsState()
            var selectedConnectionId by remember { mutableStateOf<String?>(null) }
            val selectedConnection = connectionsUiState.allConnections.find { it.id == selectedConnectionId }
            var cachedConnection by remember { mutableStateOf<Connection?>(null) }
            if (selectedConnection != null) {
                cachedConnection = selectedConnection
            } else if (selectedConnectionId != null && cachedConnection?.isActive == true) {
                cachedConnection = cachedConnection?.copy(closedAt = System.currentTimeMillis())
            }
            val displayConnection = if (selectedConnectionId != null) cachedConnection else null

            LaunchedEffect(Unit) {
                connectionsViewModel.setVisible(true)
            }

            DisposableEffect(Unit) {
                onDispose {
                    connectionsViewModel.setVisible(false)
                }
            }

            BackHandler(enabled = selectedConnectionId != null) {
                selectedConnectionId = null
            }

            ModalBottomSheet(
                onDismissRequest = {
                    showConnectionsSheet = false
                    selectedConnectionId = null
                },
                sheetState = connectionsSheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) {
                    if (displayConnection != null) {
                        ConnectionDetailsScreen(
                            connection = displayConnection,
                            onBack = { selectedConnectionId = null },
                            onClose = {
                                selectedConnectionId?.let { connectionsViewModel.closeConnection(it) }
                            },
                            asSheet = true,
                        )
                    } else {
                        ConnectionsPage(
                            serviceStatus = currentServiceStatus,
                            viewModel = connectionsViewModel,
                            asSheet = true,
                            showTitle = true,
                            onConnectionClick = { selectedConnectionId = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onServiceStatusChanged(status: Status) {
        currentServiceStatus = status
    }

    fun reconnect() {
        connection.reconnect()
    }

    override fun onServiceAlert(type: Alert, message: String?) {
        when (type) {
            Alert.RequestLocationPermission -> {
                return requestLocationPermission()
            }

            else -> {
                currentAlert = Pair(type, message)
            }
        }
    }

    private fun requestLocationPermission() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestFineLocationPermission()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestFineLocationPermission() {
        // Show location permission dialog in Compose UI
        showLocationPermissionDialog = true
    }

    private fun requestBackgroundLocationPermission() {
        // Show background location permission dialog in Compose UI
        showBackgroundLocationDialog = true
    }

    private suspend fun restartServiceForApplyChange() {
        if (currentServiceStatus != Status.Started) {
            return
        }

        BoxService.stop()
        while (true) {
            when (currentServiceStatus) {
                Status.Stopped -> {
                    startService()
                    return
                }

                Status.Starting -> {
                    return
                }

                Status.Started, Status.Stopping -> {
                    delay(100L)
                }
            }
        }
    }

    override fun onDestroy() {
        connection.disconnect()
        super.onDestroy()
    }

    @Composable
    private fun ServiceAlertDialog(alertType: Alert, message: String?, onDismiss: () -> Unit) {
        val title =
            when (alertType) {
                Alert.RequestNotificationPermission -> stringResource(R.string.notification_permission_title)
                Alert.StartCommandServer -> stringResource(R.string.error_start_command_server)
                Alert.CreateService -> stringResource(R.string.error_create_service)
                Alert.StartService -> stringResource(R.string.error_start_service)
                else -> null
            }

        val dialogMessage =
            when (alertType) {
                Alert.RequestVPNPermission -> stringResource(R.string.error_missing_vpn_permission)
                Alert.RequestNotificationPermission -> stringResource(R.string.notification_permission_required_description)
                Alert.EmptyConfiguration -> stringResource(R.string.error_empty_configuration)
                else -> message
            }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = title?.let { { Text(text = it) } },
            text = dialogMessage?.let { { Text(text = it) } },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    @Composable
    private fun LocationPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_permission_description)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.no_thanks))
                }
            },
        )
    }

    @Composable
    private fun BackgroundLocationPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_permission_background_description)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.no_thanks))
                }
            },
        )
    }

    @Composable
    private fun LocalNetworkPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.local_network_permission_title)) },
            text = { Text(stringResource(R.string.local_network_permission_description)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.no_thanks))
                }
            },
        )
    }
}
