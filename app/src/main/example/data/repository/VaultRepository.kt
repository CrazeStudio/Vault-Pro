package com.example.data.repository

import android.util.Base64
import com.example.crypto.CryptoUtils
import com.example.data.dao.CredentialDao
import com.example.data.dao.VaultMetadataDao
import com.example.data.model.Credential
import com.example.data.model.VaultMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.spec.SecretKeySpec

class VaultRepository(
    private val vaultMetadataDao: VaultMetadataDao,
    private val credentialDao: CredentialDao
) {

    // Expose whether user has ever set up their vault
    suspend fun hasVaultMetadata(): Boolean {
        return vaultMetadataDao.getMetadata() != null
    }

    /**
     * Attempts to unlock the vault. Derives key and matches verification text.
     */
    suspend fun unlock(masterPassword: String): Boolean {
        val metadata = vaultMetadataDao.getMetadata() ?: return false
        val saltBytes = Base64.decode(metadata.saltBase64, Base64.NO_WRAP)
        
        try {
            // Derive a key candidate from the typed password and the stored salt
            val candidateKey = CryptoUtils.deriveKey(masterPassword, saltBytes)
            
            // Try of decrypting the verification token. Verification text should equal "FORTRESS_VERIFY"
            val decryptedVerification = CryptoUtils.decrypt(
                metadata.verificationBase64,
                metadata.verificationIvBase64,
                candidateKey
            )
            
            if (decryptedVerification == "FORTRESS_VERIFY") {
                CryptoUtils.setSessionKey(candidateKey)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * Locks the vault by clearing local RAM keys.
     */
    fun lock() {
        CryptoUtils.lock()
    }

    /**
     * Initializes the Vault Master Password.
     */
    suspend fun initializeVault(masterPassword: String): Boolean {
        try {
            val salt = CryptoUtils.generateSalt()
            val derivedKey = CryptoUtils.deriveKey(masterPassword, salt)
            
            val encryptionResult = CryptoUtils.encrypt("FORTRESS_VERIFY", derivedKey) ?: return false
            val (verifyCipher, verifyIv) = encryptionResult
            
            val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            
            val metadata = VaultMetadata(
                saltBase64 = saltBase64,
                verificationBase64 = verifyCipher,
                verificationIvBase64 = verifyIv
            )
            
            vaultMetadataDao.setMetadata(metadata)
            // Save in current session to unlock immediately
            CryptoUtils.setSessionKey(derivedKey)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Exposes plain decrypted credentials dynamically from the encrypted room items.
     */
    fun getDecryptedCredentials(): Flow<List<Credential.Decrypted>> {
        return credentialDao.getAllCredentials().map { list ->
            list.mapNotNull { encrypted ->
                decryptCredential(encrypted)
            }
        }
    }

    /**
     * Decrypts a Room credential entity in memory.
     */
    private fun decryptCredential(encrypted: Credential): Credential.Decrypted? {
        val sessionKey = CryptoUtils.getSessionKey() ?: return null
        return try {
            val title = CryptoUtils.decrypt(encrypted.titleCipher, encrypted.titleIv, sessionKey) ?: "[Decryption Error]"
            val username = CryptoUtils.decrypt(encrypted.usernameCipher, encrypted.usernameIv, sessionKey) ?: ""
            val password = CryptoUtils.decrypt(encrypted.passwordCipher, encrypted.passwordIv, sessionKey) ?: ""
            val url = CryptoUtils.decrypt(encrypted.urlCipher, encrypted.urlIv, sessionKey) ?: ""
            val notes = CryptoUtils.decrypt(encrypted.notesCipher, encrypted.notesIv, sessionKey) ?: ""
            
            Credential.Decrypted(
                id = encrypted.id,
                title = title,
                username = username,
                passwordOriginal = password,
                url = url,
                notes = notes,
                category = encrypted.category,
                isFavorite = encrypted.isFavorite,
                updatedAt = encrypted.updatedAt
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Saves a decrypted credential back into its encrypted state inside Room.
     */
    suspend fun saveCredential(
        id: Int = 0,
        title: String,
        username: String,
        passwordRaw: String,
        url: String,
        notes: String,
        category: String,
        isFavorite: Boolean = false
    ): Boolean {
        val sessionKey = CryptoUtils.getSessionKey() ?: return false
        try {
            // Encrypt each individual field separately using random GCM IVs
            val titleEnc = CryptoUtils.encrypt(title, sessionKey) ?: return false
            val userEnc = CryptoUtils.encrypt(username, sessionKey) ?: return false
            val passEnc = CryptoUtils.encrypt(passwordRaw, sessionKey) ?: return false
            val urlEnc = CryptoUtils.encrypt(url, sessionKey) ?: return false
            val notesEnc = CryptoUtils.encrypt(notes, sessionKey) ?: return false
            
            val credential = Credential(
                id = id,
                titleCipher = titleEnc.first,
                titleIv = titleEnc.second,
                usernameCipher = userEnc.first,
                usernameIv = userEnc.second,
                passwordCipher = passEnc.first,
                passwordIv = passEnc.second,
                urlCipher = urlEnc.first,
                urlIv = urlEnc.second,
                notesCipher = notesEnc.first,
                notesIv = notesEnc.second,
                category = category,
                isFavorite = isFavorite,
                updatedAt = System.currentTimeMillis()
            )
            
            if (id == 0) {
                credentialDao.insertCredential(credential)
            } else {
                credentialDao.updateCredential(credential)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Deletes a credential.
     */
    suspend fun deleteCredential(id: Int) {
        val item = credentialDao.getCredentialById(id)
        if (item != null) {
            credentialDao.deleteCredential(item)
        }
    }

    /**
     * Completely wipes the Vault! Removes metadata, master keys, and passwords.
     */
    suspend fun selfDestructVault() {
        credentialDao.clearAllCredentials()
        vaultMetadataDao.clearMetadata()
        CryptoUtils.lock()
    }

    /**
     * Exports all decrypted credentials as a fully encrypted JSON backup string.
     * The backup is AES-256-GCM encrypted using an exportPassphrase.
     */
    suspend fun exportVaultEncrypted(exportPassphrase: String, decList: List<Credential.Decrypted>): String? {
        try {
            // Create JSON payload of all credentials
            val rootArray = JSONArray()
            for (item in decList) {
                val obj = JSONObject()
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
            
            // Encrypt using the exportPassphrase
            // Draw a random salt
            val salt = CryptoUtils.generateSalt()
            val encryptionKey = CryptoUtils.deriveKey(exportPassphrase, salt)
            
            val encryptionResult = CryptoUtils.encrypt(jsonString, encryptionKey) ?: return null
            val (cipherText, iv) = encryptionResult
            
            // Write a portable structural packet containing:
            // Salt (Base64), ciphertext (Base64), IV (Base64)
            val exportPayload = JSONObject()
            exportPayload.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            exportPayload.put("ciphertext", cipherText)
            exportPayload.put("iv", iv)
            exportPayload.put("checksum_proof", "FORTRESS_EXPORT") // to verify import password
            
            // Encrypt the proof also to be secure
            val proofResult = CryptoUtils.encrypt("FORTRESS_EXPORT", encryptionKey)
            exportPayload.put("proof_cipher", proofResult?.first)
            exportPayload.put("proof_iv", proofResult?.second)
            
            return exportPayload.toString(2)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Imports credentials from a secure encrypted Backup packet.
     */
    suspend fun importVaultEncrypted(exportPassphrase: String, backupJson: String): Boolean {
        try {
            val root = JSONObject(backupJson)
            val saltBase64 = root.getString("salt")
            val ciphertext = root.getString("ciphertext")
            val iv = root.getString("iv")
            val proofCipher = root.getString("proof_cipher")
            val proofIv = root.getString("proof_iv")
            
            val saltBytes = Base64.decode(saltBase64, Base64.NO_WRAP)
            val key = CryptoUtils.deriveKey(exportPassphrase, saltBytes)
            
            // Verify lock key passphrase correct using the proof cipher
            val proofDec = CryptoUtils.decrypt(proofCipher, proofIv, key)
            if (proofDec != "FORTRESS_EXPORT") {
                return false // key is wrong!
            }
            
            // Decrypt the full payload JSON array
            val payloadDec = CryptoUtils.decrypt(ciphertext, iv, key) ?: return false
            val array = JSONArray(payloadDec)
            
            // Insert each item into current active Room database (re-encrypting them under the session key!)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                saveCredential(
                    title = obj.optString("title", "Imported Security Item"),
                    username = obj.optString("username", ""),
                    passwordRaw = obj.optString("password", ""),
                    url = obj.optString("url", ""),
                    notes = obj.optString("notes", ""),
                    category = obj.optString("category", "Login"),
                    isFavorite = obj.optBoolean("isFavorite", false)
                )
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
