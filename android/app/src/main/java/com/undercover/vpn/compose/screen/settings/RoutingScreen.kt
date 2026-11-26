package com.undercover.vpn.compose.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.undercover.vpn.R
import com.undercover.vpn.compose.screen.dashboard.UserProfilePickerSheet
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.database.Profile
import com.undercover.vpn.database.ProfileManager
import com.undercover.vpn.database.Settings
import com.undercover.vpn.database.UserProfile
import com.undercover.vpn.database.UserProfileManager
import com.undercover.vpn.routing.RoutingSection
import com.undercover.vpn.shared.ui.components.DossierSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(navController: NavController) {
    OverrideTopBar {
        // Dossier tactical UI replaces standard TopAppBar
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val isProMode by com.undercover.vpn.database.AppModeState.isProFlow.collectAsState()

    var userProfiles by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var activeUserProfile by remember { mutableStateOf<UserProfile?>(null) }
    var allServers by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var profileNodesMap by remember { mutableStateOf<Map<Long, com.undercover.vpn.shared.parser.ProfileNodesInfo>>(emptyMap()) }
    var showUserProfilePicker by remember { mutableStateOf(false) }
    var showSecondaryServerPicker by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<com.undercover.vpn.database.RoutingChannel?>(null) }
    var showNewChannelSheet by remember { mutableStateOf(false) }

    var directAppsCount by remember { mutableIntStateOf(0) }
    var byeDpiAppsCount by remember { mutableIntStateOf(0) }
    var secondaryAppsCount by remember { mutableIntStateOf(0) }
    var byeDpiEnabled by remember { mutableStateOf(false) }

    var directRuSites by remember { mutableStateOf(true) }
    var directRuApps by remember { mutableStateOf(true) }
    var byeDpiYouTube by remember { mutableStateOf(false) }
    var byeDpiDiscord by remember { mutableStateOf(false) }
    var dualServerEnabled by remember { mutableStateOf(false) }

    var customDirectDomains by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customByeDpiDomains by remember { mutableStateOf<Set<String>>(emptySet()) }
    var customSecondaryDomains by remember { mutableStateOf<Set<String>>(emptySet()) }

    var ruleSetsCount by remember { mutableIntStateOf(com.undercover.vpn.routing.RuleSetManager.downloadedCount()) }
    var ruleSetsUpdatedAt by remember { mutableStateOf(Settings.routingRuleSetsUpdatedAt) }
    var isUpdatingRuleSets by remember { mutableStateOf(false) }
    var ruleSetStatusMessage by remember { mutableStateOf<String?>(null) }

    var domainDialogSection by remember { mutableStateOf<RoutingSection?>(null) }

    fun refreshState(active: UserProfile) {
        activeUserProfile = active
        directRuSites = active.routingDirectRuSites
        directRuApps = active.routingDirectRuApps
        byeDpiYouTube = active.routingByeDpiYouTube
        byeDpiDiscord = active.routingByeDpiDiscord
        dualServerEnabled = active.dualServerEnabled
        customDirectDomains = active.directDomains.toSet()
        customByeDpiDomains = active.byeDpiDomains.toSet()
        customSecondaryDomains = active.secondaryDomains.toSet()
        directAppsCount = Settings.routingList(RoutingSection.DIRECT).size
        byeDpiAppsCount = Settings.routingList(RoutingSection.BYEDPI).size
        secondaryAppsCount = Settings.routingList(RoutingSection.SECONDARY).size
        byeDpiEnabled = Settings.byeDpiEnabled
    }

    LaunchedEffect(currentEntry) {
        withContext(Dispatchers.IO) {
            val uProfiles = UserProfileManager.list()
            val active = UserProfileManager.getActiveProfile()
            val servers = ProfileManager.list()
            val nodesMap = servers.mapNotNull { p ->
                val f = java.io.File(p.typed.path)
                if (!f.exists()) return@mapNotNull null
                val info = com.undercover.vpn.shared.parser.SubscriptionNodesHelper.extractNodesInfo(f.readText(), p.typed.path)
                if (info != null) p.id to info else null
            }.toMap()
            withContext(Dispatchers.Main) {
                userProfiles = uProfiles
                allServers = servers
                profileNodesMap = nodesMap
                refreshState(active)
            }
        }
    }

    fun persistProfile(updateBlock: (UserProfile) -> Unit) {
        val current = activeUserProfile ?: return
        updateBlock(current)
        coroutineScope.launch(Dispatchers.IO) {
            UserProfileManager.update(current)
            runCatching {
                io.nekohasekai.libbox.Libbox.newStandaloneCommandClient().serviceReload()
            }
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
            Spacer(modifier = Modifier.height(24.dp))

            // Top Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { navController.navigateUp() },
                ) {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.content_description_back),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BACK // ROUTING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                Text(
                    text = "UNDERCOVER // RULES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title Block
            Text(
                text = "МАРШРУТИЗАЦИЯ",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Раздельное туннелирование сайтов и приложений",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Operational Profile Switcher (PRO only)
            if (isProMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                        .clickable { showUserProfilePicker = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dossier_operational_profile_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeUserProfile?.name ?: stringResource(R.string.default_text),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "[ СМЕНИТЬ ]",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Section 1: Ready-made Site Presets
            DossierSectionTitle(title = "ГОТОВЫЕ НАБОРЫ САЙТОВ")

            DossierToggleRow(
                title = "Российские сайты напрямую (DIRECT)",
                subtitle = ".ru, .su, .рф, госуслуги, банки, маркетплейсы идут в обход VPN",
                checked = directRuSites,
                onCheckedChange = { checked ->
                    directRuSites = checked
                    Settings.routingDirectRuSites = checked
                    persistProfile { it.routingDirectRuSites = checked }
                },
            )

            DossierToggleRow(
                title = "YouTube через ByeDPI",
                subtitle = "Обход замедления видео и потоков YouTube через ByeDPI",
                checked = byeDpiYouTube,
                onCheckedChange = { checked ->
                    byeDpiYouTube = checked
                    Settings.routingByeDpiYouTube = checked
                    persistProfile { it.routingByeDpiYouTube = checked }
                },
            )

            DossierToggleRow(
                title = "Discord через ByeDPI",
                subtitle = "Голосовые и медиасерверы Discord через ByeDPI",
                checked = byeDpiDiscord,
                onCheckedChange = { checked ->
                    byeDpiDiscord = checked
                    Settings.routingByeDpiDiscord = checked
                    persistProfile { it.routingByeDpiDiscord = checked }
                },
            )

            // Section 2: Application Routing
            DossierSectionTitle(title = "МАРШРУТИЗАЦИЯ ПРИЛОЖЕНИЙ")

            DossierToggleRow(
                title = "Российские приложения напрямую (DIRECT)",
                subtitle = "Сбер, Т-Банк, ВТБ, Госуслуги, Ozon, WB, Яндекс и др. по обновляемой базе ru-app-list",
                checked = directRuApps,
                onCheckedChange = { checked ->
                    directRuApps = checked
                    Settings.routingDirectRuApps = checked
                    persistProfile { it.routingDirectRuApps = checked }
                },
            )

            DossierNavRow(
                title = "Выбрать приложения напрямую (DIRECT)",
                subtitle = if (directAppsCount > 0) "Выбрано приложений: $directAppsCount" else "Нажмите, чтобы исключить приложения из VPN",
                onClick = {
                    navController.navigate("settings/routing/apps/${RoutingSection.DIRECT.name}")
                },
            )

            if (isProMode || byeDpiEnabled) {
                DossierNavRow(
                    title = "Обход блокировок (ByeDPI)",
                    subtitle = if (byeDpiEnabled) {
                        "Выбрано приложений: $byeDpiAppsCount"
                    } else {
                        "ByeDPI отключен в настройках"
                    },
                    onClick = {
                        navController.navigate("settings/routing/apps/${RoutingSection.BYEDPI.name}")
                    },
                )
            }

            // Section 3: Custom Domains
            DossierSectionTitle(title = "ПОЛЬЗОВАТЕЛЬСКИЕ САЙТЫ")

            DossierNavRow(
                title = "Свои сайты напрямую (DIRECT)",
                subtitle = if (customDirectDomains.isEmpty()) "Нажмите, чтобы добавить свои домены в обход VPN" else "Добавлено доменов: ${customDirectDomains.size}",
                onClick = { domainDialogSection = RoutingSection.DIRECT },
            )

            if (isProMode || byeDpiEnabled) {
                DossierNavRow(
                    title = "Свои сайты через ByeDPI",
                    subtitle = if (customByeDpiDomains.isEmpty()) "Нажмите, чтобы добавить домены для ByeDPI" else "Добавлено доменов: ${customByeDpiDomains.size}",
                    onClick = { domainDialogSection = RoutingSection.BYEDPI },
                )
            }

            // Section 4: Multi-Server Channels (PRO only)
            if (isProMode) {
                val channels = activeUserProfile?.channels.orEmpty()
                DossierSectionTitle(title = stringResource(R.string.dossier_active_channels_label, channels.size + 1))

                channels.forEachIndexed { idx, ch ->
                    val chServerName = allServers.find { it.id == ch.serverId }?.name
                        ?: stringResource(R.string.dossier_select_secondary_server)
                    val title = "КАНАЛ ${idx + 2} · $chServerName" +
                        if (!ch.nodeTag.isNullOrBlank()) " (${ch.nodeTag})" else ""
                    val subtitle = "${ch.categories.size} катег. · ${ch.apps.size} прил. · ${ch.domains.size} дом."
                    DossierNavRow(
                        title = title,
                        subtitle = subtitle,
                        onClick = { editingChannel = ch },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        .clickable { showNewChannelSheet = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.dossier_add_channel_btn),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Section 5: Updatable Rule-Sets (.srs)
            DossierSectionTitle(title = "ОБНОВЛЯЕМЫЕ БАЗЫ (.SRS)")

            val updatedLabel = remember(ruleSetsUpdatedAt, ruleSetsCount) {
                if (ruleSetsUpdatedAt > 0L && ruleSetsCount > 0) {
                    val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    "Загружено баз: $ruleSetsCount/${com.undercover.vpn.routing.RuleSetManager.SPECS.size} · ${fmt.format(java.util.Date(ruleSetsUpdatedAt))}"
                } else {
                    "Базы не загружены (используются встроенные списки)"
                }
            }

            DossierNavRow(
                title = if (isUpdatingRuleSets) "Обновление баз..." else "Обновить базы маршрутизации",
                subtitle = ruleSetStatusMessage ?: updatedLabel,
                onClick = {
                    if (!isUpdatingRuleSets) {
                        isUpdatingRuleSets = true
                        ruleSetStatusMessage = "Загрузка geosite-ru, geoip-ru, ru-app-list..."
                        coroutineScope.launch {
                            val result = com.undercover.vpn.routing.RuleSetManager.updateAll(force = true)
                            ruleSetsCount = com.undercover.vpn.routing.RuleSetManager.downloadedCount()
                            ruleSetsUpdatedAt = Settings.routingRuleSetsUpdatedAt
                            isUpdatingRuleSets = false
                            ruleSetStatusMessage = result.fold(
                                onSuccess = { count ->
                                    if (count > 0) {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                io.nekohasekai.libbox.Libbox.newStandaloneCommandClient().serviceReload()
                                            }
                                        }
                                        "Обновлено баз: $count/${com.undercover.vpn.routing.RuleSetManager.SPECS.size}"
                                    } else {
                                        "Базы актуальны"
                                    }
                                },
                                onFailure = { err ->
                                    "Ошибка загрузки: ${err.message ?: "проверьте сеть"}"
                                },
                            )
                        }
                    }
                },
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // Custom Domains Dialog
    if (domainDialogSection != null) {
        val section = domainDialogSection!!
        val title = when (section) {
            RoutingSection.DIRECT -> "СВОИ САЙТЫ // DIRECT"
            RoutingSection.BYEDPI -> "СВОИ САЙТЫ // BYEDPI"
            RoutingSection.SECONDARY -> "СВОИ САЙТЫ // СЕРВЕР 2 (DUAL)"
        }
        val subtitle = when (section) {
            RoutingSection.DIRECT -> "Эти сайты пойдут в обход VPN напрямую"
            RoutingSection.BYEDPI -> "Эти сайты пойдут через ByeDPI"
            RoutingSection.SECONDARY -> "Эти сайты пойдут через второй активный сервер (Server 2)"
        }
        val domainList = when (section) {
            RoutingSection.DIRECT -> customDirectDomains
            RoutingSection.BYEDPI -> customByeDpiDomains
            RoutingSection.SECONDARY -> customSecondaryDomains
        }

        CustomDomainsDialog(
            title = title,
            subtitle = subtitle,
            domains = domainList,
            onAddDomain = { newDomain ->
                val clean = newDomain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
                if (clean.isNotEmpty()) {
                    when (section) {
                        RoutingSection.DIRECT -> {
                            val updated = customDirectDomains + clean
                            customDirectDomains = updated
                            Settings.routingCustomDirectDomains = updated
                            persistProfile { it.directDomains = updated.toList() }
                        }
                        RoutingSection.BYEDPI -> {
                            val updated = customByeDpiDomains + clean
                            customByeDpiDomains = updated
                            Settings.routingCustomByeDpiDomains = updated
                            persistProfile { it.byeDpiDomains = updated.toList() }
                        }
                        RoutingSection.SECONDARY -> {
                            val updated = customSecondaryDomains + clean
                            customSecondaryDomains = updated
                            persistProfile { it.secondaryDomains = updated.toList() }
                        }
                    }
                }
            },
            onRemoveDomain = { domain ->
                when (section) {
                    RoutingSection.DIRECT -> {
                        val updated = customDirectDomains - domain
                        customDirectDomains = updated
                        Settings.routingCustomDirectDomains = updated
                        persistProfile { it.directDomains = updated.toList() }
                    }
                    RoutingSection.BYEDPI -> {
                        val updated = customByeDpiDomains - domain
                        customByeDpiDomains = updated
                        Settings.routingCustomByeDpiDomains = updated
                        persistProfile { it.byeDpiDomains = updated.toList() }
                    }
                    RoutingSection.SECONDARY -> {
                        val updated = customSecondaryDomains - domain
                        customSecondaryDomains = updated
                        persistProfile { it.secondaryDomains = updated.toList() }
                    }
                }
            },
            onDismiss = { domainDialogSection = null },
        )
    }

    if (showUserProfilePicker) {
        UserProfilePickerSheet(
            userProfiles = userProfiles,
            activeProfileId = activeUserProfile?.id ?: 1L,
            allServers = allServers,
            onSelectProfile = { id ->
                coroutineScope.launch(Dispatchers.IO) {
                    UserProfileManager.setActiveProfile(id)
                    val active = UserProfileManager.getActiveProfile()
                    withContext(Dispatchers.Main) {
                        refreshState(active)
                    }
                }
                showUserProfilePicker = false
            },
            onCreateProfile = { name ->
                coroutineScope.launch(Dispatchers.IO) {
                    val newProf = UserProfile(name = name)
                    UserProfileManager.create(newProf, andSelect = true)
                    val uProfiles = UserProfileManager.list()
                    val active = UserProfileManager.getActiveProfile()
                    withContext(Dispatchers.Main) {
                        userProfiles = uProfiles
                        refreshState(active)
                    }
                }
            },
            onDeleteProfile = { prof ->
                coroutineScope.launch(Dispatchers.IO) {
                    UserProfileManager.delete(prof)
                    val uProfiles = UserProfileManager.list()
                    val active = UserProfileManager.getActiveProfile()
                    withContext(Dispatchers.Main) {
                        userProfiles = uProfiles
                        refreshState(active)
                    }
                }
            },
            onDismiss = { showUserProfilePicker = false },
        )
    }

    if (showNewChannelSheet || editingChannel != null) {
        com.undercover.vpn.compose.screen.dashboard.ChannelEditorSheet(
            initialChannel = editingChannel,
            profiles = allServers,
            profileNodesMap = profileNodesMap,
            defaultPrimaryServerId = activeUserProfile?.primaryServerId ?: -1L,
            onSave = { ch ->
                persistProfile { prof ->
                    val list = prof.channels.toMutableList()
                    val idx = list.indexOfFirst { it.id == ch.id }
                    if (idx >= 0) list[idx] = ch else list.add(ch)
                    prof.channels = list
                }
                activeUserProfile?.let { refreshState(it) }
                showNewChannelSheet = false
                editingChannel = null
            },
            onDelete = { chId ->
                persistProfile { prof ->
                    prof.channels = prof.channels.filterNot { it.id == chId }
                }
                activeUserProfile?.let { refreshState(it) }
                showNewChannelSheet = false
                editingChannel = null
            },
            onOpenAppsPicker = { ch ->
                showNewChannelSheet = false
                editingChannel = null
                navController.navigate("settings/routing/apps/${RoutingSection.SECONDARY.name}?channelId=${ch.id}")
            },
            onDismiss = {
                showNewChannelSheet = false
                editingChannel = null
            },
        )
    }

    if (showSecondaryServerPicker) {
        val secSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showSecondaryServerPicker = false },
            sheetState = secSheetState,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.dossier_dual_server_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dossier_select_secondary_server),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(allServers) { prof ->
                        val isSelected = prof.id == activeUserProfile?.secondaryServerId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                )
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                                )
                                .clickable {
                                    persistProfile {
                                        it.secondaryServerId = prof.id
                                        it.secondaryNodeTag = null
                                    }
                                    showSecondaryServerPicker = false
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isSelected) "[ X ]" else "[   ]",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = prof.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CustomDomainsDialog(
    title: String,
    subtitle: String,
    domains: Set<String>,
    onAddDomain: (String) -> Unit,
    onRemoveDomain: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var textInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (textInput.isNotBlank()) {
                                onAddDomain(textInput)
                                textInput = ""
                            }
                        }),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                            .clickable {
                                if (textInput.isNotBlank()) {
                                    onAddDomain(textInput)
                                    textInput = ""
                                }
                            }
                            .padding(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // List of domains
                if (domains.isEmpty()) {
                    Text(
                        text = "Нет добавленных доменов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        items(domains.toList()) { domain ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                IconButton(
                                    onClick = { onRemoveDomain(domain) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "[ ГОТОВО ]",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun DossierSectionTitle(title: String) {
    Spacer(modifier = Modifier.height(32.dp))
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
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun DossierToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        DossierSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}

@Composable
private fun DossierNavRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Text(
            text = "→",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    )
}
