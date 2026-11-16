package com.example.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ThemePreferenceStoreTest {

    @Test
    fun themePreference_defaultsLightAndTogglesBetweenLightAndDark() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ThemePreferenceStore(context)

        // Default Blossom White & Pink light mode
        assertFalse(store.isDarkTheme.first())

        // Switch to Dark mode
        store.toggleTheme(currentDarkTheme = false)
        assertTrue(store.isDarkTheme.first())

        // Switch back to Light mode
        store.toggleTheme(currentDarkTheme = true)
        assertFalse(store.isDarkTheme.first())

        // Explicit setter
        store.setDarkTheme(true)
        assertTrue(store.isDarkTheme.first())
    }
}
