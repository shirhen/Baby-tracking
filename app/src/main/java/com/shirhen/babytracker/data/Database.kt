package com.shirhen.babytracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE deleted = 0 ORDER BY time DESC")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE deleted = 0 AND time >= :from ORDER BY time DESC")
    suspend fun since(from: Long): List<Event>

    @Query("SELECT * FROM events WHERE deleted = 0 AND type = :type ORDER BY time DESC LIMIT 1")
    suspend fun last(type: EventType): Event?

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun byId(id: String): Event?

    @Query("SELECT * FROM events")
    suspend fun allIncludingDeleted(): List<Event>

    @Upsert
    suspend fun upsert(event: Event)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights WHERE deleted = 0 ORDER BY time DESC")
    fun observeAll(): Flow<List<Weight>>

    @Query("SELECT * FROM weights WHERE deleted = 0 ORDER BY time DESC LIMIT 1")
    suspend fun latest(): Weight?

    @Query("SELECT * FROM weights WHERE id = :id")
    suspend fun byId(id: String): Weight?

    @Query("SELECT * FROM weights")
    suspend fun allIncludingDeleted(): List<Weight>

    @Upsert
    suspend fun upsert(weight: Weight)
}

@Dao
interface MedDao {
    @Query("SELECT * FROM meds WHERE deleted = 0 ORDER BY active DESC, name")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM meds WHERE deleted = 0 AND active = 1 ORDER BY name")
    suspend fun active(): List<Medication>

    @Query("SELECT * FROM meds WHERE id = :id")
    suspend fun byId(id: String): Medication?

    @Query("SELECT * FROM meds")
    suspend fun allIncludingDeleted(): List<Medication>

    @Upsert
    suspend fun upsert(med: Medication)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<BabyProfile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): BabyProfile?

    @Upsert
    suspend fun upsert(profile: BabyProfile)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN breastTotalMin INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN breastSides TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN medId TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN medName TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN checkScope TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN cryLevel INTEGER")
        db.execSQL("ALTER TABLE events ADD COLUMN alertLevel INTEGER")
        db.execSQL("ALTER TABLE events ADD COLUMN gasLevel INTEGER")
        db.execSQL("ALTER TABLE events ADD COLUMN tummyMin INTEGER")
        db.execSQL("ALTER TABLE events ADD COLUMN symptoms TEXT")
        db.execSQL("ALTER TABLE events ADD COLUMN tempC REAL")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS meds (id TEXT NOT NULL, name TEXT NOT NULL, dose TEXT NOT NULL, " +
                "times TEXT NOT NULL, active INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deleted INTEGER NOT NULL, " +
                "PRIMARY KEY(id))",
        )
    }
}

@Database(entities = [Event::class, Weight::class, BabyProfile::class, Medication::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun weights(): WeightDao
    abstract fun profile(): ProfileDao
    abstract fun meds(): MedDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "baby.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
