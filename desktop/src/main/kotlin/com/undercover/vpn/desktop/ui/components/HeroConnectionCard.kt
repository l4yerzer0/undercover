package com.undercover.vpn.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.model.ProxyMode
import com.undercover.vpn.desktop.model.VpnState
import kotlinx.coroutines.delay

@Composable
fun HeroConnectionCard(
    vpnState: VpnState,
    selectedProfileName: String?,
    proxyMode: ProxyMode,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting
    val isError = vpnState is VpnState.Error

    val statusColor by animateColorAsState(
        when {
            isConnected -> Color(0xFF00FF66) // Dossier Emerald Neon
            isConnecting -> Color(0xFFFFB703) // Amber
            isError -> Color(0xFFFF3366) // Warning Red
            else -> Color(0xFF6C757D) // Dormant Grey
        },
        label = "statusColor"
    )

    // Pulse animation when connecting or connected
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Timer when connected
    var durationSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vpnState) {
        if (vpnState is VpnState.Connected) {
            val start = vpnState.connectedSince
            while (true) {
                durationSeconds = (System.currentTimeMillis() - start) / 1000
                delay(1000)
            }
        } else {
            durationSeconds = 0L
        }
    }

    val formattedDuration = remember(durationSeconds) {
        val h = durationSeconds / 3600
        val m = (durationSeconds % 3600) / 60
        val s = durationSeconds % 60
        if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = if (isConnected) 0.6f else 0.3f), RoundedCornerShape(8.dp))
            .background(Color(0xFF0F1416), RoundedCornerShape(8.dp))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Status pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(statusColor.copy(alpha = if (isConnecting || isConnected) pulseAlpha else 1f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (vpnState) {
                        is VpnState.Connected -> "STATUS: PROTECTED & ENCRYPTED"
                        is VpnState.Connecting -> "STATUS: ESTABLISHING SECURE TUNNEL..."
                        is VpnState.Disconnecting -> "STATUS: DISCONNECTING..."
                        is VpnState.Error -> "STATUS: ERROR - ${vpnState.message.take(30)}"
                        is VpnState.Disconnected -> "STATUS: UNPROTECTED"
                    }.uppercase(),
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Big Action Button / Switch
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 240.dp, height = 70.dp)
                    .border(
                        width = 2.dp,
                        color = if (isConnected) statusColor else statusColor.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(
                        if (isConnected) statusColor.copy(alpha = 0.15f) else Color(0xFF161B1E),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable(enabled = !isConnecting) { onToggleConnection() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            isConnecting -> "CONNECTING..."
                            isConnected -> "DISCONNECT"
                            else -> "CONNECT"
                        },
                        color = if (isConnected) statusColor else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Active Server
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ACTIVE TARGET",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (if (vpnState is VpnState.Connected) vpnState.nodeName else selectedProfileName) ?: "None Selected",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                // Mode
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ROUTING MODE",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (proxyMode) {
                            ProxyMode.SYSTEM_PROXY -> "SYSTEM PROXY (SOCKS/HTTP)"
                            ProxyMode.TUN_VPN -> "TUN VIRTUAL ADAPTER"
                        },
                        color = Color(0xFF58A6FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Uptime
                if (isConnected) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "DURATION",
                            color = Color(0xFF8B949E),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formattedDuration,
                            color = Color(0xFF00FF66),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
