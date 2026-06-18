package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_metadata")
data class VaultMetadata(
    @PrimaryKey val id: Int = 1,
    val saltBase64: String,
    val verificationBase64: String,
    val verificationIvBase64: String,
    val createdAt: Long = System.currentTimeMillis()
)
