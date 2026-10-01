package com.tasker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tasker.app.data.CustomTheme
import com.tasker.app.data.Settings

@Immutable
data class ThemeSpec(
    val id: String,
    val name: String,
    val dark: Boolean,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val accent: Color,
)

object Themes {
    val presets = listOf(
        // Light
        ThemeSpec("paper", "Paper", false, Color(0xFFE5484D), Color(0xFFFFFFFF), Color(0xFFF8F8F7), Color(0xFF1C1C1E), Color(0xFF3B82F6)),
        ThemeSpec("linen", "Linen", false, Color(0xFFC2703D), Color(0xFFF7F3EC), Color(0xFFFFFDF8), Color(0xFF2B2622), Color(0xFF5E8C61)),
        ThemeSpec("mint", "Mint", false, Color(0xFF2F9E6E), Color(0xFFF3FAF6), Color(0xFFFFFFFF), Color(0xFF1B2A23), Color(0xFF0EA5E9)),
        ThemeSpec("lavender", "Lavender", false, Color(0xFF7B61FF), Color(0xFFF6F4FB), Color(0xFFFFFFFF), Color(0xFF26213A), Color(0xFFEC4899)),
        ThemeSpec("sky", "Sky", false, Color(0xFF2F80ED), Color(0xFFF2F7FC), Color(0xFFFFFFFF), Color(0xFF182433), Color(0xFFF59E0B)),
        ThemeSpec("rose", "Rose", false, Color(0xFFD6457A), Color(0xFFFDF4F6), Color(0xFFFFFFFF), Color(0xFF3A1F27), Color(0xFF8B5CF6)),
        ThemeSpec("solarized_light", "Solarized Light", false, Color(0xFF268BD2), Color(0xFFFDF6E3), Color(0xFFEEE8D5), Color(0xFF586E75), Color(0xFFCB4B16)),
        // Dark
        ThemeSpec("graphite", "Graphite", true, Color(0xFFF2555A), Color(0xFF161618), Color(0xFF1F1F22), Color(0xFFEDEDED), Color(0xFF60A5FA)),
        ThemeSpec("midnight", "Midnight", true, Color(0xFF5B9DFF), Color(0xFF0F1724), Color(0xFF172133), Color(0xFFE6EDF7), Color(0xFFFBBF24)),
        ThemeSpec("amoled", "AMOLED Black", true, Color(0xFF00D1B2), Color(0xFF000000), Color(0xFF0D0D0D), Color(0xFFF2F2F2), Color(0xFFFF6B6B)),
        ThemeSpec("forest", "Forest", true, Color(0xFF4CC38A), Color(0xFF101814), Color(0xFF17221C), Color(0xFFE3EFE7), Color(0xFFE9C46A)),
        ThemeSpec("nord", "Nord", true, Color(0xFF88C0D0), Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFFECEFF4), Color(0xFFB48EAD)),
        ThemeSpec("dracula", "Dracula", true, Color(0xFFBD93F9), Color(0xFF282A36), Color(0xFF303241), Color(0xFFF8F8F2), Color(0xFFFF79C6)),
        ThemeSpec("mocha", "Mocha", true, Color(0xFFE0A458), Color(0xFF1E1A17), Color(0xFF29231F), Color(0xFFEFE6DD), Color(0xFF9CCFD8)),
        ThemeSpec("solarized_dark", "Solarized Dark", true, Color(0xFFB58900), Color(0xFF002B36), Color(0xFF073642), Color(0xFF93A1A1), Color(0xFF2AA198)),
    )

    const val CUSTOM_ID = "custom"

    fun custom(c: CustomTheme) = ThemeSpec(
        CUSTOM_ID, c.name, c.dark, Color(c.primary), Color(c.background), Color(c.surface), Color(c.text), Color(c.accent),
    )

    fun byId(id: String, settings: Settings): ThemeSpec =
        if (id == CUSTOM_ID) custom(settings.customTheme) else presets.firstOrNull { it.id == id } ?: presets.first()

    @Composable
    fun resolve(settings: Settings): ThemeSpec {
        val systemDark = isSystemInDarkTheme()
        return if (settings.followSystem) byId(if (systemDark) settings.darkThemeId else settings.lightThemeId, settings)
        else byId(settings.themeId, settings)
    }
}

