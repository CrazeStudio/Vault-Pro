package com.example.crypto

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // Active key kept in memory while the app is unlocked.
    // Cleared as soon as session locks.
    @Volatile
    private var sessionKey: SecretKeySpec? = null

    /**
     * Derives a secret key from master password and a salt.
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    /**
     * Sets the active session key.
     */
    fun setSessionKey(key: SecretKeySpec) {
        sessionKey = key
    }

    /**
     * Gets the active session key.
     */
    fun getSessionKey(): SecretKeySpec? {
        return sessionKey
    }

    /**
     * Checks if the vaults/sessions are unlocked.
     */
    fun isUnlocked(): Boolean {
        return sessionKey != null
    }

    /**
     * Locks the vault by clearing the session key.
     */
    fun lock() {
        sessionKey = null
    }

    /**
     * Helper to generate a random cryptographically secure salt.
     */
    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    /**
     * Helper to generate a random GCM IV.
     */
    fun generateIv(): ByteArray {
        val random = SecureRandom()
        val iv = ByteArray(GCM_IV_LENGTH)
        random.nextBytes(iv)
        return iv
    }

    /**
     * Encrypts plain text using the current session key.
     * Returns a pair of (Base64 Ciphertext, Base64 IV)
     */
    fun encrypt(plainText: String, customKey: SecretKeySpec? = null): Pair<String, String>? {
        val key = customKey ?: sessionKey ?: return null
        return try {
            val iv = generateIv()
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            val cipherTextBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            val base64CipherText = Base64.encodeToString(cipherTextBytes, Base64.NO_WRAP)
            val base64Iv = Base64.encodeToString(iv, Base64.NO_WRAP)
            Pair(base64CipherText, base64Iv)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypts ciphertext using its specific base64 IV and current session key.
     */
    fun decrypt(base64CipherText: String, base64Iv: String, customKey: SecretKeySpec? = null): String? {
        val key = customKey ?: sessionKey ?: return null
        return try {
            val cipherTextBytes = Base64.decode(base64CipherText, Base64.NO_WRAP)
            val ivBytes = Base64.decode(base64Iv, Base64.NO_WRAP)
            
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainTextBytes = cipher.doFinal(cipherTextBytes)
            String(plainTextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a random secure password.
     */
    fun generateSecurePassword(
        length: Int = 16,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeNumbers: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val uppercaseChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowercaseChars = "abcdefghijklmnopqrstuvwxyz"
        val numberChars = "0123456789"
        val symbolChars = "!@#$%^&*()-_=+[]{}|;:,.<>?"

        val charPool = StringBuilder()
        val guaranteedChars = mutableListOf<Char>()
        val random = SecureRandom()

        if (includeUppercase) {
            charPool.append(uppercaseChars)
            guaranteedChars.add(uppercaseChars[random.nextInt(uppercaseChars.length)])
        }
        if (includeLowercase) {
            charPool.append(lowercaseChars)
            guaranteedChars.add(lowercaseChars[random.nextInt(lowercaseChars.length)])
        }
        if (includeNumbers) {
            charPool.append(numberChars)
            guaranteedChars.add(numberChars[random.nextInt(numberChars.length)])
        }
        if (includeSymbols) {
            charPool.append(symbolChars)
            guaranteedChars.add(symbolChars[random.nextInt(symbolChars.length)])
        }

        if (charPool.isEmpty()) {
            charPool.append(lowercaseChars)
        }

        val remainingLength = length - guaranteedChars.size
        for (i in 0 until remainingLength) {
            charPool.append(charPool[random.nextInt(charPool.length)])
        }

        val generatedList = guaranteedChars + List(remainingLength) { charPool[random.nextInt(charPool.length)] }
        return generatedList.shuffled(random).joinToString("")
    }
}
