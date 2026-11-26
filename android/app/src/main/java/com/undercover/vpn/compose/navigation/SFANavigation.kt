package com.undercover.vpn.compose.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.undercover.vpn.compose.screen.configuration.NewProfileScreen
import com.undercover.vpn.compose.screen.connections.ConnectionDetailsRoute
import com.undercover.vpn.compose.screen.connections.ConnectionsConfigsScreen
import com.undercover.vpn.compose.screen.connections.ConnectionsPage
import com.undercover.vpn.compose.screen.connections.ConnectionsViewModel
import com.undercover.vpn.compose.screen.dashboard.DashboardScreen
import com.undercover.vpn.compose.screen.dashboard.DashboardViewModel
import com.undercover.vpn.compose.screen.dashboard.GroupsCard
import com.undercover.vpn.compose.screen.dashboard.groups.GroupsViewModel
import com.undercover.vpn.compose.screen.log.HookLogScreen
import com.undercover.vpn.compose.screen.log.LogScreen
import com.undercover.vpn.compose.screen.log.LogViewModel
import com.undercover.vpn.compose.screen.privilegesettings.PrivilegeSettingsManageScreen
import com.undercover.vpn.compose.screen.profile.EditProfileContentScreen
import com.undercover.vpn.compose.screen.profile.EditProfileRoute
import com.undercover.vpn.compose.screen.profileoverride.PerAppProxyScreen
import com.undercover.vpn.compose.screen.settings.ByeDpiScreen
import com.undercover.vpn.compose.screen.settings.RoutingScreen
import com.undercover.vpn.routing.RoutingSection
import com.undercover.vpn.compose.screen.settings.AppSettingsScreen
import com.undercover.vpn.compose.screen.settings.CoreSettingsScreen
import com.undercover.vpn.compose.screen.settings.DeveloperProfilesJsonScreen
import com.undercover.vpn.compose.screen.settings.DeveloperSettingsScreen
import com.undercover.vpn.compose.screen.settings.EditRemoteServerScreen
import com.undercover.vpn.compose.screen.settings.FDroidMirrorScreen
import com.undercover.vpn.compose.screen.settings.PrivilegeSettingsScreen
import com.undercover.vpn.compose.screen.settings.ProfileOverrideScreen
import com.undercover.vpn.compose.screen.settings.RemoteControlScreen
import com.undercover.vpn.compose.screen.settings.ServiceSettingsScreen
import com.undercover.vpn.compose.screen.settings.SettingsScreen
import com.undercover.vpn.compose.screen.tools.CrashReportDetailScreen
import com.undercover.vpn.compose.screen.tools.CrashReportFileContentScreen
import com.undercover.vpn.compose.screen.tools.CrashReportListScreen
import com.undercover.vpn.compose.screen.tools.CrashReportMetadataScreen
import com.undercover.vpn.compose.screen.tools.NetworkQualityScreen
import com.undercover.vpn.compose.screen.tools.OOMReportDetailScreen
import com.undercover.vpn.compose.screen.tools.OOMReportFileContentScreen
import com.undercover.vpn.compose.screen.tools.OOMReportListScreen
import com.undercover.vpn.compose.screen.tools.OOMReportMetadataScreen
import com.undercover.vpn.compose.screen.tools.OutboundPickerScreen
import com.undercover.vpn.compose.screen.tools.STUNTestScreen
import com.undercover.vpn.compose.screen.tools.ToolsScreen
import com.undercover.vpn.constant.Status

private val slideInFromRight: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
}

private val slideOutToRight: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
}

private val slideInFromLeft: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
}

private val slideOutToLeft: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
}

