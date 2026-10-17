package com.shirhen.babytracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
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
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<BabyProfile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): BabyProfile?

    @Upsert
    suspend fun upsert(profile: BabyProfile)
}

@Database(entities = [Event::class, Weight::class, BabyProfile::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun weights(): WeightDao
    abstract fun profile(): ProfileDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "baby.db")
                    .build().also { instance = it }
            }
    }
}
