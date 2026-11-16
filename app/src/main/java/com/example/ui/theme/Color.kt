package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Campus Palette ("Blossom White & Pink" - Default)
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFFFF1F6)
val LightSurfaceHigh = Color(0xFFFFE4EE)
val LightOutline = Color(0xFFFBCFE8)

val LightPrimary = Color(0xFFDB2777)       // Vibrant Pink
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFFCE7F3)
val LightOnPrimaryContainer = Color(0xFF831843)

val LightSecondary = Color(0xFFEC4899)     // Rose Pink
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFDF2F8)
val LightOnSecondaryContainer = Color(0xFF9D174D)

val LightTertiary = Color(0xFFBE185D)      // Deep Berry Pink
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFE4E6)
val LightOnTertiaryContainer = Color(0xFF881337)

val LightError = Color(0xFFE11D48)
val LightOnError = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1F1219)
val LightOnSurfaceVariant = Color(0xFF6B4C5B)

// Dark Campus Palette ("Midnight Rose")
val DarkBackground = Color(0xFF13090F)
val DarkSurface = Color(0xFF1D0F18)
val DarkSurfaceVariant = Color(0xFF2B1723)
val DarkSurfaceHigh = Color(0xFF3A1E2F)
val DarkOutline = Color(0xFF5A3148)

val DarkPrimary = Color(0xFFF472B6)        // Soft Luminous Pink
val DarkOnPrimary = Color(0xFF3E0824)
val DarkPrimaryContainer = Color(0xFF831843)
val DarkOnPrimaryContainer = Color(0xFFFCE7F3)

val DarkSecondary = Color(0xFFFB7185)      // Coral Rose
val DarkOnSecondary = Color(0xFF420816)
val DarkSecondaryContainer = Color(0xFF881337)
val DarkOnSecondaryContainer = Color(0xFFFFE4E6)

val DarkTertiary = Color(0xFFF9A8D4)       // Blush Pink Highlight
val DarkOnTertiary = Color(0xFF450B29)
val DarkTertiaryContainer = Color(0xFF701A43)
val DarkOnTertiaryContainer = Color(0xFFFDF2F8)

val DarkError = Color(0xFFFB7185)
val DarkOnError = Color(0xFF3F0614)
val DarkOnSurface = Color(0xFFFDF2F8)
val DarkOnSurfaceVariant = Color(0xFFD6B4C6)

// Department semantic accent colors (Dark & Light contrast-tuned)
val DeptCseAccent = Color(0xFFF472B6)
val DeptEceAccent = Color(0xFFC084FC)
val DeptEeeAccent = Color(0xFFFBBF24)
val DeptBmeAccent = Color(0xFF2DD4BF)
val DeptMeAccent = Color(0xFFFB7185)

val LightDeptCseAccent = Color(0xFFDB2777)
val LightDeptEceAccent = Color(0xFF9333EA)
val LightDeptEeeAccent = Color(0xFFB45309)
val LightDeptBmeAccent = Color(0xFF0F766E)
val LightDeptMeAccent = Color(0xFFBE123C)

@Composable
fun departmentAccentColor(department: String): Color {
    val isDark = LocalIsDarkTheme.current
    return when (department.uppercase()) {
        "CSE" -> if (isDark) DeptCseAccent else LightDeptCseAccent
        "ECE" -> if (isDark) DeptEceAccent else LightDeptEceAccent
        "EEE" -> if (isDark) DeptEeeAccent else LightDeptEeeAccent
        "BME" -> if (isDark) DeptBmeAccent else LightDeptBmeAccent
        "ME" -> if (isDark) DeptMeAccent else LightDeptMeAccent
        else -> if (isDark) DarkPrimary else LightPrimary
    }
}
