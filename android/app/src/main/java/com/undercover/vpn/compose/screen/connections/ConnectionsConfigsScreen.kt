package com.undercover.vpn.compose.screen.connections

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.undercover.vpn.R
import com.undercover.vpn.compose.component.qr.QRCodeDialog
import com.undercover.vpn.compose.component.qr.QRScanSheet
import com.undercover.vpn.compose.navigation.NewProfileArgs
import com.undercover.vpn.compose.navigation.ProfileRoutes
import com.undercover.vpn.compose.screen.configuration.ProfileImportHandler
import com.undercover.vpn.compose.screen.dashboard.DashboardViewModel
import com.undercover.vpn.compose.screen.qrscan.QRScanResult
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.compose.util.QRCodeGenerator
import com.undercover.vpn.compose.util.RelativeTimeFormatter
import com.undercover.vpn.database.Profile
import com.undercover.vpn.database.TypedProfile
import com.undercover.vpn.ktx.shareProfile
import com.undercover.vpn.ktx.shareProfileAsJson
import io.nekohasekai.libbox.Libbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsConfigsScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel? = null,
    onOpenNewProfile: (NewProfileArgs) -> Unit,
) {
    OverrideTopBar {
        // Dossier tactical UI replaces standard TopAppBar
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolvedViewModel: DashboardViewModel = dashboardViewModel ?: viewModel()
    val uiState by resolvedViewModel.uiState.collectAsState()
    val profiles = uiState.profiles
    val selectedProfileId = uiState.selectedProfileId
    val importHandler = remember { ProfileImportHandler(context) }

    var showAddSheet by remember { mutableStateOf(false) }
    var showQRScanSheet by remember { mutableStateOf(false) }
    var showQRCodeDialog by remember { mutableStateOf(false) }
    var qrCodeProfile by remember { mutableStateOf<Profile?>(null) }
    var profileToDelete by remember { mutableStateOf<Profile?>(null) }
    var profileToShare by remember { mutableStateOf<Profile?>(null) }

    val importFromFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    when (val parseResult = importHandler.parseUri(uri)) {
                        is ProfileImportHandler.UriParseResult.Success -> {
                            when (val result = importHandler.importFromUri(uri)) {
                                is ProfileImportHandler.ImportResult.Success -> {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, R.string.onboarding_profile_imported, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                is ProfileImportHandler.ImportResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        is ProfileImportHandler.UriParseResult.Error -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, parseResult.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))

                // Header Row (Matches Dashboard Dossier aesthetic)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "UNDERCOVER // CONNECTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = "CONFIGS (${profiles.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Title Block
                Text(
                    text = "ПОДКЛЮЧЕНИЯ",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Конфигурации и серверы доступа",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Add Profile Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                            .clickable { showAddSheet = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "[ ДОБАВИТЬ ]",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Update All Subscriptions (if any remote profiles exist)
                    val hasRemote = profiles.any { it.typed.type == TypedProfile.Type.Remote }
                    if (hasRemote) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            .clickable {
                                profiles.filter { it.typed.type == TypedProfile.Type.Remote }.forEach {
                                    resolvedViewModel.updateProfile(it)
                                }
                            }
                            .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[ ОБНОВИТЬ ВСЕ ]",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Section header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "СПИСОК КОНФИГУРАЦИЙ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "[ НЕТ СОХРАНЕННЫХ ПОДКЛЮЧЕНИЙ ]",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Импортируйте ссылку (vless, vmess, ss) или файл конфигурации.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "[ НАЖМИТЕ ЧТОБЫ ДОБАВИТЬ ]",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showAddSheet = true },
                            )
                        }
                    }
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    val isSelected = profile.id == selectedProfileId
                    val isUpdating = uiState.updatingProfileId == profile.id

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
                            )
                            .clickable { resolvedViewModel.selectProfile(profile.id) }
                            .padding(16.dp),
                    ) {
                        Column {
                            // Top Row: Selector bracket, Name, Type Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = if (isSelected) "[ X ]" else "[   ]",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                val isRemote = profile.typed.type == TypedProfile.Type.Remote
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isRemote) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = if (isRemote) "REMOTE" else "LOCAL",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isRemote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Subtitle Row: details & last updated
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = when (profile.typed.type) {
                                        TypedProfile.Type.Local -> "Локальный профиль"
                                        TypedProfile.Type.Remote -> "Обновлено: ${RelativeTimeFormatter.format(context, profile.typed.lastUpdated)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                )

                                if (isUpdating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (profile.typed.type == TypedProfile.Type.Remote) {
                                    Text(
                                        text = "[ ↻ ОБНОВИТЬ ]",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable { resolvedViewModel.updateProfile(profile) }
                                            .padding(4.dp),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Text(
                                    text = "[ ✎ ИЗМЕНИТЬ ]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .clickable { navController.navigate(ProfileRoutes.editProfile(profile.id)) }
                                        .padding(4.dp),
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = "[ ↗ ЭКСПОРТ ]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .clickable { profileToShare = profile }
                                        .padding(4.dp),
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = "[ ✕ ]",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .clickable { profileToDelete = profile }
                                        .padding(4.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Modal Bottom Sheet: Add Profile
    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(0.dp),
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                Text(
                    text = "ДОБАВЛЕНИЕ ПОДКЛЮЧЕНИЯ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ВЫБЕРИТЕ СПОСОБ ИМПОРТА",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Option 1: Clipboard
                DossierAddOption(
                    title = "Импорт из буфера обмена",
                    subtitle = "Поддерживает vless://, vmess://, ss://, ссылки подписок",
                    icon = Icons.Outlined.ContentPaste,
                    onClick = {
                        showAddSheet = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!text.isNullOrBlank()) {
                            scope.launch {
                                when (val parseResult = importHandler.parseQRCode(text)) {
                                    is ProfileImportHandler.QRCodeParseResult.RemoteProfile -> {
                                        withContext(Dispatchers.Main) {
                                            onOpenNewProfile(
                                                NewProfileArgs(
                                                    importName = parseResult.name,
                                                    importUrl = parseResult.url,
                                                ),
                                            )
                                        }
                                    }
                                    is ProfileImportHandler.QRCodeParseResult.LocalProfile -> {
                                        when (val result = importHandler.importFromQRCode(text)) {
                                            is ProfileImportHandler.ImportResult.Success -> {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, R.string.onboarding_profile_imported, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            is ProfileImportHandler.ImportResult.Error -> {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                    is ProfileImportHandler.QRCodeParseResult.Error -> {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, parseResult.message, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                        }
                    },
                )

                // Option 2: Scan QR
                DossierAddOption(
                    title = "Сканировать QR-код",
                    subtitle = "Используйте камеру устройства",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = {
                        showAddSheet = false
                        showQRScanSheet = true
                    },
                )

                // Option 3: Import from file
                DossierAddOption(
                    title = "Импорт из файла",
                    subtitle = "Файлы .json, .yaml, .txt, .conf",
                    icon = Icons.Outlined.FileUpload,
                    onClick = {
                        showAddSheet = false
                        importFromFileLauncher.launch("*/*")
                    },
                )

                // Option 4: Create manually
                DossierAddOption(
                    title = "Создать вручную",
                    subtitle = "Ручной ввод параметров конфигурации",
                    icon = Icons.Outlined.Create,
                    onClick = {
                        showAddSheet = false
                        onOpenNewProfile(NewProfileArgs())
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // QR Scan Sheet
    if (showQRScanSheet) {
        QRScanSheet(
            onDismiss = { showQRScanSheet = false },
            onScanResult = { result ->
                showQRScanSheet = false
                when (result) {
                    is QRScanResult.RemoteProfile -> {
                        scope.launch {
                            when (val parseResult = importHandler.parseQRCode(result.uri.toString())) {
                                is ProfileImportHandler.QRCodeParseResult.RemoteProfile -> {
                                    withContext(Dispatchers.Main) {
                                        onOpenNewProfile(
                                            NewProfileArgs(
                                                importName = parseResult.name,
                                                importUrl = parseResult.url,
                                            ),
                                        )
                                    }
                                }
                                is ProfileImportHandler.QRCodeParseResult.LocalProfile -> {
                                    when (val importResult = importHandler.importFromQRCode(result.uri.toString())) {
                                        is ProfileImportHandler.ImportResult.Success -> {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, R.string.onboarding_profile_imported, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        is ProfileImportHandler.ImportResult.Error -> {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, importResult.message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                                is ProfileImportHandler.QRCodeParseResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, parseResult.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            },
        )
    }

    // Share Options Dialog
    if (profileToShare != null) {
        val target = profileToShare!!
        AlertDialog(
            onDismissRequest = { profileToShare = null },
            shape = RoundedCornerShape(0.dp),
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            title = {
                Text(
                    text = "ЭКСПОРТ ПОДКЛЮЧЕНИЯ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            text = {
                Column {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "[ ПОДЕЛИТЬСЯ ФАЙЛОМ .BPF ]",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                profileToShare = null
                                scope.launch(Dispatchers.IO) {
                                    runCatching { context.shareProfile(target) }
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                    Text(
                        text = "[ ПОДЕЛИТЬСЯ JSON КОНФИГОМ ]",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                profileToShare = null
                                scope.launch(Dispatchers.IO) {
                                    runCatching { context.shareProfileAsJson(target) }
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                    if (target.typed.type == TypedProfile.Type.Remote) {
                        Text(
                            text = "[ ПОКАЗАТЬ QR-КОД ]",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    qrCodeProfile = target
                                    showQRCodeDialog = true
                                    profileToShare = null
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { profileToShare = null }) {
                    Text(
                        text = "[ ЗАКРЫТЬ ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            },
        )
    }

    // QR Code Dialog
    if (showQRCodeDialog && qrCodeProfile != null) {
        val target = qrCodeProfile!!
        val link = remember(target) {
            Libbox.generateRemoteProfileImportLink(target.name, target.typed.remoteURL)
        }
        val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
        val qrBitmap = QRCodeGenerator.rememberPrimaryBitmap(link, backgroundColor = surfaceColor)
        QRCodeDialog(
            bitmap = qrBitmap,
            onDismiss = {
                showQRCodeDialog = false
                qrCodeProfile = null
            },
        )
    }

    // Delete Confirmation Dialog
    if (profileToDelete != null) {
        val target = profileToDelete!!
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            shape = RoundedCornerShape(0.dp),
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            title = {
                Text(
                    text = "УДАЛЕНИЕ ПОДКЛЮЧЕНИЯ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(
                    text = "Вы уверены, что хотите удалить конфигурацию «${target.name}»?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    resolvedViewModel.deleteProfile(target)
                    profileToDelete = null
                }) {
                    Text(
                        text = "[ УДАЛИТЬ ]",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { profileToDelete = null }) {
                    Text(
                        text = "[ ОТМЕНА ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            },
        )
    }
}

@Composable
private fun DossierAddOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Text(
            text = "→",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
    )
}
