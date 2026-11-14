package com.undercover.vpn.compose.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.undercover.vpn.shared.ui.components.DossierListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.undercover.vpn.R
import com.undercover.vpn.database.Settings
import com.undercover.vpn.routing.RoutingSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point for per-app routing: one row per [RoutingSection], each opening the
 * app picker for that section's list.
 *
 * Assignments become sing-box route rules at service start — nothing here touches
 * VpnService's own app lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingScreen(navController: NavController) {
    // Re-read whenever this screen comes back to the top of the stack: the picker
    // writes the lists on its own, so a value captured once would go stale as soon
    // as the user assigns an app and navigates back.
    val currentEntry by navController.currentBackStackEntryAsState()
    var directCount by remember { mutableIntStateOf(0) }
    var byeDpiCount by remember { mutableIntStateOf(0) }
    var byeDpiEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(currentEntry) {
        withContext(Dispatchers.IO) {
            val direct = Settings.routingDirectList.size
            val byeDpi = Settings.routingByeDpiList.size
            val enabled = Settings.byeDpiEnabled
            withContext(Dispatchers.Main) {
                directCount = direct
                byeDpiCount = byeDpi
                byeDpiEnabled = enabled
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routing)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.routing_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            DossierListItem(
                headlineContent = { Text(stringResource(R.string.routing_direct)) },
                supportingContent = {
                    Text(stringResource(R.string.routing_apps_selected, directCount))
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.CallSplit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        navController.navigate("settings/routing/apps/${RoutingSection.DIRECT.name}")
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            DossierListItem(
                headlineContent = { Text(stringResource(R.string.routing_byedpi)) },
                supportingContent = {
                    Text(
                        if (byeDpiEnabled) {
                            stringResource(R.string.routing_apps_selected, byeDpiCount)
                        } else {
                            stringResource(R.string.routing_byedpi_disabled)
                        },
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        navController.navigate("settings/routing/apps/${RoutingSection.BYEDPI.name}")
                    },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}
