package cn.jiayi.familymemory.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PersonEntity::class,
        RelationshipEntity::class,
        RecordEntity::class,
        TagEntity::class,
        RecordTagEntity::class,
        PendingSyncEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FamilyDatabase : RoomDatabase() {
    abstract fun coreDao(): CoreDao
}
