package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.ThemePreferenceStore
import com.example.notifications.CampusNotificationHelper
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.MainFeatureScreen
import com.example.ui.theme.MecConnectTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    private var pendingNotificationRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CampusNotificationHelper.ensureChannel(this)
        pendingNotificationRoute = extractRouteFromIntent(intent)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val themeStore = remember(context) {
                ThemePreferenceStore(context.applicationContext)
            }
            val isDarkTheme by themeStore.isDarkTheme.collectAsStateWithLifecycle(initialValue = false)
            var userOverrodeTheme by remember { mutableStateOf(false) }

            LaunchedEffect(isDarkTheme) {
                val barStyle = if (isDarkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            MecConnectTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    isDarkTheme = isDarkTheme,
                    userOverrodeTheme = userOverrodeTheme,
                    pendingRoute = pendingNotificationRoute,
                    onConsumePendingRoute = { pendingNotificationRoute = null },
                    onToggleTheme = {
                        userOverrodeTheme = true
                        scope.launch { themeStore.toggleTheme(isDarkTheme) }
                    },
                    onSetTheme = { enabled, fromUser ->
                        if (fromUser) {
                            userOverrodeTheme = true
                        }
                        scope.launch { themeStore.setDarkTheme(enabled) }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractRouteFromIntent(intent)?.let { route ->
            pendingNotificationRoute = route
        }
    }

    private fun extractRouteFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val explicit = intent.getStringExtra(CampusNotificationHelper.EXTRA_NAVIGATE_ROUTE)
        if (!explicit.isNullOrBlank()) return explicit
        val dataRoute = intent.getStringExtra("route")
        if (!dataRoute.isNullOrBlank()) return dataRoute
        val type = intent.getStringExtra("type")?.lowercase()
        return when (type) {
            "event", "event_update" -> "events"
            "schedule" -> "schedule"
            "announcement", "alert" -> "pulse"
            else -> null
        }
    }
}

@Composable
fun AppNavigation(
    isDarkTheme: Boolean = false,
    userOverrodeTheme: Boolean = false,
    pendingRoute: String? = null,
    onConsumePendingRoute: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onSetTheme: (enabled: Boolean, fromUser: Boolean) -> Unit = { _, _ -> },
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            MainFeatureScreen(
                currentUserId = user.uid,
                initialDisplayName = user.displayName ?: "MEC Student",
                userEmail = user.email.orEmpty(),
                isDarkTheme = isDarkTheme,
                userOverrodeTheme = userOverrodeTheme,
                pendingRoute = pendingRoute,
                onConsumePendingRoute = onConsumePendingRoute,
                onToggleTheme = onToggleTheme,
                onSetTheme = onSetTheme
            )
        }
    }
}
