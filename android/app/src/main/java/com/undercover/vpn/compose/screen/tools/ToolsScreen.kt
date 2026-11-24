package com.undercover.vpn.compose.screen.tools

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.undercover.vpn.R
import com.undercover.vpn.compose.topbar.OverrideTopBar

@Composable
fun ToolsScreen(
    navController: NavController,
) {
    OverrideTopBar {
        // Dossier header
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
                    text = "UNDERCOVER // TOOLS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = stringResource(R.string.tools_diagnostics),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.title_tools).uppercase(),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Section: Network
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.tools_network_utilities),
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

            DossierToolRow(
                title = stringResource(R.string.network_quality),
                subtitle = stringResource(R.string.tools_network_quality_desc),
                onClick = { navController.navigate("tools/network_quality") },
            )

            DossierToolRow(
                title = stringResource(R.string.stun_test),
                subtitle = stringResource(R.string.tools_stun_test_desc),
                onClick = { navController.navigate("tools/stun_test") },
            )

            DossierToolRow(
                title = stringResource(R.string.tools_active_sessions),
                subtitle = stringResource(R.string.tools_active_sessions_desc),
                onClick = { navController.navigate("tools/connections") },
            )

            DossierToolRow(
                title = stringResource(R.string.byedpi),
                subtitle = stringResource(R.string.tools_byedpi_desc),
                onClick = { navController.navigate("tools/byedpi") },
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

@Composable
private fun DossierToolRow(
    title: String,
    subtitle: String,
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
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
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
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
    )
}
