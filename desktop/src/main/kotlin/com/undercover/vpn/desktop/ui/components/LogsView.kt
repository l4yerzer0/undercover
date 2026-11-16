package com.undercover.vpn.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.core.SingBoxCoreManager
import kotlinx.coroutines.flow.collectLatest
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun LogsView(modifier: Modifier = Modifier) {
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
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
            .background(Color(0xFF0D1117), RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ENGINE LOGS & DIAGNOSTICS (${logsList.size} LINES)",
                color = Color(0xFF58A6FF),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )

            Row {
                Text(
                    text = "COPY ALL",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable {
                            val text = logsList.joinToString("\n")
                            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                        }
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CLEAR",
                    color = Color(0xFFFF7B72),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { logsList.clear() }
                        .border(1.dp, Color(0xFFFF7B72).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Logs display
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050709), RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            items(logsList) { line ->
                val lineColor = when {
                    line.contains("[ERROR]") || line.contains("error") -> Color(0xFFFF3366)
                    line.contains("[WARN]") || line.contains("warn") -> Color(0xFFFFB703)
                    line.contains("[APP]") -> Color(0xFF00FF66)
                    line.contains("[CORE]") || line.contains("[CONFIG]") -> Color(0xFF58A6FF)
                    else -> Color(0xFFC9D1D9)
                }
                Text(
                    text = line,
                    color = lineColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
