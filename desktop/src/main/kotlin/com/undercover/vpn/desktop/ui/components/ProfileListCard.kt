package com.undercover.vpn.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.model.ProfileType
import com.undercover.vpn.desktop.model.ProxyNode

@Composable
fun ProfileListCard(
    profile: DesktopProfile,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onSelectNode: (String) -> Unit,
    onPing: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = if (isSelected) Color(0xFF00FF66) else Color(0xFF30363D)
    val bgColor = if (isSelected) Color(0xFF131A18) else Color(0xFF161B22)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Title & Type Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Selection indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(2.dp, if (isSelected) Color(0xFF00FF66) else Color(0xFF8B949E), RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFF00FF66) else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { onSelect() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            // Type Badge
                            val typeLabel = when (profile.type) {
                                ProfileType.SINGLE_LINK -> "DIRECT NODE"
                                ProfileType.REMOTE_SUBSCRIPTION -> "SUBSCRIPTION (${profile.outbounds.size})"
                                ProfileType.CUSTOM_CONFIG -> "CUSTOM JSON"
                            }
                            Text(
                                text = typeLabel,
                                color = Color(0xFF58A6FF),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFF58A6FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (profile.selectedOutboundTag != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ACTIVE NODE: ${profile.selectedOutboundTag}",
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Latency Badge & Ping
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (profile.latencyMs != null) {
                        val pingColor = when {
                            profile.latencyMs < 100 -> Color(0xFF00FF66)
                            profile.latencyMs < 250 -> Color(0xFFFFB703)
                            else -> Color(0xFFFF3366)
                        }
                        Text(
                            text = "${profile.latencyMs} ms",
                            color = pingColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .border(1.dp, pingColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .background(pingColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Ping button
                    Text(
                        text = "PING",
                        color = Color(0xFF58A6FF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { onPing() }
                            .border(1.dp, Color(0xFF58A6FF).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // Expand nodes button (if subscription)
                    if (profile.outbounds.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (expanded) "HIDE NODES ▲" else "NODES ▼",
                            color = Color(0xFFC9D1D9),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Update button
                    if (profile.type == ProfileType.REMOTE_SUBSCRIPTION) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SYNC",
                            color = Color(0xFF3FB950),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onUpdate() }
                                .border(1.dp, Color(0xFF3FB950).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Delete button
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DEL",
                        color = Color(0xFFFF7B72),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { onDelete() }
                            .border(1.dp, Color(0xFFFF7B72).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Expandable node list
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(4.dp))
                        .background(Color(0xFF0D1117), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "AVAILABLE NODES IN THIS SUBSCRIPTION:",
                        color = Color(0xFF8B949E),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    profile.outbounds.forEach { node ->
                        val isNodeSelected = profile.selectedOutboundTag == node.tag
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectNode(node.tag) }
                                .background(if (isNodeSelected) Color(0xFF1F2A24) else Color.Transparent)
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isNodeSelected) "● " else "○ ",
                                    color = if (isNodeSelected) Color(0xFF00FF66) else Color(0xFF8B949E),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = node.tag,
                                    color = if (isNodeSelected) Color(0xFF00FF66) else Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = node.type,
                                color = Color(0xFF58A6FF),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
