package com.example.data.dao

import androidx.room.*
import com.example.data.model.Credential
import kotlinx.coroutines.flow.Flow

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials ORDER BY updatedAt DESC")
    fun getAllCredentials(): Flow<List<Credential>>

    @Query("SELECT * FROM credentials WHERE id = :id LIMIT 1")
    suspend fun getCredentialById(id: Int): Credential?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: Credential)

    @Update
    suspend fun updateCredential(credential: Credential)

    @Delete
    suspend fun deleteCredential(credential: Credential)

    @Query("DELETE FROM credentials")
    suspend fun clearAllCredentials()
}
