package studio.nexora.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectDatabaseTest {
    @Test
    fun savesUpdatesListsAndDeletesProjects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("nexora_projects.db")
        val database = ProjectDatabase(context)
        try {
            val id = database.save(null, "Demo", """{"clips":2}""", 100L)
            assertTrue(id > 0L)
            assertEquals("Demo", database.list().single().name)
            database.save(id, "Demo Updated", """{"clips":3}""", 200L)
            val saved = database.list().single()
            assertEquals(id, saved.id)
            assertEquals("Demo Updated", saved.name)
            assertEquals("""{"clips":3}""", saved.document)
            assertEquals(200L, saved.updatedAt)
            assertTrue(database.delete(id))
            assertTrue(database.list().isEmpty())
        } finally {
            database.close()
            context.deleteDatabase("nexora_projects.db")
        }
    }
}
