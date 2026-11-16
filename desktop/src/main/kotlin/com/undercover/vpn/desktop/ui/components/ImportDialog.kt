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
import androidx.compose.ui.window.Dialog
import com.undercover.vpn.desktop.core.ProfileImporter
import com.undercover.vpn.desktop.model.DesktopProfile
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onProfileImported: (DesktopProfile) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(550.dp)
                .border(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "IMPORT PROFILE / SUBSCRIPTION",
                    color = Color(0xFF00FF66),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Supports Happ links, vless://, hysteria2://, subscription URLs, or raw sing-box JSON.",
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        errorMessage = null
                    },
                    placeholder = {
                        Text(
                            "Paste link here (happ://, vless://, https://...)",
                            color = Color(0xFF6E7681),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF66),
                        unfocusedBorderColor = Color(0xFF30363D),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF00FF66)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ERROR: $errorMessage",
                        color = Color(0xFFFF3366),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Paste from clipboard button
                    Text(
                        text = "PASTE CLIPBOARD",
                        color = Color(0xFF58A6FF),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    val data = clipboard.getData(DataFlavor.stringFlavor) as? String
                                    if (!data.isNullOrBlank()) {
                                        textInput = data
                                        errorMessage = null
                                    }
                                } catch (_: Exception) {}
                            }
                            .border(1.dp, Color(0xFF58A6FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    Row {
                        Text(
                            text = "CANCEL",
                            color = Color(0xFF8B949E),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .border(1.dp, Color(0xFF00FF66), RoundedCornerShape(4.dp))
                                .background(Color(0xFF00FF66).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .clickable(enabled = textInput.isNotBlank() && !isLoading) {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            val profile = ProfileImporter.importFromText(textInput)
                                            onProfileImported(profile)
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Import failed"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF00FF66),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "IMPORT",
                                    color = Color(0xFF00FF66),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
