package cn.jiayi.familymemory.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "persons", indices = [Index("name"), Index(value = ["clientUuid"], unique = true)])
data class PersonEntity(
    @androidx.room.PrimaryKey val id: String,
    val clientUuid: String,
    val serverId: String? = null,
    val name: String,
    val surname: String = "",
    val givenName: String = "",
    val formerNames: List<String> = emptyList(),
    val nickname: String = "",
    val gender: String? = null,
    val birthYear: Int? = null,
    val birthDatePrecision: String = "unknown",
    val deathYear: Int? = null,
    val deathDatePrecision: String = "unknown",
    val birthPlace: String = "",
    val ancestralHome: String = "",
    val formerResidences: List<String> = emptyList(),
    val biography: String = "",
    val isSelf: Boolean = false,
    val notes: String = "",
    val sourceText: String = "",
    val verificationStatus: String = "unverified_information",
    val visibility: String = "private",
    val syncVersion: Int = 1,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "relationships",
    foreignKeys = [
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personAId"]),
        ForeignKey(entity = PersonEntity::class, parentColumns = ["id"], childColumns = ["personBId"]),
    ],
    indices = [Index("personAId"), Index("personBId"), Index(value = ["clientUuid"], unique = true)],
)
data class RelationshipEntity(
    @androidx.room.PrimaryKey val id: String,
    val clientUuid: String,
    val serverId: String? = null,
    val personAId: String,
    val personBId: String,
    val relationType: String,
    val notes: String = "",
    val sourceText: String = "",
    val verificationStatus: String = "unverified_information",
    val visibility: String = "private",
    val syncVersion: Int = 1,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "records", indices = [Index(value = ["clientUuid"], unique = true), Index("occurredAt")])
data class RecordEntity(
    @androidx.room.PrimaryKey val id: String,
    val clientUuid: String,
    val serverId: String? = null,
    val title: String,
    val originalText: String = "",
    val editedText: String = "",
    val recordType: String = "text",
    val occurredAt: Long? = null,
    val datePrecision: String = "unknown",
    val locationText: String = "",
    val personIds: List<String> = emptyList(),
    val visibility: String = "private",
    val sourceType: String = "personal_memory",
    val verificationStatus: String = "unverified_information",
    val syncVersion: Int = 1,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true), Index(value = ["clientUuid"], unique = true)])
data class TagEntity(
    @androidx.room.PrimaryKey val id: String,
    val clientUuid: String,
    val serverId: String? = null,
    val name: String,
    val color: String = "",
    val syncVersion: Int = 1,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "record_tags",
    primaryKeys = ["recordId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordId"), Index("tagId")],
)
data class RecordTagEntity(val recordId: String, val tagId: String)

@Entity(tableName = "pending_sync", indices = [Index(value = ["entityType", "clientUuid"], unique = true)])
data class PendingSyncEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val entityType: String,
    val clientUuid: String,
    val operation: String,
    val payloadJson: String,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @androidx.room.PrimaryKey val key: String = "server",
    val latestServerVersion: Int = 0,
    val lastSyncedAt: Long? = null,
)

@Entity(
    tableName = "media_assets",
    foreignKeys = [
        ForeignKey(entity = RecordEntity::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recordId"), Index(value = ["clientUuid"], unique = true), Index("uploadStatus")],
)
data class MediaEntity(
    @androidx.room.PrimaryKey val id: String,
    val clientUuid: String,
    val recordId: String,
    val serverId: String? = null,
    val mediaType: String,
    val mimeType: String,
    val originalFilename: String,
    val localPath: String,
    val localThumbnailPath: String? = null,
    val sizeBytes: Long,
    val sha256: String,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val uploadStatus: String = "pending",
    val uploadProgress: Int = 0,
    val uploadId: String? = null,
    val uploadedBytes: Long = 0,
    val lastError: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "record_drafts")
data class RecordDraftEntity(
    @androidx.room.PrimaryKey val id: String = "current",
    val title: String = "",
    val originalText: String = "",
    val occurredYear: String = "",
    val locationText: String = "",
    val personIds: List<String> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)
