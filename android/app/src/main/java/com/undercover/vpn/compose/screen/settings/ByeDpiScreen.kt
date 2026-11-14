package com.undercover.vpn.compose.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import com.undercover.vpn.shared.ui.components.DossierListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.undercover.vpn.R
import com.undercover.vpn.byedpi.ByeDpiStrategyTester
import com.undercover.vpn.compose.base.UiEvent
import com.undercover.vpn.compose.base.rememberApplyServiceChangeNotifier
import com.undercover.vpn.constant.Status
import com.undercover.vpn.database.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByeDpiScreen(
    navController: NavController,
    serviceStatus: Status = Status.Stopped,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifyApplyChange = rememberApplyServiceChangeNotifier(serviceStatus)

    var enabled by remember { mutableStateOf(Settings.byeDpiEnabled) }
    var strategy by remember { mutableStateOf(Settings.byeDpiStrategy) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<ByeDpiStrategyTester.StrategyResult>() }

    val serviceRunning = serviceStatus == Status.Started
    val testing = testJob != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.byedpi)) },
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
                .padding(horizontal = 16.dp),
        ) {
            DossierListItem(
                headlineContent = { Text(stringResource(R.string.byedpi)) },
                supportingContent = { Text(stringResource(R.string.byedpi_description)) },
                trailingContent = {
                    com.undercover.vpn.shared.ui.components.DossierSwitch(
                        checked = enabled,
                        enabled = !testing,
                        onCheckedChange = { checked ->
                            enabled = checked
                            scope.launch(Dispatchers.IO) {
                                Settings.byeDpiEnabled = checked
                                withContext(Dispatchers.Main) {
                                    notifyApplyChange(UiEvent.ApplyServiceChange.Mode.Restart)
                                }
                            }
                        },
                    )
                },
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            OutlinedTextField(
                value = strategy,
                onValueChange = { value ->
                    strategy = value
                    scope.launch(Dispatchers.IO) { Settings.byeDpiStrategy = value }
                },
                label = { Text(stringResource(R.string.byedpi_strategy)) },
                enabled = !testing,
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    enabled = !serviceRunning || testing,
                    onClick = {
                        val running = testJob
                        if (running != null) {
                            running.cancel()
                            testJob = null
                            progress = ""
                            return@Button
                        }
                        results.clear()
                        val strategies = ByeDpiStrategyTester.loadStrategies(context)
                        val sites = ByeDpiStrategyTester.loadSites(context)
                        testJob = scope.launch(Dispatchers.IO) {
                            try {
                                ByeDpiStrategyTester.run(
                                    strategies = strategies,
                                    sites = sites,
                                ) { index, result ->
                                    scope.launch(Dispatchers.Main) {
                                        progress = "${index + 1} / ${strategies.size}"
                                        if (index < results.size) {
                                            results[index] = result
                                        } else {
                                            results.add(result)
                                        }
                                    }
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    testJob = null
                                    progress = ""
                                    results.sortByDescending { it.ratio }
                                }
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (testing) R.string.byedpi_test_stop else R.string.byedpi_test_start,
                        ),
                    )
                }

                if (progress.isNotEmpty()) {
                    Text(progress, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (serviceRunning && !testing) {
                Text(
                    stringResource(R.string.byedpi_test_requires_stop),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            if (testing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results) { result ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !testing) {
                                strategy = result.command
                                scope.launch(Dispatchers.IO) {
                                    Settings.byeDpiStrategy = result.command
                                }
                            },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                when {
                                    result.failedToStart ->
                                        stringResource(R.string.byedpi_test_failed)
                                    else -> "${result.percent}%  (${result.success}/${result.total})"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                result.command,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
