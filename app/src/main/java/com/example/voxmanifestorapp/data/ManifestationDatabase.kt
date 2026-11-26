package com.example.voxmanifestorapp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Manifestation::class,
        ConceptEntity::class,
        ConceptItemEntity::class,
        ConversationLogEntry::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(MetadataTypeConverter::class)
abstract class ManifestationDatabase : RoomDatabase() {
    abstract fun manifestationDao(): ManifestationDao
    abstract fun conceptDao(): ConceptDao
    abstract fun conversationLogDao(): ConversationLogDao
}
