package com.undercover.vpn.compose.component.qr

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.undercover.vpn.R
import com.undercover.vpn.compose.screen.qrscan.QRCodeCropArea
import com.undercover.vpn.compose.screen.qrscan.QRScanResult
import com.undercover.vpn.compose.screen.qrscan.QRScanViewModel
import com.undercover.vpn.compose.util.QRCodeDecoder
import com.undercover.vpn.shared.ui.components.DossierButton
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun QRScanSheet(
    onDismiss: () -> Unit,
    onScanResult: (QRScanResult) -> Unit,
    viewModel: QRScanViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var isDecodingImage by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            isDecodingImage = true
            coroutineScope.launch {
                val decoded = QRCodeDecoder.decodeFromUri(context, uri)
                isDecodingImage = false
                if (!decoded.isNullOrBlank()) {
                    onScanResult(QRScanResult.RemoteProfile(android.net.Uri.parse(decoded)))
                } else {
                    Toast.makeText(
                        context,
                        R.string.error_no_qr_found,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            hasPermission = true
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetQRSState()
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.result) {
        uiState.result?.let { result ->
            viewModel.clearResult()
            onScanResult(result)
        }
    }

    // Laser scan sweep animation
    val infiniteTransition = rememberInfiniteTransition()
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Camera Preview View
            if (hasPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    lifecycleOwner = lifecycleOwner,
                    cropArea = uiState.cropArea,
                )
            }

            // Tactical Viewfinder HUD Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                val boxSize = minOf(size.width * 0.74f, 290.dp.toPx())
                val left = (size.width - boxSize) / 2f
                val top = (size.height - boxSize) / 2f - 40.dp.toPx()
                val right = left + boxSize
                val bottom = top + boxSize
                val armLength = 28.dp.toPx()
                val strokeWidth = 3.dp.toPx()

                // 1. Dim the area outside the viewfinder square
                drawRect(
                    color = Color.Black.copy(alpha = 0.65f),
                    topLeft = Offset.Zero,
                    size = Size(size.width, top),
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.65f),
                    topLeft = Offset(0f, bottom),
                    size = Size(size.width, size.height - bottom),
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.65f),
                    topLeft = Offset(0f, top),
                    size = Size(left, boxSize),
                )
                drawRect(
                    color = Color.Black.copy(alpha = 0.65f),
                    topLeft = Offset(right, top),
                    size = Size(size.width - right, boxSize),
                )

                // 2. Tactical Corner Brackets [ ]
                // Top-Left
                drawLine(primaryColor, Offset(left, top), Offset(left + armLength, top), strokeWidth)
                drawLine(primaryColor, Offset(left, top), Offset(left, top + armLength), strokeWidth)

                // Top-Right
                drawLine(primaryColor, Offset(right, top), Offset(right - armLength, top), strokeWidth)
                drawLine(primaryColor, Offset(right, top), Offset(right, top + armLength), strokeWidth)

                // Bottom-Left
                drawLine(primaryColor, Offset(left, bottom), Offset(left + armLength, bottom), strokeWidth)
                drawLine(primaryColor, Offset(left, bottom), Offset(left, bottom - armLength), strokeWidth)

                // Bottom-Right
                drawLine(primaryColor, Offset(right, bottom), Offset(right - armLength, bottom), strokeWidth)
                drawLine(primaryColor, Offset(right, bottom), Offset(right, bottom - armLength), strokeWidth)

                // 3. Subtle Center Crosshair (+)
                val centerX = left + boxSize / 2f
                val centerY = top + boxSize / 2f
                val crosshairLength = 12.dp.toPx()
                drawLine(
                    primaryColor.copy(alpha = 0.35f),
                    Offset(centerX - crosshairLength, centerY),
                    Offset(centerX + crosshairLength, centerY),
                    1.5.dp.toPx(),
                )
                drawLine(
                    primaryColor.copy(alpha = 0.35f),
                    Offset(centerX, centerY - crosshairLength),
                    Offset(centerX, centerY + crosshairLength),
                    1.5.dp.toPx(),
                )

                // 4. Animated Scanning Laser Line
                val laserY = top + boxSize * laserProgress
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.9f),
                            Color.Transparent,
                        ),
                        startX = left,
                        endX = right,
                    ),
                    start = Offset(left, laserY),
                    end = Offset(right, laserY),
                    strokeWidth = 2.dp.toPx(),
                )

                // 5. Smart Crop Area tracking (if active)
                uiState.cropArea?.let { crop ->
                    val rect = mapCropAreaToPreview(crop, size.width, size.height)
                    if (rect != null) {
                        drawRect(
                            color = primaryColor.copy(alpha = 0.85f),
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }

            // Top Tactical HUD Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tactical Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(0.dp))
                        .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(0.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = primaryColor,
                    )
                }

                // Center Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "UNDERCOVER // SCANNER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.5.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = primaryColor,
                    )
                    Text(
                        text = stringResource(R.string.profile_add_scan_qr_code),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                }

                // Balance spacer on right so title remains centered
                Spacer(modifier = Modifier.size(44.dp))
            }

            // Decoding image overlay
            if (isDecodingImage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryColor)
                        Spacer(modifier = Modifier.padding(top = 16.dp))
                        Text(
                            text = "РАСПОЗНАВАНИЕ QR-КОДА...",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = primaryColor,
                        )
                    }
                }
            }

            // QRS Multi-frame mode indicator
            if (uiState.qrsMode && uiState.qrsProgress != null) {
                val (decoded, total) = uiState.qrsProgress!!
                val progress = if (total > 0) decoded.toFloat() / total.toFloat() / 1.2f else 0f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(96.dp),
                            color = primaryColor,
                            strokeWidth = 6.dp,
                            trackColor = primaryColor.copy(alpha = 0.25f),
                        )
                        if (total > 0) {
                            Text(
                                text = "${minOf(99, (progress * 100).toInt())}%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White,
                            )
                        }
                        Text(
                            text = "QRS SYNC",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            ),
                            color = primaryColor,
                            modifier = Modifier.offset(y = (-88).dp),
                        )
                    }
                }
            }

            // Bottom Area: Controls dock, Viewfinder Instruction & Cancel Button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Tactical Action Controls Dock: Gallery, Flashlight, Camera Switch
                Row(
                    modifier = Modifier.padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Gallery Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(0.dp))
                                .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(0.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = stringResource(R.string.import_from_gallery),
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            text = "ГАЛЕРЕЯ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = primaryColor.copy(alpha = 0.8f),
                        )
                    }

                    // Flashlight (Torch) Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleTorch() },
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (uiState.torchEnabled) primaryColor else Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(0.dp),
                                )
                                .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(0.dp)),
                        ) {
                            Icon(
                                imageVector = if (uiState.torchEnabled) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = stringResource(R.string.profile_add_scan_enable_torch),
                                tint = if (uiState.torchEnabled) MaterialTheme.colorScheme.onPrimary else primaryColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            text = if (uiState.torchEnabled) "ВКЛ" else "ФОНАРИК",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = if (uiState.torchEnabled) primaryColor else primaryColor.copy(alpha = 0.8f),
                        )
                    }

                    // Camera Switch (Flip) Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleFrontCamera(lifecycleOwner) },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(0.dp))
                                .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(0.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = stringResource(R.string.profile_add_scan_use_front_camera),
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            text = "КАМЕРА",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                            ),
                            color = primaryColor.copy(alpha = 0.8f),
                        )
                    }
                }

                Text(
                    text = "ПОМЕСТИТЕ QR-КОД В РАМКУ СКАНЕРА",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = primaryColor.copy(alpha = 0.9f),
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                DossierButton(
                    text = stringResource(R.string.cancel),
                    isOutlined = true,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    viewModel: QRScanViewModel,
    lifecycleOwner: LifecycleOwner,
    cropArea: QRCodeCropArea?,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(previewView) {
        previewView?.let { view ->
            view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            viewModel.startCamera(lifecycleOwner, view)
        }
        onDispose { }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this

                    previewStreamState.observe(lifecycleOwner) { state ->
                        if (state == PreviewView.StreamState.STREAMING) {
                            viewModel.onPreviewStreamStateChanged(true)
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        }
                    }
                }
            },
        )
    }
}