@Composable
fun SFANavHost(
    navController: NavHostController,
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    newProfileArgs: NewProfileArgs = NewProfileArgs(),
    onClearNewProfileArgs: () -> Unit = {},
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    dashboardViewModel: DashboardViewModel? = null,
    logViewModel: LogViewModel? = null,
    groupsViewModel: GroupsViewModel? = null,
    connectionsViewModel: ConnectionsViewModel? = null,
    onToggleConnection: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = if (com.undercover.vpn.database.Settings.hasSeenOnboarding) Screen.Dashboard.route else "onboarding",
        modifier = modifier,
    ) {
        composable("onboarding") {
            com.undercover.vpn.compose.screen.onboarding.OnboardingScreen(onFinish = {
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.undercover.vpn.database.Settings.hasSeenOnboarding = true
                }
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }

        composable(Screen.Dashboard.route) {
            if (dashboardViewModel != null) {
                DashboardScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    onOpenNewProfile = onOpenNewProfile,
                    viewModel = dashboardViewModel,
                    onToggleConnection = onToggleConnection,
                    onProfileOpen = { profile -> navController.navigate(ProfileRoutes.editProfile(profile.id)) },
                    onOpenChannelApps = { chId ->
                        navController.navigate("settings/routing/apps/${com.undercover.vpn.routing.RoutingSection.SECONDARY.name}?channelId=$chId")
                    },
                )
            } else {
                DashboardScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    onOpenNewProfile = onOpenNewProfile,
                    onToggleConnection = onToggleConnection,
                    onProfileOpen = { profile -> navController.navigate(ProfileRoutes.editProfile(profile.id)) },
                    onOpenChannelApps = { chId ->
                        navController.navigate("settings/routing/apps/${com.undercover.vpn.routing.RoutingSection.SECONDARY.name}?channelId=$chId")
                    },
                )
            }
        }

        composable(Screen.Log.route) {
            if (logViewModel != null) {
                LogScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    viewModel = logViewModel,
                    onBack = { navController.navigateUp() },
                )
            } else {
                LogScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    onBack = { navController.navigateUp() },
                )
            }
        }

        composable(Screen.Groups.route) {
            if (groupsViewModel != null) {
                GroupsCard(
                    serviceStatus = serviceStatus,
                    viewModel = groupsViewModel,
                    showTopBar = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                GroupsCard(
                    serviceStatus = serviceStatus,
                    showTopBar = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(Screen.Connections.route) {
            ConnectionsConfigsScreen(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                onOpenNewProfile = onOpenNewProfile,
            )
        }

        composable(
            route = "tools/connections",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            if (connectionsViewModel != null) {
                ConnectionsPage(
                    serviceStatus = serviceStatus,
                    viewModel = connectionsViewModel,
                    showTitle = false,
                    showTopBar = true,
                    onConnectionClick = { connectionId ->
                        navController.navigate("connections/detail/${Uri.encode(connectionId)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ConnectionsPage(
                    serviceStatus = serviceStatus,
                    showTitle = false,
                    showTopBar = true,
                    onConnectionClick = { connectionId ->
                        navController.navigate("connections/detail/${Uri.encode(connectionId)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(ProfileRoutes.NewProfile) {
            DisposableEffect(Unit) {
                onDispose { onClearNewProfileArgs() }
            }
            NewProfileScreen(
                importName = newProfileArgs.importName,
                importUrl = newProfileArgs.importUrl,
                qrsData = newProfileArgs.qrsData,
                assignedProfileId = newProfileArgs.assignedProfileId,
                onNavigateBack = {
                    onClearNewProfileArgs()
                    navController.navigateUp()
                },
                onProfileCreated = { profileId, isManual ->
                    onClearNewProfileArgs()
                    if (isManual) {
                        navController.navigate(ProfileRoutes.editProfile(profileId)) {
                            popUpTo(ProfileRoutes.NewProfile) {
                                inclusive = true
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(
            route = ProfileRoutes.EditProfile,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: -1L
            EditProfileRoute(
                profileId = profileId,
                onNavigateBack = { navController.navigateUp() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable("connections/detail/{connectionId}") { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getString("connectionId")
            if (connectionId != null) {
                if (connectionsViewModel != null) {
                    ConnectionDetailsRoute(
                        connectionId = connectionId,
                        serviceStatus = serviceStatus,
                        viewModel = connectionsViewModel,
                        onBack = { navController.navigateUp() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ConnectionDetailsRoute(
                        connectionId = connectionId,
                        serviceStatus = serviceStatus,
                        onBack = { navController.navigateUp() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composable(Screen.Tools.route) {
            ToolsScreen(navController = navController)
        }

        // Tools subscreens with slide animations
        composable(
            route = "tools/network_quality",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            NetworkQualityScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "tools/stun_test",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            STUNTestScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "tools/outbound_picker/{selectedOutbound}",
            arguments = listOf(navArgument("selectedOutbound") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val selectedOutbound = Uri.decode(backStackEntry.arguments?.getString("selectedOutbound") ?: "")
            OutboundPickerScreen(navController = navController, selectedOutbound = selectedOutbound)
        }

        composable(
            route = "tools/crash_report",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            CrashReportListScreen(navController = navController)
        }

        composable(
            route = "tools/crash_report/{reportId}",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            CrashReportDetailScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/crash_report/{reportId}/metadata",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            CrashReportMetadataScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/crash_report/{reportId}/file/{fileKind}",
            arguments = listOf(
                navArgument("reportId") { type = NavType.StringType },
                navArgument("fileKind") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            val fileKind = backStackEntry.arguments?.getString("fileKind") ?: return@composable
            CrashReportFileContentScreen(navController = navController, reportId = reportId, fileKind = fileKind)
        }

        composable(
            route = "tools/oom_report",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            OOMReportListScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "tools/oom_report/{reportId}",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            OOMReportDetailScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/oom_report/{reportId}/metadata",
            arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            OOMReportMetadataScreen(navController = navController, reportId = reportId)
        }

        composable(
            route = "tools/oom_report/{reportId}/file/{fileKind}",
            arguments = listOf(
                navArgument("reportId") { type = NavType.StringType },
                navArgument("fileKind") { type = NavType.StringType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
            val fileKind = backStackEntry.arguments?.getString("fileKind") ?: return@composable
            OOMReportFileContentScreen(navController = navController, reportId = reportId, fileKind = fileKind)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        // Settings subscreens with slide animations
        composable(
            route = "settings/app",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            AppSettingsScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/fdroid_mirror",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            FDroidMirrorScreen(navController = navController)
        }

        composable(
            route = "settings/routing",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            RoutingScreen(navController = navController)
        }

        composable(
            route = "settings/routing/apps/{section}?channelId={channelId}",
            arguments = listOf(
                androidx.navigation.navArgument("channelId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            PerAppProxyScreen(
                onBack = { navController.navigateUp() },
                serviceStatus = serviceStatus,
                section = RoutingSection.fromName(backStackEntry.arguments?.getString("section")),
                channelId = backStackEntry.arguments?.getString("channelId"),
            )
        }

        composable(
            route = "tools/byedpi",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ByeDpiScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/core",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToRight,
            popEnterTransition = slideInFromRight,
            popExitTransition = slideOutToRight,
        ) {
            CoreSettingsScreen(navController = navController)
        }

        composable(
            route = "settings/developer",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            DeveloperSettingsScreen(navController = navController)
        }

        composable(
            route = "settings/developer/profiles_json",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            DeveloperProfilesJsonScreen(
                navController = navController,
                onProfileSelect = { profileId, isReadOnly ->
                    navController.navigate("settings/developer/profile_content/$profileId/$isReadOnly")
                },
            )
        }

        composable(
            route = "settings/developer/profile_content/{profileId}/{isReadOnly}",
            arguments = listOf(
                navArgument("profileId") { type = NavType.LongType },
                navArgument("isReadOnly") { type = NavType.BoolType },
            ),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: -1L
            val isReadOnly = backStackEntry.arguments?.getBoolean("isReadOnly") ?: false

            EditProfileContentScreen(
                profileId = profileId,
                onNavigateBack = { navController.navigateUp() },
                isReadOnly = isReadOnly,
            )
        }

        composable(
            route = "settings/service",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ServiceSettingsScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/profile_override",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ProfileOverrideScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/profile_override/manage",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PerAppProxyScreen(onBack = { navController.navigateUp() }, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/remote_control",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            RemoteControlScreen(navController = navController)
        }

        composable(
            route = "settings/remote_control/new",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            EditRemoteServerScreen(navController = navController)
        }

        composable(
            route = "settings/remote_control/edit/{serverId}",
            arguments = listOf(navArgument("serverId") { type = NavType.LongType }),
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: -1L
            EditRemoteServerScreen(navController = navController, serverId = serverId)
        }

        composable(
            route = "settings/privilege",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PrivilegeSettingsScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/privilege/manage",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PrivilegeSettingsManageScreen(onBack = { navController.navigateUp() }, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/privilege/logs",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            HookLogScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "settings/log",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            LogScreen(
                serviceStatus = serviceStatus,
                showStartFab = false,
                showStatusBar = false,
                viewModel = logViewModel,
                onBack = { navController.navigateUp() },
            )
        }
    }
}
