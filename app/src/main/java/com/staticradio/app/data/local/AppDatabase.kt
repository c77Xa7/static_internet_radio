package com.staticradio.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

private val DEFAULT_GENRES = listOf("pop", "electronic", "techno", "dub", "ambient", "breakbeat", "UK garage", "jungle")
private val DEFAULT_MOODS = listOf("energetic", "heavy", "crowd safe", "calm", "educational")
private val DEFAULT_STYLES = listOf("DJ set", "Radio show", "Talk Show")

@Database(
    entities = [
        StationEntity::class, TagEntity::class, StationTagCrossRef::class,
        MixEntity::class, MixTrackEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun mixDao(): MixDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "static-radio.db"
                )
                    // No real user data to preserve yet — this drops the placeholder
                    // seed stations on upgrade instead of writing a throwaway migration.
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        // Runs once, only on a brand-new (or destructively-recreated) database —
                        // seeds the Genre/Mood/Style vocabularies with sensible defaults instead
                        // of leaving Add/Edit Station pickers empty on first run.
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            fun seed(names: List<String>, type: String) {
                                names.forEach { name ->
                                    db.execSQL(
                                        "INSERT OR IGNORE INTO tags (name, type) VALUES (?, ?)",
                                        arrayOf(name, type)
                                    )
                                }
                            }
                            seed(DEFAULT_GENRES, "GENRE")
                            seed(DEFAULT_MOODS, "MOOD")
                            seed(DEFAULT_STYLES, "STYLE")
                        }
                    })
                    .build()
                    .also { instance = it }
            }
    }
}
