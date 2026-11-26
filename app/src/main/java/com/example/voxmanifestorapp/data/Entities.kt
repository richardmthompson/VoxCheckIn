package com.example.voxmanifestorapp.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation

// Entities:
    // ManifestationWithDetails (containing all the below)
    // Manifestation
    // StateDescription
    // EcologyItem
    // ResourceItem
    // MilestoneItem

@Entity(tableName = "manifestations")
data class Manifestation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val slot: Int,      // which slot is wish placed in on mainscreen?
    val timeframe: String,
    val completedBy: Long?,
    val notes: String,
    val lastDiscussedTimestamp: Long? = null, // Tracks when this wish was last discussed in the Core Loop
    val iconPath: String? = null
)

/*
data class ManifestationWithDetails(
    @Embedded val manifestation: Manifestation,
    @Relation(parentColumn = "id", entityColumn = "manifestationId")
    val stateDescriptions: List<StateDescription>,
    @Relation(parentColumn = "id", entityColumn = "manifestationId")
    val ecologyItems: List<EcologyItem>,
    @Relation(parentColumn = "id", entityColumn = "manifestationId")
    val resourceItems: List<ResourceItem>,
    @Relation(parentColumn = "id", entityColumn = "manifestationId")
    val milestoneItems: List<MilestoneItem>
)

// "how will you know when the manifestation has been realised / accomplished?"

@Entity(
    foreignKeys = [ForeignKey(
        entity = Manifestation::class,
        parentColumns = ["id"],
        childColumns = ["manifestationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class StateDescription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val manifestationId: Int,
    val isPresentState: Boolean,
    val description: String
)

enum class EcologyType {
    INTENTION, CONSEQUENCE
}

@Entity(
    foreignKeys = [ForeignKey(
        entity = Manifestation::class,
        parentColumns = ["id"],
        childColumns = ["manifestationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class EcologyItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val manifestationId: Int,
    val type: EcologyType,
    val description: String
)

// "what resources are available that you could use to help you accomplish this?"

@Entity(
    foreignKeys = [ForeignKey(
        entity = Manifestation::class,
        parentColumns = ["id"],
        childColumns = ["manifestationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ResourceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val manifestationId: Int,
    val description: String
)

// "what milestones are on the way from here to there?"

@Entity(
    foreignKeys = [ForeignKey(
        entity = Manifestation::class,
        parentColumns = ["id"],
        childColumns = ["manifestationId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MilestoneItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val manifestationId: Int,
    val description: String
)


 */