private fun mapCropAreaToPreview(area: QRCodeCropArea, viewWidth: Float, viewHeight: Float): Rect? {
    if (viewWidth <= 0f || viewHeight <= 0f) return null

    val rotation = ((area.rotationDegrees % 360) + 360) % 360
    var rotLeft = area.left.toFloat()
    var rotTop = area.top.toFloat()
    var rotRight = area.right.toFloat()
    var rotBottom = area.bottom.toFloat()
    var imageWidth = area.imageWidth.toFloat()
    var imageHeight = area.imageHeight.toFloat()
    when (rotation) {
        90 -> {
            rotLeft = (area.imageHeight - area.bottom).toFloat()
            rotTop = area.left.toFloat()
            rotRight = (area.imageHeight - area.top).toFloat()
            rotBottom = area.right.toFloat()
            imageWidth = area.imageHeight.toFloat()
            imageHeight = area.imageWidth.toFloat()
        }
        180 -> {
            rotLeft = (area.imageWidth - area.right).toFloat()
            rotTop = (area.imageHeight - area.bottom).toFloat()
            rotRight = (area.imageWidth - area.left).toFloat()
            rotBottom = (area.imageHeight - area.top).toFloat()
        }
        270 -> {
            rotLeft = area.top.toFloat()
            rotTop = (area.imageWidth - area.right).toFloat()
            rotRight = area.bottom.toFloat()
            rotBottom = (area.imageWidth - area.left).toFloat()
            imageWidth = area.imageHeight.toFloat()
            imageHeight = area.imageWidth.toFloat()
        }
    }

    if (imageWidth <= 0f || imageHeight <= 0f) return null

    val scale = max(viewWidth / imageWidth, viewHeight / imageHeight)
    val dx = (viewWidth - imageWidth * scale) / 2f
    val dy = (viewHeight - imageHeight * scale) / 2f

    val left = rotLeft * scale + dx
    val top = rotTop * scale + dy
    val right = rotRight * scale + dx
    val bottom = rotBottom * scale + dy

    val clampedLeft = left.coerceIn(0f, viewWidth)
    val clampedTop = top.coerceIn(0f, viewHeight)
    val clampedRight = right.coerceIn(0f, viewWidth)
    val clampedBottom = bottom.coerceIn(0f, viewHeight)

    if (clampedRight - clampedLeft < 4f || clampedBottom - clampedTop < 4f) return null

    return Rect(clampedLeft, clampedTop, clampedRight, clampedBottom)
}
