package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.CredentialDao
import com.example.data.dao.VaultMetadataDao
import com.example.data.model.Credential
import com.example.data.model.VaultMetadata

@Database(entities = [VaultMetadata::class, Credential::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultMetadataDao(): VaultMetadataDao
    abstract fun credentialDao(): CredentialDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fortress_database"
                )
                .fallbackToDestructiveMigration() // ensures safety during dev schema additions
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
