package app.safevault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EntryEntity::class, VaultMetaEntity::class], version = 1, exportSchema = true)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao

    companion object {
        fun open(context: Context, name: String = "safevault.db"): VaultDatabase =
            Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, name)
                .addCallback(object : Callback() {
                    // Overwrite deleted content with zeros instead of leaving it in free pages.
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").close()
                    }
                })
                .build()
    }
}
