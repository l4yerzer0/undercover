package com.undercover.vpn.compose.screen.onboarding

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
    val pagerState = rememberPagerState(pageCount = { 5 })
    val importHandler = remember { ProfileImportHandler(context) }
    var showQRScanSheet by remember { mutableStateOf(false) }
    val isProMode by com.undercover.vpn.database.AppModeState.isProFlow.collectAsState()

    // Permission tracking states
    var isVpnGranted by remember { mutableStateOf(VpnService.prepare(context) == null) }
    var isNotifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isCameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isBatteryIgnored by remember {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        mutableStateOf(pm?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    // Permission Launchers
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isVpnGranted = VpnService.prepare(context) == null
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isNotifGranted = granted
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        isCameraGranted = granted
    }

    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryIgnored = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Bar: Branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "UNDERCOVER",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Pager Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> {
                    // Screen 1: Intro / Redacted
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_undercover_logo_light),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 32.dp),
                        )

                        Text(
                            text = stringResource(R.string.onboarding_slide1_text),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 38.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                1 -> {
                    // Screen 2: "Полностью открытый код. Ноль телеметрии. Безопасность"
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_manifest_title),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.onboarding_slide2_text),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                lineHeight = 30.sp,
                                letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Card 1: Open Source
                        OnboardingFeatureCard(
                            tag = "[ 100% OPEN SOURCE ]",
                            title = stringResource(R.string.onboarding_feat_opensource_title),
                            description = stringResource(R.string.onboarding_feat_opensource_desc),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Card 2: Zero Telemetry
                        OnboardingFeatureCard(
                            tag = "[ ZERO TELEMETRY ]",
                            title = stringResource(R.string.onboarding_feat_telemetry_title),
                            description = stringResource(R.string.onboarding_feat_telemetry_desc),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Card 3: Security & Encryption
                        OnboardingFeatureCard(
                            tag = "[ CRYPTO SECURITY ]",
                            title = stringResource(R.string.onboarding_feat_security_title),
                            description = stringResource(R.string.onboarding_feat_security_desc),
                        )
                    }
                }

                2 -> {
                    // Screen 3: Permissions Explanation & Granting
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.onboarding_permissions_tag),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.onboarding_permissions_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = stringResource(R.string.onboarding_permissions_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 1. VPN Service Permission
                        OnboardingPermissionCard(
                            icon = Icons.Default.VpnKey,
                            title = stringResource(R.string.onboarding_perm_vpn_title),
                            description = stringResource(R.string.onboarding_perm_vpn_desc),
                            isGranted = isVpnGranted,
                            onGrantClick = {
                                val prepareIntent = VpnService.prepare(context)
                                if (prepareIntent != null) {
                                    vpnLauncher.launch(prepareIntent)
                                } else {
                                    isVpnGranted = true
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Notifications Permission
                        OnboardingPermissionCard(
                            icon = Icons.Default.Notifications,
                            title = stringResource(R.string.onboarding_perm_notif_title),
                            description = stringResource(R.string.onboarding_perm_notif_desc),
                            isGranted = isNotifGranted,
                            onGrantClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    isNotifGranted = true
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Camera Permission
                        OnboardingPermissionCard(
                            icon = Icons.Default.CameraAlt,
                            title = stringResource(R.string.onboarding_perm_camera_title),
                            description = stringResource(R.string.onboarding_perm_camera_desc),
                            isGranted = isCameraGranted,
                            onGrantClick = {
                                cameraLauncher.launch(Manifest.permission.CAMERA)
                            },
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. Background / Battery Optimization
                        OnboardingPermissionCard(
                            icon = Icons.Default.Power,
                            title = stringResource(R.string.onboarding_perm_battery_title),
                            description = stringResource(R.string.onboarding_perm_battery_desc),
                            isGranted = isBatteryIgnored,
                            onGrantClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    batteryLauncher.launch(intent)
                                } catch (_: Exception) {
                                    // Fallback to standard battery settings
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        batteryLauncher.launch(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                3 -> {
                    // Screen 4: Mode Selection Screen (Simple vs PRO)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_mode_title),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.onboarding_mode_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(28.dp))

                        // Simple Mode Card
                        OnboardingModeCard(
                            selected = !isProMode,
                            title = stringResource(R.string.mode_simple_title),
                            subtitle = stringResource(R.string.mode_simple_subtitle),
                            description = stringResource(R.string.mode_simple_desc),
                            onClick = { com.undercover.vpn.database.AppModeState.setProMode(false) },
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // PRO Mode Card
                        OnboardingModeCard(
                            selected = isProMode,
                            title = stringResource(R.string.mode_pro_title),
                            subtitle = stringResource(R.string.mode_pro_subtitle),
                            description = stringResource(R.string.mode_pro_desc),
                            onClick = { com.undercover.vpn.database.AppModeState.setProMode(true) },
                        )
                    }
                }

                4 -> {
                    // Screen 5: Empty Connection Screen (Add First Connection)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_undercover_logo_light),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .size(112.dp)
                                .padding(bottom = 24.dp),
                        )

                        Text(
                            text = stringResource(R.string.onboarding_empty_title),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.onboarding_empty_desc),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Bottom Controls
        if (pagerState.currentPage < 4) {
            // Page Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(5) { iteration ->
                    val color = if (pagerState.currentPage == iteration)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(8.dp),
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
                    .padding(bottom = 12.dp),
            )
        } else {
            // Screen 5: Action Buttons for adding first connection
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DossierButton(
                    text = stringResource(R.string.onboarding_scan_qr),
                    isOutlined = false,
                    icon = Icons.Default.QrCodeScanner,
                    onClick = { showQRScanSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                DossierButton(
                    text = stringResource(R.string.onboarding_import_clipboard),
                    isOutlined = true,
                    icon = Icons.Default.ContentPaste,
                    onClick = { importClipboard() },
                    modifier = Modifier.fillMaxWidth(),
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
            },
        )
    }
}

@Composable
private fun OnboardingFeatureCard(
    tag: String,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(0.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            ),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun OnboardingPermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                RoundedCornerShape(0.dp),
            )
            .background(
                if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                else MaterialTheme.colorScheme.surface,
            )
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_perm_granted),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onGrantClick)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_perm_grant),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun OnboardingModeCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    description: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            )
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                ),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (selected) "[ X ]" else "[   ]",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "«$subtitle»",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
    }
}
