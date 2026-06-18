package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.VaultMetadata

@Dao
interface VaultMetadataDao {
    @Query("SELECT * FROM vault_metadata WHERE id = 1 LIMIT 1")
    suspend fun getMetadata(): VaultMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: VaultMetadata)

    @Query("DELETE FROM vault_metadata")
    suspend fun clearMetadata()
}
