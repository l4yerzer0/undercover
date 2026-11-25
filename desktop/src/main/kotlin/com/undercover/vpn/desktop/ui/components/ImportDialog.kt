package com.undercover.vpn.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.undercover.vpn.desktop.core.ProfileImporter
import com.undercover.vpn.desktop.model.DesktopProfile
import com.undercover.vpn.desktop.ui.DossierRed
import com.undercover.vpn.desktop.ui.RuStrings
import com.undercover.vpn.desktop.ui.Strings
import com.undercover.vpn.shared.ui.components.DossierButton
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onProfileImported: (DesktopProfile) -> Unit,
    strings: Strings = RuStrings
) {
    var textInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(600.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                .background(MaterialTheme.colorScheme.background)
                .padding(28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.importTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.importHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                            "https://... or happ://... or vless://...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ERROR: $errorMessage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DossierRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.pasteClipboard,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
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
                            .padding(8.dp)
                    )

                    Text(
                        text = strings.cancel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.sp,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    DossierButton(
                        text = strings.importBtn,
                        isOutlined = false,
                        onClick = {
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
                    )
                }
            }
        }
    }
}
