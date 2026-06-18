package com.example.ui.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoUtils
import com.example.data.AppDatabase
import com.example.data.model.Credential
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.HttpURLConnection

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = VaultRepository(database.vaultMetadataDao(), database.credentialDao())

    private val _isSetupCompleted = MutableStateFlow(false)
    val isSetupCompleted: StateFlow<Boolean> = _isSetupCompleted.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Decrypted credentials observed by UI reactively
    val credentials: StateFlow<List<Credential.Decrypted>> = repository.getDecryptedCredentials()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // GitHub Sync states
    private val _gitHubRepo = MutableStateFlow("")
    val gitHubRepo = _gitHubRepo.asStateFlow()

    private val _gitHubPath = MutableStateFlow("fortress_vault.json")
    val gitHubPath = _gitHubPath.asStateFlow()

    private val _gitHubToken = MutableStateFlow("")
    val gitHubToken = _gitHubToken.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus = _syncStatus.asStateFlow()

    private val _syncIsLoading = MutableStateFlow(false)
    val syncIsLoading = _syncIsLoading.asStateFlow()

    // Passphrase for exports/imports
    var exportPassphrase = MutableStateFlow("")
    var importPassphrase = MutableStateFlow("")

    private val okHttpClient = OkHttpClient()

    init {
        checkVaultStatus()
        loadGitHubConfig()
    }

    /**
     * Inspects room database to see if initialization setup has already been done.
     */
    fun checkVaultStatus() {
        viewModelScope.launch {
            _isSetupCompleted.value = repository.hasVaultMetadata()
            _isUnlocked.value = CryptoUtils.isUnlocked()
        }
    }

    /**
     * Runs configuration of Master Password.
     */
    fun initializeVault(password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.initializeVault(password)
            if (success) {
                _isSetupCompleted.value = true
                _isUnlocked.value = true
            }
            onComplete(success)
        }
    }

    /**
     * Tries to unlock the database session using Master Password.
     */
    fun unlockVault(password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.unlock(password)
            if (success) {
                _isUnlocked.value = true
            }
            onComplete(success)
        }
    }

    /**
     * Locks vault instantly.
     */
    fun lockVault() {
        repository.lock()
        _isUnlocked.value = false
    }

    /**
     * Wipes all contents and resets Vault.
     */
    fun selfDestructVault() {
        viewModelScope.launch {
            repository.selfDestructVault()
            _isSetupCompleted.value = false
            _isUnlocked.value = false
        }
    }

    /**
     * Saves or replaces a security credential.
     */
    fun saveCredential(
        id: Int,
        title: String,
        username: String,
        passwordRaw: String,
        url: String,
        notes: String,
        category: String,
        isFavorite: Boolean,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val success = repository.saveCredential(
                id = id,
                title = title,
                username = username,
                passwordRaw = passwordRaw,
                url = url,
                notes = notes,
                category = category,
                isFavorite = isFavorite
            )
            onComplete(success)
        }
    }

    /**
     * Removes a credential by ID.
     */
    fun deleteCredential(id: Int) {
        viewModelScope.launch {
            repository.deleteCredential(id)
        }
    }

    /**
     * Export AES-encrypted JSON backups local data.
     */
    fun generateEncryptedExport(passphrase: String): String? {
        // Run synchronously/fast on current memory list
        val currentList = credentials.value
        return try {
            // Need a coroutine-friendly wrapper if needed, or run inline safely
            var result: String? = null
            viewModelScope.launch {
                result = repository.exportVaultEncrypted(passphrase, currentList)
            }.run { 
                // Return calculated result
            }
            // Better to block CPU thread for decryption if it's moderate size, or block with runBlocking or inline.
            // Let's implement inline runBlocking / simple synchronous calculation
            val salt = CryptoUtils.generateSalt()
            val encryptionKey = CryptoUtils.deriveKey(passphrase, salt)
            
            val rootArray = org.json.JSONArray()
            for (item in currentList) {
                val obj = org.json.JSONObject()
                obj.put("title", item.title)
                obj.put("username", item.username)
                obj.put("password", item.passwordOriginal)
                obj.put("url", item.url)
                obj.put("notes", item.notes)
                obj.put("category", item.category)
                obj.put("isFavorite", item.isFavorite)
                rootArray.put(obj)
            }
            val jsonString = rootArray.toString(2)
            val encryptionResult = CryptoUtils.encrypt(jsonString, encryptionKey) ?: return null
            
            val exportPayload = org.json.JSONObject()
            exportPayload.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            exportPayload.put("ciphertext", encryptionResult.first)
            exportPayload.put("iv", encryptionResult.second)
            exportPayload.put("checksum_proof", "FORTRESS_EXPORT")
            
            val proofResult = CryptoUtils.encrypt("FORTRESS_EXPORT", encryptionKey)
            exportPayload.put("proof_cipher", proofResult?.first)
            exportPayload.put("proof_iv", proofResult?.second)
            
            exportPayload.toString(2)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Imports an AES-encrypted JSON backup.
     */
    fun importEncryptedPayload(passphrase: String, payloadJson: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importVaultEncrypted(passphrase, payloadJson)
            onComplete(success)
        }
    }

    // --- GITHUB CONFIG SYNCHRONIZATION ---

    fun updateGitHubConfig(repo: String, path: String, token: String) {
        _gitHubRepo.value = repo
        _gitHubPath.value = path
        _gitHubToken.value = token
        saveGitHubConfig(repo, path, token)
    }

    private fun saveGitHubConfig(repo: String, path: String, token: String) {
        val sp = getApplication<Application>().getSharedPreferences("fortress_sync", Application.MODE_PRIVATE)
        sp.edit()
            .putString("repo", repo)
            .putString("path", path)
            .putString("token", token)
            .apply()
    }

    private fun loadGitHubConfig() {
        val sp = getApplication<Application>().getSharedPreferences("fortress_sync", Application.MODE_PRIVATE)
        _gitHubRepo.value = sp.getString("repo", "") ?: ""
        _gitHubPath.value = sp.getString("path", "fortress_vault.json") ?: "fortress_vault.json"
        _gitHubToken.value = sp.getString("token", "") ?: ""
    }

    /**
     * Commits/Pushes the AES-GCM Encrypted vault JSON directly to the user's GitHub Repository.
     */
    fun pushVaultToGitHub(passphrase: String) {
        val repo = _gitHubRepo.value.trim()
        val path = _gitHubPath.value.trim()
        val token = _gitHubToken.value.trim()

        if (repo.isEmpty() || path.isEmpty() || token.isEmpty()) {
            _syncStatus.value = "Error: Repository details and PAT token cannot be blank!"
            return
        }
        if (passphrase.isEmpty()) {
            _syncStatus.value = "Error: Master or Backup Password required to encrypt contents!"
            return
        }

        _syncIsLoading.value = true
        _syncStatus.value = "Generating encrypted vault package..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Generate encrypted string backup
                val encryptedBackupContent = repository.exportVaultEncrypted(passphrase, credentials.value)
                if (encryptedBackupContent == null) {
                    withContext(Dispatchers.Main) {
                        _syncStatus.value = "Error: Cryptography packaging failed!"
                        _syncIsLoading.value = false
                    }
                    return@launch
                }

                val targetUrl = "https://api.github.com/repos/$repo/contents/$path"

                // 2. Search if file exists to fetch its SHA (required for file updates in GitHub REST API)
                val getRequest = Request.Builder()
                    .url(targetUrl)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                var fileSha: String? = null
                okHttpClient.newCall(getRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val obj = JSONObject(bodyString)
                        fileSha = obj.optString("sha", null)
                    }
                }

                // 3. Compile push request body
                val pushBody = JSONObject()
                pushBody.put("message", "Fortress vault synchronized backup (AES-256 encrypted)")
                // Content must be base64-encoded to push to GitHub contents API
                val payloadBase64 = Base64.encodeToString(encryptedBackupContent.toByteArray(), Base64.NO_WRAP)
                pushBody.put("content", payloadBase64)
                if (fileSha != null) {
                    pushBody.put("sha", fileSha)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val putRequest = Request.Builder()
                    .url(targetUrl)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .put(pushBody.toString().toRequestBody(mediaType))
                    .build()

                okHttpClient.newCall(putRequest).execute().use { response ->
                    withContext(Dispatchers.Main) {
                        _syncIsLoading.value = false
                        if (response.isSuccessful) {
                            _syncStatus.value = "Success! Backup successfully pushed to GitHub."
                        } else {
                            _syncStatus.value = "Failed: ${response.code} ${response.message}\nCheck your repo path and token access scopes."
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _syncIsLoading.value = false
                    _syncStatus.value = "Connection Error: ${e.localizedMessage}"
                }
            }
        }
    }

    /**
     * Downloads/Pulls the AES-encrypted JSON file from GitHub and decrypts/inserts it into Room database locally.
     */
    fun pullVaultFromGitHub(passphrase: String) {
        val repo = _gitHubRepo.value.trim()
        val path = _gitHubPath.value.trim()
        val token = _gitHubToken.value.trim()

        if (repo.isEmpty() || path.isEmpty() || token.isEmpty()) {
            _syncStatus.value = "Error: Repository details and PAT token cannot be blank!"
            return
        }
        if (passphrase.isEmpty()) {
            _syncStatus.value = "Error: Passcode is required to decrypt pulled package content!"
            return
        }

        _syncIsLoading.value = true
        _syncStatus.value = "Pulling file from GitHub..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetUrl = "https://api.github.com/repos/$repo/contents/$path"
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            _syncIsLoading.value = false
                            _syncStatus.value = "Pull Failed: Server returned status code ${response.code}. Does the file exist?"
                        }
                        return@launch
                    }

                    val bodyStr = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(bodyStr)
                    val base64EncodedFile = jsonResponse.getString("content").replace("\n", "").trim()
                    // Decode GitHub file content
                    val fileContentsBytes = Base64.decode(base64EncodedFile, Base64.NO_WRAP)
                    val encryptedBackupPayload = String(fileContentsBytes)

                    withContext(Dispatchers.Main) {
                        _syncStatus.value = "Decrypting pulled payload..."
                    }

                    // Re-encrypt/save into live Room database
                    val importSuccess = repository.importVaultEncrypted(passphrase, encryptedBackupPayload)

                    withContext(Dispatchers.Main) {
                        _syncIsLoading.value = false
                        if (importSuccess) {
                            _syncStatus.value = "Success! Pulled and decrypted vault contents from GitHub. Local database updated."
                        } else {
                            _syncStatus.value = "Decryption Failed: Incorrect password/passphrase for backing package."
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _syncIsLoading.value = false
                    _syncStatus.value = "Connection Error: ${e.localizedMessage}"
                }
            }
        }
    }
}
