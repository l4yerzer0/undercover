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
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.database.Settings
import com.undercover.vpn.routing.RoutingSection
import com.undercover.vpn.shared.ui.components.DossierSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(navController: NavController) {
    OverrideTopBar {
        // Dossier tactical UI replaces standard TopAppBar
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    var directAppsCount by remember { mutableIntStateOf(0) }
    var byeDpiAppsCount by remember { mutableIntStateOf(0) }
    var byeDpiEnabled by remember { mutableStateOf(false) }

    var directRuSites by remember { mutableStateOf(Settings.routingDirectRuSites) }
    var byeDpiYouTube by remember { mutableStateOf(Settings.routingByeDpiYouTube) }
    var byeDpiDiscord by remember { mutableStateOf(Settings.routingByeDpiDiscord) }

    var customDirectDomains by remember { mutableStateOf(Settings.routingCustomDirectDomains) }
    var customByeDpiDomains by remember { mutableStateOf(Settings.routingCustomByeDpiDomains) }

    var domainDialogSection by remember { mutableStateOf<RoutingSection?>(null) }

    LaunchedEffect(currentEntry) {
        withContext(Dispatchers.IO) {
            val direct = Settings.routingDirectList.size
            val byeDpi = Settings.routingByeDpiList.size
            val enabled = Settings.byeDpiEnabled
            val customDirect = Settings.routingCustomDirectDomains
            val customByeDpi = Settings.routingCustomByeDpiDomains
            withContext(Dispatchers.Main) {
                directAppsCount = direct
                byeDpiAppsCount = byeDpi
                byeDpiEnabled = enabled
                customDirectDomains = customDirect
                customByeDpiDomains = customByeDpi
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

            // Section 1: Ready-made Site Presets
            DossierSectionTitle(title = "ГОТОВЫЕ НАБОРЫ САЙТОВ")

            DossierToggleRow(
                title = "Российские сайты напрямую (DIRECT)",
                subtitle = ".ru, .su, .рф, госуслуги, банки, маркетплейсы идут в обход VPN",
                checked = directRuSites,
                onCheckedChange = { checked ->
                    directRuSites = checked
                    Settings.routingDirectRuSites = checked
                },
            )

            DossierToggleRow(
                title = "YouTube через ByeDPI",
                subtitle = "Обход замедления видео и потоков YouTube через ByeDPI",
                checked = byeDpiYouTube,
                onCheckedChange = { checked ->
                    byeDpiYouTube = checked
                    Settings.routingByeDpiYouTube = checked
                },
            )

            DossierToggleRow(
                title = "Discord через ByeDPI",
                subtitle = "Голосовые и медиасерверы Discord через ByeDPI",
                checked = byeDpiDiscord,
                onCheckedChange = { checked ->
                    byeDpiDiscord = checked
                    Settings.routingByeDpiDiscord = checked
                },
            )

            // Section 2: Application Routing
            DossierSectionTitle(title = "МАРШРУТИЗАЦИЯ ПРИЛОЖЕНИЙ")

            DossierNavRow(
                title = "Прямое подключение (DIRECT)",
                subtitle = "Выбрано приложений: $directAppsCount",
                onClick = {
                    navController.navigate("settings/routing/apps/${RoutingSection.DIRECT.name}")
                },
            )

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

            // Section 3: Custom Domains
            DossierSectionTitle(title = "ПОЛЬЗОВАТЕЛЬСКИЕ САЙТЫ")

            DossierNavRow(
                title = "Свои сайты напрямую (DIRECT)",
                subtitle = if (customDirectDomains.isEmpty()) "Нажмите, чтобы добавить свои домены" else "Добавлено доменов: ${customDirectDomains.size}",
                onClick = { domainDialogSection = RoutingSection.DIRECT },
            )

            DossierNavRow(
                title = "Свои сайты через ByeDPI",
                subtitle = if (customByeDpiDomains.isEmpty()) "Нажмите, чтобы добавить домены для ByeDPI" else "Добавлено доменов: ${customByeDpiDomains.size}",
                onClick = { domainDialogSection = RoutingSection.BYEDPI },
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }

    // Custom Domains Dialog
    if (domainDialogSection != null) {
        val section = domainDialogSection!!
        val isDirect = section == RoutingSection.DIRECT
        val domainList = if (isDirect) customDirectDomains else customByeDpiDomains

        CustomDomainsDialog(
            title = if (isDirect) "СВОИ САЙТЫ // DIRECT" else "СВОИ САЙТЫ // BYEDPI",
            subtitle = if (isDirect) "Эти сайты пойдут в обход VPN напрямую" else "Эти сайты пойдут через ByeDPI",
            domains = domainList,
            onAddDomain = { newDomain ->
                val clean = newDomain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
                if (clean.isNotEmpty()) {
                    if (isDirect) {
                        val updated = customDirectDomains + clean
                        customDirectDomains = updated
                        Settings.routingCustomDirectDomains = updated
                    } else {
                        val updated = customByeDpiDomains + clean
                        customByeDpiDomains = updated
                        Settings.routingCustomByeDpiDomains = updated
                    }
                }
            },
            onRemoveDomain = { domain ->
                if (isDirect) {
                    val updated = customDirectDomains - domain
                    customDirectDomains = updated
                    Settings.routingCustomDirectDomains = updated
                } else {
                    val updated = customByeDpiDomains - domain
                    customByeDpiDomains = updated
                    Settings.routingCustomByeDpiDomains = updated
                }
            },
            onDismiss = { domainDialogSection = null },
        )
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
