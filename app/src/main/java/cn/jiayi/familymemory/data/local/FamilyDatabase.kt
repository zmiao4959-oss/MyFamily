package cn.jiayi.familymemory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PersonEntity::class,
        RelationshipEntity::class,
        RecordEntity::class,
        TagEntity::class,
        RecordTagEntity::class,
        PendingSyncEntity::class,
        SyncStateEntity::class,
        MediaEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FamilyDatabase : RoomDatabase() {
    abstract fun coreDao(): CoreDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS media_assets (
                        id TEXT NOT NULL PRIMARY KEY,
                        clientUuid TEXT NOT NULL,
                        recordId TEXT NOT NULL,
                        serverId TEXT,
                        mediaType TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        originalFilename TEXT NOT NULL,
                        localPath TEXT NOT NULL,
                        localThumbnailPath TEXT,
                        sizeBytes INTEGER NOT NULL,
                        sha256 TEXT NOT NULL,
                        durationMs INTEGER,
                        width INTEGER,
                        height INTEGER,
                        uploadStatus TEXT NOT NULL,
                        uploadProgress INTEGER NOT NULL,
                        uploadId TEXT,
                        uploadedBytes INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(recordId) REFERENCES records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_media_assets_clientUuid ON media_assets(clientUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_assets_recordId ON media_assets(recordId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_assets_uploadStatus ON media_assets(uploadStatus)")
            }
        }
    }
}
