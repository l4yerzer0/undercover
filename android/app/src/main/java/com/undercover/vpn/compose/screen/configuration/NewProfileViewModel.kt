package com.undercover.vpn.compose.screen.configuration

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.nekohasekai.libbox.Libbox
import com.undercover.vpn.R
import com.undercover.vpn.bg.UpdateProfileWork
import com.undercover.vpn.database.Profile
import com.undercover.vpn.database.ProfileManager
import com.undercover.vpn.database.TypedProfile
import com.undercover.vpn.utils.HTTPClient
import com.undercover.vpn.shared.parser.SubscriptionContentParser
import com.undercover.vpn.shared.parser.SubscriptionMetadataFetcher
import com.undercover.vpn.shared.parser.SubscriptionNodesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Date

data class NewProfileUiState(
    val name: String = "",
    val profileType: ProfileType = ProfileType.Local,
    val profileSource: ProfileSource = ProfileSource.CreateNew,
    // Remote profile fields
    val remoteUrl: String = "",
    val autoUpdate: Boolean = true,
    val autoUpdateInterval: Int = 120, // in minutes (default 2 hours)
    val cachedContent: String? = null,
    val isLoadingMetadata: Boolean = false,
    // File import
    val importUri: Uri? = null,
    val importFileName: String? = null,
    // QRS import
    val qrsData: ByteArray? = null,
    val assignedProfileId: Long = 0L,
    // State
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    val createdProfile: Profile? = null,
    val needsRoutingSetup: Boolean = false,
    // Field errors
    val nameError: String? = null,
    val remoteUrlError: String? = null,
    val importError: String? = null,
)

enum class ProfileType {
    Local,
    Remote,
}

enum class ProfileSource {
    CreateNew,
    Import,
}

class NewProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(NewProfileUiState())
    val uiState: StateFlow<NewProfileUiState> = _uiState.asStateFlow()

    fun initializeFromQRImport(name: String?, url: String?, assignedProfileId: Long = 0L) {
        if (!url.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    name = name ?: "",
                    profileType = ProfileType.Remote,
                    remoteUrl = url,
                    isLoadingMetadata = true,
                    assignedProfileId = assignedProfileId,
                )
            }
            viewModelScope.launch {
                try {
                    val fetchResult = withContext(Dispatchers.IO) {
                        SubscriptionMetadataFetcher.fetch(url, HTTPClient.userAgent)
                    }
                    val meta = fetchResult.metadata
                    _uiState.update { current ->
                        current.copy(
                            isLoadingMetadata = false,
                            name = if (current.name.isBlank() || current.name == name) {
                                meta.title ?: current.name.ifBlank { "Subscription" }
                            } else {
                                current.name
                            },
                            autoUpdateInterval = (meta.updateIntervalHours ?: (current.autoUpdateInterval / 60)).coerceAtLeast(1) * 60,
                            cachedContent = fetchResult.content,
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isLoadingMetadata = false,
                            name = if (current.name.isBlank()) name ?: "Subscription" else current.name,
                        )
                    }
                }
            }
        }
    }

    fun initializeFromQRSImport(name: String?, qrsData: ByteArray, assignedProfileId: Long = 0L) {
        _uiState.update {
            it.copy(
                name = name ?: "",
                profileType = ProfileType.Local,
                profileSource = ProfileSource.Import,
                qrsData = qrsData,
                assignedProfileId = assignedProfileId,
            )
        }
    }

    fun updateAssignedProfileId(id: Long) {
        _uiState.update { it.copy(assignedProfileId = id) }
    }

    fun updateName(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                nameError = if (name.isNotBlank()) null else it.nameError,
            )
        }
    }

    fun updateProfileType(type: ProfileType) {
        _uiState.update { it.copy(profileType = type) }
    }

    fun updateProfileSource(source: ProfileSource) {
        _uiState.update {
            it.copy(
                profileSource = source,
                importError = null, // Clear import error when changing source
            )
        }
    }

    fun updateRemoteUrl(url: String) {
        _uiState.update {
            it.copy(
                remoteUrl = url,
                remoteUrlError = if (url.isNotBlank()) null else it.remoteUrlError,
            )
        }
    }

    fun updateAutoUpdate(enabled: Boolean) {
        _uiState.update { it.copy(autoUpdate = enabled) }
    }

    fun updateAutoUpdateInterval(interval: String) {
        val intValue = interval.toIntOrNull() ?: 60
        _uiState.update { it.copy(autoUpdateInterval = intValue.coerceAtLeast(15)) }
    }

    fun updateAutoUpdateIntervalHours(hoursStr: String) {
        val hours = hoursStr.toIntOrNull() ?: 1
        _uiState.update { it.copy(autoUpdateInterval = hours.coerceAtLeast(1) * 60) }
    }

    fun setImportUri(uri: Uri, fileName: String?) {
        _uiState.update {
            it.copy(
                importUri = uri,
                importFileName = fileName,
                importError = null, // Clear error when file is selected
                name =
                if (it.name.isEmpty()) {
                    fileName?.substringBeforeLast(".") ?: "Imported Profile"
                } else {
                    it.name
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun validateAndCreateProfile(): Boolean {
        val state = _uiState.value
        val context = getApplication<Application>()

        // Clear previous errors
        _uiState.update {
            it.copy(
                nameError = null,
                remoteUrlError = null,
                importError = null,
            )
        }

        var hasError = false

        // Validate name
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = context.getString(R.string.profile_input_required)) }
            hasError = true
        }

        // Validate based on profile type
        when (state.profileType) {
            ProfileType.Local -> {
                if (state.profileSource == ProfileSource.Import && state.importUri == null && state.qrsData == null) {
                    _uiState.update { it.copy(importError = context.getString(R.string.profile_input_required)) }
                    hasError = true
                }
            }
            ProfileType.Remote -> {
                if (state.remoteUrl.isBlank()) {
                    _uiState.update { it.copy(remoteUrlError = context.getString(R.string.profile_input_required)) }
                    hasError = true
                }
            }
        }

        if (hasError) {
            return false
        }

        // If validation passes, create the profile
        createProfile()
        return true
    }

    private fun createProfile() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }

            try {
                val profile =
                    withContext(Dispatchers.IO) {
                        when (state.profileType) {
                            ProfileType.Local -> createLocalProfile(state)
                            ProfileType.Remote -> createRemoteProfile(state)
                        }
                    }

                val needsRoutingSetup = com.undercover.vpn.utils.FirstProfileRoutingHelper.applyRussianRoutingIfFirstProfile(getApplication())

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        isSuccess = true,
                        createdProfile = profile,
                        needsRoutingSetup = needsRoutingSetup,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }

    private suspend fun createLocalProfile(state: NewProfileUiState): Profile {
        val context = getApplication<Application>()
        val typedProfile =
            TypedProfile().apply {
                type = TypedProfile.Type.Local
            }

        val profile =
            Profile(name = state.name, typed = typedProfile).apply {
                userOrder = ProfileManager.nextOrder()
                assignedProfileId = state.assignedProfileId
            }

        val fileID = ProfileManager.nextFileID()
        val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "$fileID.json")
        typedProfile.path = configFile.path

        // Get config content
        val configContent =
            when (state.profileSource) {
                ProfileSource.CreateNew -> "{}"
                ProfileSource.Import -> {
                    if (state.qrsData != null) {
                        val content = Libbox.decodeProfileContent(state.qrsData)
                        content.config
                    } else {
                        state.importUri?.let { uri ->
                            val sourceURL = uri.toString()
                            when {
                                sourceURL.startsWith("content://") -> {
                                    val inputStream = context.contentResolver.openInputStream(uri) as InputStream
                                    inputStream.use { it.bufferedReader().readText() }
                                }
                                sourceURL.startsWith("file://") -> {
                                    File(Uri.parse(sourceURL).path!!).readText()
                                }
                                sourceURL.startsWith("http://") || sourceURL.startsWith("https://") -> {
                                    HTTPClient().use { it.getString(sourceURL) }
                                }
                                else -> throw Exception("Unsupported source: $sourceURL")
                            }
                        } ?: "{}"
                    }
                }
            }

        // Validate config
        val migratedContent = com.undercover.vpn.shared.parser.SingBoxConfigMigrator.migrate(configContent)
        Libbox.checkConfig(migratedContent)
        configFile.writeText(migratedContent)

        // Create profile in database and select it
        ProfileManager.create(profile, andSelect = true)

        return profile
    }

    private suspend fun createRemoteProfile(state: NewProfileUiState): Profile {
        val context = getApplication<Application>()
        val typedProfile =
            TypedProfile().apply {
                type = TypedProfile.Type.Remote
                remoteURL = state.remoteUrl
                autoUpdate = state.autoUpdate
                autoUpdateInterval = state.autoUpdateInterval
                lastUpdated = Date()
            }

        val profile =
            Profile(name = state.name, typed = typedProfile).apply {
                userOrder = ProfileManager.nextOrder()
                assignedProfileId = state.assignedProfileId
            }

        val fileID = ProfileManager.nextFileID()
        val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "$fileID.json")
        typedProfile.path = configFile.path

        // Fetch initial config - this MUST succeed for remote profiles
        val rawContent = state.cachedContent ?: withContext(Dispatchers.IO) {
            SubscriptionMetadataFetcher.fetch(state.remoteUrl, HTTPClient.userAgent).content
        }
        // Subscriptions may be a sing-box JSON config, base64, or a plain list of proxy links.
        val multiCore = SubscriptionContentParser.toMultiCoreConfig(rawContent)
            ?: throw IllegalStateException("Не удалось распознать формат подписки: ожидается конфигурация sing-box или список прокси-ссылок")
        val migratedContent = com.undercover.vpn.shared.parser.SingBoxConfigMigrator.migrate(multiCore.singBoxJson)
        Libbox.checkConfig(migratedContent)
        val configContent = migratedContent

        configFile.writeText(configContent)
        SubscriptionNodesHelper.saveXraySidecar(configFile.path, multiCore.xrayConfigsByTag)

        // Create profile in database and select it
        ProfileManager.create(profile, andSelect = true)

        // Reconfigure updater if auto-update is enabled
        if (state.autoUpdate) {
            UpdateProfileWork.reconfigureUpdater()
        }

        return profile
    }
}
