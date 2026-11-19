package studio.nexora.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun launchesAndShowsEditorHome() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("NEXORA STUDIO")).check(matches(isDisplayed()))
            onView(withText("Workspace")).check(matches(isDisplayed()))
            onView(withText("Timeline")).check(matches(isDisplayed()))
        }
    }
}
