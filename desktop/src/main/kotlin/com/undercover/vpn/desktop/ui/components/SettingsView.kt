package com.undercover.vpn.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.core.SingBoxCoreManager
import com.undercover.vpn.desktop.model.DesktopSettings
import com.undercover.vpn.desktop.model.ProxyMode
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    settings: DesktopSettings,
    onSaveSettings: (DesktopSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember(settings) { mutableStateOf(settings.proxyMode) }
    var socksPortStr by remember(settings) { mutableStateOf(settings.socksPort.toString()) }
    var httpPortStr by remember(settings) { mutableStateOf(settings.httpPort.toString()) }
    var autoConnect by remember(settings) { mutableStateOf(settings.autoConnect) }
    var minimizeToTray by remember(settings) { mutableStateOf(settings.minimizeToTray) }

    var detectedCorePath by remember { mutableStateOf(SingBoxCoreManager.findSingBoxBinary()?.absolutePath) }
    var isDownloadingCore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
            .background(Color(0xFF161B22), RoundedCornerShape(6.dp))
            .padding(24.dp)
    ) {
        Text(
            text = "APPLICATION SETTINGS & CORE CONFIGURATION",
            color = Color(0xFF00FF66),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Routing Mode Section
        Text(
            text = "ROUTING & PROXY ENGINE MODE:",
            color = Color(0xFF8B949E),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            // System Proxy Mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (mode == ProxyMode.SYSTEM_PROXY) Color(0xFF00FF66) else Color(0xFF30363D),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (mode == ProxyMode.SYSTEM_PROXY) Color(0xFF00FF66).copy(alpha = 0.1f) else Color(0xFF0D1117),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable {
                        mode = ProxyMode.SYSTEM_PROXY
                        onSaveSettings(settings.copy(proxyMode = ProxyMode.SYSTEM_PROXY))
                    }
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "SYSTEM PROXY (RECOMMENDED)",
                        color = if (mode == ProxyMode.SYSTEM_PROXY) Color(0xFF00FF66) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configures OS SOCKS5/HTTP proxy. Zero root/admin permissions needed. Fast and stable.",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // TUN Adapter Mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        1.dp,
                        if (mode == ProxyMode.TUN_VPN) Color(0xFF00FF66) else Color(0xFF30363D),
                        RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (mode == ProxyMode.TUN_VPN) Color(0xFF00FF66).copy(alpha = 0.1f) else Color(0xFF0D1117),
                        RoundedCornerShape(4.dp)
                    )
                    .clickable {
                        mode = ProxyMode.TUN_VPN
                        onSaveSettings(settings.copy(proxyMode = ProxyMode.TUN_VPN))
                    }
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "TUN VIRTUAL ADAPTER (FULL VPN)",
                        color = if (mode == ProxyMode.TUN_VPN) Color(0xFF00FF66) else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Virtual network interface. Intercepts 100% of system traffic. Requires root/sudo.",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Local Ports Row
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LOCAL SOCKS5 PORT:",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = socksPortStr,
                    onValueChange = {
                        socksPortStr = it
                        val p = it.toIntOrNull()
                        if (p != null && p in 1024..65535) {
                            onSaveSettings(settings.copy(socksPort = p))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF66),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LOCAL HTTP PROXY PORT:",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = httpPortStr,
                    onValueChange = {
                        httpPortStr = it
                        val p = it.toIntOrNull()
                        if (p != null && p in 1024..65535) {
                            onSaveSettings(settings.copy(httpPort = p))
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF66),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sing-box Core binary status
        Text(
            text = "SING-BOX CORE BINARY STATUS:",
            color = Color(0xFF8B949E),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                .background(Color(0xFF0D1117), RoundedCornerShape(4.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (detectedCorePath != null) "DETECTED & READY" else "NOT FOUND LOCALLY",
                    color = if (detectedCorePath != null) Color(0xFF00FF66) else Color(0xFFFFB703),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detectedCorePath ?: "Will auto-download on first connection",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .border(1.dp, Color(0xFF58A6FF), RoundedCornerShape(4.dp))
                    .clickable(enabled = !isDownloadingCore) {
                        scope.launch {
                            isDownloadingCore = true
                            try {
                                val bin = SingBoxCoreManager.downloadSingBox()
                                detectedCorePath = bin.absolutePath
                            } catch (_: Exception) {}
                            finally {
                                isDownloadingCore = false
                            }
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isDownloadingCore) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF58A6FF),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (detectedCorePath == null) "DOWNLOAD CORE" else "RE-DOWNLOAD CORE",
                        color = Color(0xFF58A6FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
