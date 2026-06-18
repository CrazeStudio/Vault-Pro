package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class Credential(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val titleCipher: String,
    val titleIv: String,
    val usernameCipher: String,
    val usernameIv: String,
    val passwordCipher: String,
    val passwordIv: String,
    val urlCipher: String,
    val urlIv: String,
    val notesCipher: String,
    val notesIv: String,
    val category: String, // e.g. "Login", "Card", "Secure Note"
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Utility representation of decrypted credentials in memory ONLY
    data class Decrypted(
        val id: Int,
        val title: String,
        val username: String,
        val passwordOriginal: String,
        val url: String,
        val notes: String,
        val category: String,
        val isFavorite: Boolean,
        val updatedAt: Long
    )
}
