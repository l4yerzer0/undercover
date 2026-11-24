package com.undercover.vpn.compose.screen.configuration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.undercover.vpn.R
import com.undercover.vpn.compose.base.SelectableMessageDialog
import com.undercover.vpn.compose.topbar.OverrideTopBar
import com.undercover.vpn.shared.ui.components.DossierSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProfileScreen(
    importName: String? = null,
    importUrl: String? = null,
    qrsData: ByteArray? = null,
    assignedProfileId: Long = 0L,
    onNavigateBack: () -> Unit,
    onProfileCreated: (profileId: Long, isManual: Boolean) -> Unit,
    viewModel: NewProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSubscriptionMode = !importUrl.isNullOrBlank() || uiState.profileType == ProfileType.Remote

    LaunchedEffect(importName, importUrl, qrsData, assignedProfileId) {
        if (qrsData != null) {
            viewModel.initializeFromQRSImport(importName, qrsData, assignedProfileId)
        } else if (!importUrl.isNullOrBlank()) {
            viewModel.initializeFromQRImport(importName, importUrl, assignedProfileId)
        } else {
            viewModel.updateProfileType(ProfileType.Local)
            viewModel.updateProfileSource(ProfileSource.CreateNew)
            viewModel.updateAssignedProfileId(assignedProfileId)
            if (!importName.isNullOrBlank()) {
                viewModel.updateName(importName)
            }
        }
    }

    // Error dialog state
    var showErrorDialog by remember { mutableStateOf(false) }

    // Handle success
    LaunchedEffect(uiState.isSuccess, uiState.createdProfile) {
        if (uiState.isSuccess) {
            uiState.createdProfile?.let { profile ->
                onProfileCreated(profile.id, !isSubscriptionMode)
            }
        }
    }

    // Show error dialog when there's an error message
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            showErrorDialog = true
        }
    }

    if (showErrorDialog) {
        SelectableMessageDialog(
            title = stringResource(R.string.error_title),
            message = uiState.errorMessage ?: "",
            onDismiss = {
                showErrorDialog = false
                viewModel.clearError()
            },
        )
    }

    val screenTitle = if (isSubscriptionMode) "ИМПОРТ ПОДПИСКИ" else "НОВЫЙ ПРОФИЛЬ"

    OverrideTopBar {
        TopAppBar(
            title = {
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Section Tag
            Text(
                text = if (isSubscriptionMode) "[ ПОДПИСКА // ПАРАМЕТРЫ ]" else "[ ЛОКАЛЬНЫЙ // КОНФИГ ]",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )

            // Main Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(0.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Subscription Name
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isSubscriptionMode) "НАЗВАНИЕ ПОДПИСКИ" else "НАЗВАНИЕ ПРОФИЛЯ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        OutlinedTextField(
                            value = uiState.name,
                            onValueChange = viewModel::updateName,
                            placeholder = {
                                Text(
                                    if (uiState.isLoadingMetadata) "Получение названия..." else "Введите название",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            },
                            trailingIcon = {
                                if (uiState.isLoadingMetadata) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(0.dp),
                            isError = uiState.nameError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            ),
                            supportingText = uiState.nameError?.let { error ->
                                { Text(text = error, color = MaterialTheme.colorScheme.error) }
                            },
                        )
                    }

                    if (isSubscriptionMode && uiState.remoteUrl.isNotBlank()) {
                        Text(
                            text = "URL: ${uiState.remoteUrl}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 2,
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            thickness = 1.dp,
                        )

                        // Auto-update switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Автообновление",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Периодически загружать обновленные серверы",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            DossierSwitch(
                                checked = uiState.autoUpdate,
                                onCheckedChange = viewModel::updateAutoUpdate,
                            )
                        }

                        // Auto-update interval in hours
                        AnimatedVisibility(
                            visible = uiState.autoUpdate,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(
                                    text = "ИНТЕРВАЛ ОБНОВЛЕНИЯ (В ЧАСАХ)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                                val intervalHours = (uiState.autoUpdateInterval / 60).coerceAtLeast(1)
                                OutlinedTextField(
                                    value = intervalHours.toString(),
                                    onValueChange = viewModel::updateAutoUpdateIntervalHours,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(0.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    ),
                                    supportingText = {
                                        Text(
                                            text = "Рекомендуемый интервал: от 1 до 24 часов",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (uiState.isSaving) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primary
                        )
                        .clickable(enabled = !uiState.isSaving) {
                            viewModel.validateAndCreateProfile()
                        }
                        .padding(vertical = 16.dp, horizontal = 24.dp),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = if (isSubscriptionMode) "ПРОДОЛЖИТЬ" else "СОЗДАТЬ И РЕДАКТИРОВАТЬ",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}
