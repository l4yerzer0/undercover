package com.undercover.vpn.compose.screen.dashboard

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import com.undercover.vpn.shared.ui.components.DossierRadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.ProfileContent
import com.undercover.vpn.R
import com.undercover.vpn.compose.component.qr.QRCodeDialog
import com.undercover.vpn.compose.component.qr.QRSDialog
import com.undercover.vpn.compose.component.qr.QRScanSheet
import com.undercover.vpn.compose.navigation.NewProfileArgs
import com.undercover.vpn.compose.screen.configuration.ProfileImportHandler
import com.undercover.vpn.compose.screen.qrscan.QRScanResult
import com.undercover.vpn.compose.util.QRCodeGenerator
import com.undercover.vpn.compose.util.RelativeTimeFormatter
import com.undercover.vpn.database.Profile
import com.undercover.vpn.database.TypedProfile
import com.undercover.vpn.ktx.errorDialogBuilder
import com.undercover.vpn.ktx.shareProfile
import com.undercover.vpn.ktx.shareProfileAsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesCard(
    profiles: List<Profile>,
    selectedProfileId: Long,
    isLoading: Boolean,
    showAddProfileSheet: Boolean,
    showProfilePickerSheet: Boolean,
    updatingProfileId: Long? = null,
    updatedProfileId: Long? = null,
    onProfileSelected: (Long) -> Unit,
    onProfileEdit: (Profile) -> Unit,
    onProfileDelete: (Profile) -> Unit,
    onProfileShare: (Profile) -> Unit,
    onProfileShareURL: (Profile) -> Unit,
    onProfileUpdate: (Profile) -> Unit,
    onProfileMove: (Int, Int) -> Unit,
    onShowAddProfileSheet: () -> Unit,
    onHideAddProfileSheet: () -> Unit,
    onShowProfilePickerSheet: () -> Unit,
    onHideProfilePickerSheet: () -> Unit,
    onOpenNewProfile: (NewProfileArgs) -> Unit,
    onProfileOpen: (Profile) -> Unit = {},
    onOpenSetupRouting: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val importHandler = remember { ProfileImportHandler(context) }

    var showQRCodeDialog by remember { mutableStateOf(false) }
    var qrCodeProfile by remember { mutableStateOf<Profile?>(null) }

    var showQRSDialog by remember { mutableStateOf(false) }
    var qrsProfile by remember { mutableStateOf<Profile?>(null) }
    var qrsProfileData by remember { mutableStateOf<ByteArray?>(null) }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }
    var pendingQrsData by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    var showQRScanSheet by remember { mutableStateOf(false) }

    val importFromFileLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            uri?.let {
                coroutineScope.launch {
                    when (val parseResult = importHandler.parseUri(uri)) {
                        is ProfileImportHandler.UriParseResult.Success -> {
                            withContext(Dispatchers.Main) {
                                pendingImportName = parseResult.name
                                pendingImportUri = uri
                                showImportConfirmDialog = true
                            }
                        }
                        is ProfileImportHandler.UriParseResult.Error -> {
                            withContext(Dispatchers.Main) {
                                context.errorDialogBuilder(Exception(parseResult.message)).show()
                            }
                        }
                    }
                }
            }
        }

    // The save dialogs are launched from a row, but their result arrives later and
    // out of that scope, so remember which profile asked.
    var pendingFileProfile by remember { mutableStateOf<Profile?>(null) }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            val selectedProfile = pendingFileProfile ?: profiles.find { it.id == selectedProfileId }
            if (selectedProfile != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val profileData = createProfileContent(selectedProfile)
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(profileData)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.success_profile_saved),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "${context.getString(R.string.failed_save_profile)}: ${e.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    val saveJsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            val selectedProfile = pendingFileProfile ?: profiles.find { it.id == selectedProfileId }
            if (selectedProfile != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val jsonContent = File(selectedProfile.typed.path).readText()
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonContent.toByteArray())
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.success_profile_saved),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "${context.getString(R.string.failed_save_profile)}: ${e.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    val selectedProfile = profiles.find { it.id == selectedProfileId }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.title_configuration),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    onClick = onShowAddProfileSheet,
                    shape = RoundedCornerShape(0.dp),
                    color = if (isSystemInDarkTheme()) {
                        lerp(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            0.5f,
                        )
                    } else {
                        MaterialTheme.colorScheme.surfaceDim
                    },
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_profile),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_profiles),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    profiles.forEach { profile ->
                        ProfileRow(
                            profile = profile,
                            selected = profile.id == selectedProfileId,
                            onSelect = { onProfileSelected(profile.id) },
                            onOpen = { onProfileOpen(profile) },
                            onShareFile = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        context.shareProfile(profile)
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            context.errorDialogBuilder(e).show()
                                        }
                                    }
                                }
                            },
                            onSaveFile = {
                                pendingFileProfile = profile
                                saveFileLauncher.launch("${profile.name}.bpf")
                            },
                            onSaveJson = {
                                pendingFileProfile = profile
                                saveJsonFileLauncher.launch("${profile.name}.json")
                            },
                            onShareJson = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        context.shareProfileAsJson(profile)
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            context.errorDialogBuilder(e).show()
                                        }
                                    }
                                }
                            },
                            onShareURL = {
                                qrCodeProfile = profile
                                showQRCodeDialog = true
                            },
                            onShareQRS = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val data = createProfileContent(profile)
                                        withContext(Dispatchers.Main) {
                                            qrsProfile = profile
                                            qrsProfileData = data
                                            showQRSDialog = true
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            context.errorDialogBuilder(e).show()
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showProfilePickerSheet) {
        ProfilePickerSheet(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            onProfileSelected = { profile -> onProfileSelected(profile.id) },
            onProfileEdit = onProfileEdit,
            onProfileDelete = onProfileDelete,
            onProfileMove = onProfileMove,
            onDismiss = onHideProfilePickerSheet,
        )
    }

    var showAddByLinkDialog by remember { mutableStateOf(false) }
    var linkInputText by remember { mutableStateOf("") }

    if (showAddProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = onHideAddProfileSheet,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.add_profile),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )

                ListItem(
                    modifier = Modifier.clickable {
                        onHideAddProfileSheet()
                        showAddByLinkDialog = true
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text("По ссылке")
                    },
                    supportingContent = {
                        Text("Импорт подписки или конфигурации по URL")
                    },
                )

                ListItem(
                    modifier = Modifier.clickable {
                        onHideAddProfileSheet()
                        showQRScanSheet = true
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(stringResource(R.string.profile_add_scan_qr_code))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.scan_qr_code_description))
                    },
                )
            }
        }
    }

    if (showAddByLinkDialog) {
        LaunchedEffect(Unit) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!clip.isNullOrBlank() && (clip.startsWith("http://") || clip.startsWith("https://") ||
                clip.startsWith("sing-box://") || clip.startsWith("v2raytun://") || clip.startsWith("happ://") ||
                clip.startsWith("vless://") || clip.startsWith("vmess://") || clip.startsWith("ss://") ||
                clip.startsWith("trojan://") || clip.startsWith("hy2://"))) {
                linkInputText = clip
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAddByLinkDialog = false
                linkInputText = ""
            },
            shape = RoundedCornerShape(0.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "ДОБАВИТЬ ПО ССЫЛКЕ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Вставьте ссылку на подписку или конфигурацию:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    OutlinedTextField(
                        value = linkInputText,
                        onValueChange = { linkInputText = it },
                        placeholder = { Text("https://...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(0.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clip.isNullOrBlank()) {
                                    linkInputText = clip
                                } else {
                                    Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(0.dp),
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Вставить из буфера", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = linkInputText.trim()
                        if (input.isNotBlank()) {
                            showAddByLinkDialog = false
                            linkInputText = ""
                            coroutineScope.launch {
                                when (val parseResult = importHandler.parseQRCode(input)) {
                                    is ProfileImportHandler.QRCodeParseResult.RemoteProfile -> {
                                        withContext(Dispatchers.Main) {
                                            onOpenNewProfile(
                                                NewProfileArgs(
                                                    importName = parseResult.name,
                                                    importUrl = parseResult.url,
                                                )
                                            )
                                        }
                                    }
                                    is ProfileImportHandler.QRCodeParseResult.LocalProfile -> {
                                        when (val result = importHandler.importFromQRCode(input)) {
                                            is ProfileImportHandler.ImportResult.Success -> {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, R.string.onboarding_profile_imported, Toast.LENGTH_SHORT).show()
                                                    if (result.needsRoutingSetup) {
                                                        onOpenSetupRouting()
                                                    }
                                                }
                                            }
                                            is ProfileImportHandler.ImportResult.Error -> {
                                                withContext(Dispatchers.Main) {
                                                    context.errorDialogBuilder(Exception(result.message)).show()
                                                }
                                            }
                                        }
                                    }
                                    is ProfileImportHandler.QRCodeParseResult.Error -> {
                                        withContext(Dispatchers.Main) {
                                            context.errorDialogBuilder(Exception(parseResult.message)).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Text(
                        text = "ПРОДОЛЖИТЬ",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddByLinkDialog = false
                        linkInputText = ""
                    },
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Text(
                        text = "ОТМЕНА",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            },
        )
    }

    if (showQRCodeDialog && qrCodeProfile != null) {
        val profile = qrCodeProfile!!
        val link = remember(profile) {
            Libbox.generateRemoteProfileImportLink(
                profile.name,
                profile.typed.remoteURL,
            )
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

    if (showQRSDialog && qrsProfile != null && qrsProfileData != null) {
        QRSDialog(
            profileData = qrsProfileData!!,
            profileName = qrsProfile!!.name,
            onDismiss = {
                showQRSDialog = false
                qrsProfile = null
                qrsProfileData = null
            },
        )
    }

    if (showImportConfirmDialog && pendingImportName != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportName = null
                pendingQrsData = null
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.import_profile_confirm_title)) },
            text = { Text(stringResource(R.string.import_profile_confirm_message, pendingImportName!!)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        val qrsData = pendingQrsData
                        val importUri = pendingImportUri
                        pendingImportName = null
                        pendingQrsData = null
                        pendingImportUri = null
                        coroutineScope.launch {
                            if (qrsData != null) {
                                when (val result = importHandler.importFromQRSData(qrsData)) {
                                    is ProfileImportHandler.ImportResult.Success -> {
                                        withContext(Dispatchers.Main) {
                                            if (result.needsRoutingSetup) {
                                                onOpenSetupRouting()
                                            } else {
                                                onProfileEdit(result.profile)
                                            }
                                        }
                                    }
                                    is ProfileImportHandler.ImportResult.Error -> {
                                        withContext(Dispatchers.Main) {
                                            context.errorDialogBuilder(Exception(result.message)).show()
                                        }
                                    }
                                }
                            } else if (importUri != null) {
                                when (val result = importHandler.importFromUri(importUri)) {
                                    is ProfileImportHandler.ImportResult.Success -> {
                                        withContext(Dispatchers.Main) {
                                            if (result.needsRoutingSetup) {
                                                onOpenSetupRouting()
                                            } else {
                                                onProfileEdit(result.profile)
                                            }
                                        }
                                    }
                                    is ProfileImportHandler.ImportResult.Error -> {
                                        withContext(Dispatchers.Main) {
                                            context.errorDialogBuilder(Exception(result.message)).show()
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        pendingImportName = null
                        pendingQrsData = null
                        pendingImportUri = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showQRScanSheet) {
        QRScanSheet(
            onDismiss = { showQRScanSheet = false },
            onScanResult = { result ->
                showQRScanSheet = false
                when (result) {
                    is QRScanResult.QRSData -> {
                        coroutineScope.launch {
                            when (val parseResult = importHandler.parseQRSData(result.data)) {
                                is ProfileImportHandler.QRSParseResult.Success -> {
                                    withContext(Dispatchers.Main) {
                                        pendingImportName = parseResult.name
                                        pendingQrsData = result.data
                                        showImportConfirmDialog = true
                                    }
                                }
                                is ProfileImportHandler.QRSParseResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        context.errorDialogBuilder(Exception(parseResult.message)).show()
                                    }
                                }
                            }
                        }
                    }
                    is QRScanResult.RemoteProfile -> {
                        coroutineScope.launch {
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
                                                if (importResult.needsRoutingSetup) {
                                                    onOpenSetupRouting()
                                                } else {
                                                    onProfileEdit(importResult.profile)
                                                }
                                            }
                                        }
                                        is ProfileImportHandler.ImportResult.Error -> {
                                            withContext(Dispatchers.Main) {
                                                context.errorDialogBuilder(Exception(importResult.message)).show()
                                            }
                                        }
                                    }
                                }
                                is ProfileImportHandler.QRCodeParseResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        context.errorDialogBuilder(Exception(parseResult.message)).show()
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

private suspend fun createProfileContent(profile: Profile): ByteArray {
    val content = ProfileContent()
    content.name = profile.name
    when (profile.typed.type) {
        TypedProfile.Type.Local -> {
            content.type = Libbox.ProfileTypeLocal
        }
        TypedProfile.Type.Remote -> {
            content.type = Libbox.ProfileTypeRemote
        }
    }
    content.config = java.io.File(profile.typed.path).readText()
    content.remotePath = profile.typed.remoteURL
    content.autoUpdate = profile.typed.autoUpdate
    content.autoUpdateInterval = profile.typed.autoUpdateInterval
    content.lastUpdated = profile.typed.lastUpdated.time
    return content.encode()
}

@Composable
private fun ProfileInfoRow(profile: Profile?) {
    if (profile == null) return

    val context = LocalContext.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (profile.typed.type == TypedProfile.Type.Remote) {
                    Icons.Default.Cloud
                } else {
                    Icons.Outlined.Description
                },
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (profile.typed.type == TypedProfile.Type.Remote) {
                    stringResource(R.string.profile_type_remote)
                } else {
                    stringResource(R.string.profile_type_local)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (profile.typed.type == TypedProfile.Type.Remote) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = RelativeTimeFormatter.format(context, profile.typed.lastUpdated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    profile: Profile?,
    isUpdating: Boolean,
    showUpdateSuccess: Boolean,
    onEdit: () -> Unit,
    onUpdate: () -> Unit,
    onShareFile: () -> Unit,
    onSaveFile: () -> Unit,
    onSaveJson: () -> Unit,
    onShareJson: () -> Unit,
    onShareURL: () -> Unit,
    onShareQRS: () -> Unit,
) {
    if (profile == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionButton(
            icon = Icons.Default.Edit,
            contentDescription = stringResource(R.string.edit),
            onClick = onEdit,
        )

        if (profile.typed.type == TypedProfile.Type.Remote) {
            ActionButton(
                icon = when {
                    showUpdateSuccess -> Icons.Default.Check
                    else -> Icons.Default.Refresh
                },
                contentDescription = stringResource(R.string.update_profile),
                onClick = onUpdate,
                enabled = !isUpdating && !showUpdateSuccess,
                isLoading = isUpdating,
            )
        }

        ShareButton(
            profile = profile,
            onShareFile = onShareFile,
            onSaveFile = onSaveFile,
            onSaveJson = onSaveJson,
            onShareJson = onShareJson,
            onShareURL = onShareURL,
            onShareQRS = onShareQRS,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(0.dp),
        color = if (isSystemInDarkTheme()) {
            lerp(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.surfaceContainerHigh,
                0.5f,
            )
        } else {
            MaterialTheme.colorScheme.surfaceDim
        },
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

@Composable
private fun ShareButton(
    profile: Profile,
    onShareFile: () -> Unit,
    onSaveFile: () -> Unit,
    onSaveJson: () -> Unit,
    onShareJson: () -> Unit,
    onShareURL: () -> Unit,
    onShareQRS: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        ActionButton(
            icon = Icons.Default.IosShare,
            contentDescription = stringResource(R.string.menu_share),
            onClick = { expanded = true },
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save_as_file)) },
                onClick = {
                    expanded = false
                    onSaveFile()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_file)) },
                onClick = {
                    expanded = false
                    onShareFile()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save_content_json)) },
                onClick = {
                    expanded = false
                    onSaveJson()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DataObject,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_content_json)) },
                onClick = {
                    expanded = false
                    onShareJson()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DataObject,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
            if (profile.typed.type == TypedProfile.Type.Remote) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.profile_share_url)) },
                    onClick = {
                        expanded = false
                        onShareURL()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_qrs)) },
                onClick = {
                    expanded = false
                    onShareQRS()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }
    }
}

/**
 * One profile in the list: tap the name to open it, the radio to make it active,
 * the share icon for the export menu.
 */
@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onShareFile: () -> Unit,
    onSaveFile: () -> Unit,
    onSaveJson: () -> Unit,
    onShareJson: () -> Unit,
    onShareURL: () -> Unit,
    onShareQRS: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DossierRadioButton(
            selected = selected,
            onClick = onSelect,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(0.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = profile.typed.type.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ShareButton(
            profile = profile,
            onShareFile = onShareFile,
            onSaveFile = onSaveFile,
            onSaveJson = onSaveJson,
            onShareJson = onShareJson,
            onShareURL = onShareURL,
            onShareQRS = onShareQRS,
        )
    }
}
