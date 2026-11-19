package studio.nexora.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedProject(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val document: String
)

/** Small built-in SQLite store: no ORM, network, or extra runtime dependency. */
class ProjectDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "nexora_projects.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE projects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                document TEXT NOT NULL DEFAULT ''
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX projects_updated_idx ON projects(updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add forward-only migrations here; never discard existing user projects.
    }

    fun save(id: Long?, name: String, document: String, now: Long = System.currentTimeMillis()): Long {
        val values = ContentValues().apply {
            put("name", name.ifBlank { "Untitled project" })
            put("updated_at", now)
            put("document", document)
        }
        val db = writableDatabase
        if (id != null && id > 0L) {
            val changed = db.update("projects", values, "id=?", arrayOf(id.toString()))
            if (changed > 0) return id
        }
        return db.insertOrThrow("projects", null, values)
    }

    fun list(): List<SavedProject> {
        val result = ArrayList<SavedProject>()
        readableDatabase.query(
            "projects", arrayOf("id", "name", "updated_at", "document"),
            null, null, null, null, "updated_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SavedProject(
                    cursor.getLong(0), cursor.getString(1),
                    cursor.getLong(2), cursor.getString(3)
                )
            }
        }
        return result
    }

    fun delete(id: Long): Boolean =
        writableDatabase.delete("projects", "id=?", arrayOf(id.toString())) > 0
}
