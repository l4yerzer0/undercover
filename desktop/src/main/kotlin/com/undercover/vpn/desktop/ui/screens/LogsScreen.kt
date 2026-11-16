package com.undercover.vpn.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.core.SingBoxCoreManager
import com.undercover.vpn.desktop.ui.DossierMint
import com.undercover.vpn.desktop.ui.DossierRed
import kotlinx.coroutines.flow.collectLatest
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun LogsScreen(modifier: Modifier = Modifier) {
    val logsList = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        logsList.clear()
        logsList.addAll(SingBoxCoreManager.getRecentLogs())
        SingBoxCoreManager.logsFlow.collectLatest { logLine ->
            logsList.add(logLine)
            if (logsList.size > 1000) logsList.removeAt(0)
            listState.animateScrollToItem(logsList.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LOGS (${logsList.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )

            Row {
                Text(
                    text = "COPY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clickable {
                            val text = logsList.joinToString("\n")
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "CLEAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = DossierRed,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clickable { logsList.clear() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        )

        // Log content
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            items(logsList) { line ->
                val color = when {
                    line.contains("[ERROR]") || line.contains("error") -> DossierRed
                    line.contains("[WARN]") || line.contains("warn") -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    line.contains("[APP]") || line.contains("[CORE]") -> DossierMint
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                }
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