private fun on(c: Color): Color = if (c.luminance() > 0.45f) Color(0xFF111111) else Color.White

fun ThemeSpec.colorScheme(): ColorScheme {
    val muted = lerp(text, background, 0.42f)
    val outline = lerp(text, background, 0.72f)
    val outlineVariant = lerp(text, background, 0.86f)
    val primaryContainer = lerp(primary, background, if (dark) 0.70f else 0.82f)
    val accentContainer = lerp(accent, background, if (dark) 0.70f else 0.82f)
    fun tone(f: Float) = lerp(background, text, f)
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = on(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = text,
        secondary = accent, onSecondary = on(accent),
        secondaryContainer = accentContainer, onSecondaryContainer = text,
        tertiary = accent, onTertiary = on(accent),
        tertiaryContainer = accentContainer, onTertiaryContainer = text,
        background = background, onBackground = text,
        surface = background, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = muted,
        surfaceTint = primary,
        inverseSurface = text, inverseOnSurface = background, inversePrimary = primaryContainer,
        outline = outline, outlineVariant = outlineVariant,
        surfaceBright = tone(0.12f), surfaceDim = background,
        surfaceContainerLowest = lerp(background, Color.Black, 0.3f),
        surfaceContainerLow = lerp(background, surface, 0.5f),
        surfaceContainer = surface,
        surfaceContainerHigh = lerp(surface, text, 0.05f),
        surfaceContainerHighest = lerp(surface, text, 0.09f),
        error = Color(0xFFFF6B6B), onError = Color.Black,
    ) else lightColorScheme(
        primary = primary, onPrimary = on(primary),
        primaryContainer = primaryContainer, onPrimaryContainer = text,
        secondary = accent, onSecondary = on(accent),
        secondaryContainer = accentContainer, onSecondaryContainer = text,
        tertiary = accent, onTertiary = on(accent),
        tertiaryContainer = accentContainer, onTertiaryContainer = text,
        background = background, onBackground = text,
        surface = background, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = muted,
        surfaceTint = primary,
        inverseSurface = text, inverseOnSurface = background, inversePrimary = primaryContainer,
        outline = outline, outlineVariant = outlineVariant,
        surfaceBright = background, surfaceDim = tone(0.06f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = lerp(background, surface, 0.5f),
        surfaceContainer = surface,
        surfaceContainerHigh = lerp(surface, text, 0.04f),
        surfaceContainerHighest = lerp(surface, text, 0.07f),
        error = Color(0xFFD92D20), onError = Color.White,
    )
}

/** Extra semantic colours not covered by Material's scheme. */
@Immutable
data class TaskerColors(
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
    val overdue: Color,
    val top: Color,
    val dark: Boolean,
)

val LocalTaskerColors = staticCompositionLocalOf {
    TaskerColors(Color.Red, Color(0xFFF59E0B), Color(0xFF3B82F6), Color.Red, Color(0xFFF5B301), false)
}

private val TaskerTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp),
    )
}

@Composable
fun TaskerTheme(spec: ThemeSpec, content: @Composable () -> Unit) {
    val colors = TaskerColors(
        priorityHigh = if (spec.dark) Color(0xFFFF6B6B) else Color(0xFFE5484D),
        priorityMedium = if (spec.dark) Color(0xFFFFB547) else Color(0xFFF08C00),
        priorityLow = if (spec.dark) Color(0xFF6CB4FF) else Color(0xFF3B82F6),
        overdue = if (spec.dark) Color(0xFFFF6B6B) else Color(0xFFD92D20),
        top = Color(0xFFF5B301),
        dark = spec.dark,
    )
    CompositionLocalProvider(LocalTaskerColors provides colors) {
        MaterialTheme(colorScheme = spec.colorScheme(), typography = TaskerTypography, content = content)
    }
}
