package com.undercover.vpn.compose.screen.onboarding

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.ColorFilter
import com.undercover.vpn.shared.ui.theme.DossierCream
import com.undercover.vpn.shared.ui.theme.DossierInk
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undercover.vpn.R
import com.undercover.vpn.compose.component.qr.QRScanSheet
import com.undercover.vpn.compose.screen.configuration.ProfileImportHandler
import com.undercover.vpn.compose.screen.qrscan.QRScanResult
import com.undercover.vpn.shared.ui.components.DossierButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val importHandler = remember { ProfileImportHandler(context) }
    var showQRScanSheet by remember { mutableStateOf(false) }

    fun importClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()?.trim()
        if (!clipText.isNullOrBlank()) {
            coroutineScope.launch {
                val result = importHandler.importFromQRCode(clipText)
                withContext(Dispatchers.Main) {
                    when (result) {
                        is ProfileImportHandler.ImportResult.Success -> {
                            Toast.makeText(context, R.string.onboarding_profile_imported, Toast.LENGTH_SHORT).show()
                            onFinish()
                        }
                        is ProfileImportHandler.ImportResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(context, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Bar: Branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "UNDERCOVER",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0, 1, 2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val isDark = isSystemInDarkTheme()
                        Image(
                            painter = painterResource(if (isDark) R.drawable.ic_undercover_logo_light else R.drawable.ic_undercover_logo),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(if (isDark) DossierCream else DossierInk),
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 32.dp)
                        )

                        val text = when (page) {
                            0 -> stringResource(R.string.onboarding_slide1_text)
                            1 -> stringResource(R.string.onboarding_slide2_text)
                            else -> stringResource(R.string.onboarding_slide3_text)
                        }

                        Text(
                            text = text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 38.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                3 -> {
                    // Empty Connection Screen (Add First Connection)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val isDark = isSystemInDarkTheme()
                        Image(
                            painter = painterResource(if (isDark) R.drawable.ic_undercover_logo_light else R.drawable.ic_undercover_logo),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(if (isDark) DossierCream else DossierInk),
                            modifier = Modifier
                                .size(112.dp)
                                .padding(bottom = 24.dp)
                        )

                        Text(
                            text = stringResource(R.string.onboarding_empty_title),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.onboarding_empty_desc),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Bottom Controls
        if (pagerState.currentPage < 3) {
            // Page Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { iteration ->
                    val color = if (pagerState.currentPage == iteration)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(8.dp)
                    )
                }
            }

            DossierButton(
                text = stringResource(R.string.next),
                isOutlined = false,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        } else {
            // Screen 3: Action Buttons for adding first connection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DossierButton(
                    text = stringResource(R.string.onboarding_scan_qr),
                    isOutlined = false,
                    icon = Icons.Default.QrCodeScanner,
                    onClick = { showQRScanSheet = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                DossierButton(
                    text = stringResource(R.string.onboarding_import_clipboard),
                    isOutlined = true,
                    icon = Icons.Default.ContentPaste,
                    onClick = { importClipboard() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // QR Scan Sheet dialog
    if (showQRScanSheet) {
        QRScanSheet(
            onDismiss = { showQRScanSheet = false },
            onScanResult = { scanResult ->
                showQRScanSheet = false
                coroutineScope.launch {
                    val result = when (scanResult) {
                        is QRScanResult.QRSData -> importHandler.importFromQRSData(scanResult.data)
                        is QRScanResult.RemoteProfile -> importHandler.importFromQRCode(scanResult.uri.toString())
                    }
                    withContext(Dispatchers.Main) {
                        when (result) {
                            is ProfileImportHandler.ImportResult.Success -> {
                                Toast.makeText(context, R.string.onboarding_profile_imported, Toast.LENGTH_SHORT).show()
                                onFinish()
                            }
                            is ProfileImportHandler.ImportResult.Error -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }
}
