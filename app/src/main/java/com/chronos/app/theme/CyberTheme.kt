package com.chronos.app.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeAppearanceMode(
    val id: String,
    val displayName: String,
    val nightModeFlag: Int
) {
    NIGHT("night", "Modo Noche", AppCompatDelegate.MODE_NIGHT_YES),
    DAY("day", "Modo Día", AppCompatDelegate.MODE_NIGHT_NO),
    SYSTEM("system", "Automático", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    companion object {
        fun fromId(id: String?): ThemeAppearanceMode {
            return entries.firstOrNull { it.id == id } ?: NIGHT
        }
    }
}

data class ResolvedCyberPalette(
    val themeId: CyberThemeId,
    val isDark: Boolean,
    val backgroundColor: Int,
    val surfaceColor: Int,
    val glassSurfaceColor: Int,
    val surfaceElevatedColor: Int,
    val rimHighlightColor: Int,
    val subtleBorderColor: Int,
    val capsuleFillColor: Int,
    val primaryNeon: Int,
    val secondaryNeon: Int,
    val tertiaryNeon: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    val onPrimaryColor: Int
)

private data class PaletteSpec(
    val isDark: Boolean,
    val backgroundColor: Int,
    val surfaceColor: Int,
    val glassSurfaceColor: Int,
    val surfaceElevatedColor: Int,
    val rimHighlightColor: Int,
    val subtleBorderColor: Int,
    val capsuleFillColor: Int,
    val primaryNeon: Int,
    val secondaryNeon: Int,
    val tertiaryNeon: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    val onPrimaryColor: Int
) {
    fun toResolved(themeId: CyberThemeId): ResolvedCyberPalette {
        return ResolvedCyberPalette(
            themeId = themeId,
            isDark = isDark,
            backgroundColor = backgroundColor,
            surfaceColor = surfaceColor,
            glassSurfaceColor = glassSurfaceColor,
            surfaceElevatedColor = surfaceElevatedColor,
            rimHighlightColor = rimHighlightColor,
            subtleBorderColor = subtleBorderColor,
            capsuleFillColor = capsuleFillColor,
            primaryNeon = primaryNeon,
            secondaryNeon = secondaryNeon,
            tertiaryNeon = tertiaryNeon,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            onPrimaryColor = onPrimaryColor
        )
    }
}

enum class CyberThemeId(
    val id: String,
    val displayName: String,
    private val darkSpec: PaletteSpec,
    private val daySpec: PaletteSpec
) {
    CYBERPUNK_CLASSIC(
        id = "cyberpunk_classic",
        displayName = "Cyberpunk Cian",
        darkSpec = PaletteSpec(
            isDark = true,
            backgroundColor = 0xFF0A0A12.toInt(),
            surfaceColor = 0xFF12121E.toInt(),
            glassSurfaceColor = 0xD912121E.toInt(),
            surfaceElevatedColor = 0xFF1C2036.toInt(),
            rimHighlightColor = 0x5500F0FF.toInt(),
            subtleBorderColor = 0x33FFFFFF.toInt(),
            capsuleFillColor = 0x261C223B.toInt(),
            primaryNeon = 0xFF00F0FF.toInt(),
            secondaryNeon = 0xFFFF0055.toInt(),
            tertiaryNeon = 0xFFFFB800.toInt(),
            textPrimary = 0xFFF4F8FF.toInt(),
            textSecondary = 0xFFA6B3D1.toInt(),
            textMuted = 0xFF657091.toInt(),
            onPrimaryColor = 0xFF0A0A12.toInt()
        ),
        daySpec = PaletteSpec(
            isDark = false,
            backgroundColor = 0xFFF2F4F8.toInt(),
            surfaceColor = 0xFFFFFFFF.toInt(),
            glassSurfaceColor = 0xEBFFFFFF.toInt(),
            surfaceElevatedColor = 0xFFE4E9F2.toInt(),
            rimHighlightColor = 0x59007ACC.toInt(),
            subtleBorderColor = 0x241A1C23.toInt(),
            capsuleFillColor = 0x141A1C23.toInt(),
            primaryNeon = 0xFF007ACC.toInt(),
            secondaryNeon = 0xFFD60044.toInt(),
            tertiaryNeon = 0xFFD97706.toInt(),
            textPrimary = 0xFF1A1C23.toInt(),
            textSecondary = 0xFF4A5162.toInt(),
            textMuted = 0xFF737B8F.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        )
    ),
    HARMONY_AURORA(
        id = "harmony_aurora",
        displayName = "Harmony Aurora",
        darkSpec = PaletteSpec(
            isDark = true,
            backgroundColor = 0xFF0A0A12.toInt(),
            surfaceColor = 0xFF12121E.toInt(),
            glassSurfaceColor = 0xD9131C30.toInt(),
            surfaceElevatedColor = 0xFF1B2640.toInt(),
            rimHighlightColor = 0x592B8CFF.toInt(),
            subtleBorderColor = 0x33FFFFFF.toInt(),
            capsuleFillColor = 0x261B2640.toInt(),
            primaryNeon = 0xFF2B8CFF.toInt(),
            secondaryNeon = 0xFFB54BFF.toInt(),
            tertiaryNeon = 0xFF00F5C4.toInt(),
            textPrimary = 0xFFF5F8FF.toInt(),
            textSecondary = 0xFF9FB2D6.toInt(),
            textMuted = 0xFF647499.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        ),
        daySpec = PaletteSpec(
            isDark = false,
            backgroundColor = 0xFFF2F4F8.toInt(),
            surfaceColor = 0xFFFFFFFF.toInt(),
            glassSurfaceColor = 0xEBFFFFFF.toInt(),
            surfaceElevatedColor = 0xFFE3EAF7.toInt(),
            rimHighlightColor = 0x590A66E6.toInt(),
            subtleBorderColor = 0x241A1C23.toInt(),
            capsuleFillColor = 0x140A66E6.toInt(),
            primaryNeon = 0xFF0A66E6.toInt(),
            secondaryNeon = 0xFF8E24AA.toInt(),
            tertiaryNeon = 0xFF00897B.toInt(),
            textPrimary = 0xFF1A1C23.toInt(),
            textSecondary = 0xFF4A5162.toInt(),
            textMuted = 0xFF737B8F.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        )
    ),
    MATRIX_PROTOCOL(
        id = "matrix_protocol",
        displayName = "Matrix Verde",
        darkSpec = PaletteSpec(
            isDark = true,
            backgroundColor = 0xFF050D08.toInt(),
            surfaceColor = 0xFF0C1911.toInt(),
            glassSurfaceColor = 0xD90F2117.toInt(),
            surfaceElevatedColor = 0xFF13261B.toInt(),
            rimHighlightColor = 0x4D00FF66.toInt(),
            subtleBorderColor = 0x33FFFFFF.toInt(),
            capsuleFillColor = 0x2613261B.toInt(),
            primaryNeon = 0xFF00FF66.toInt(),
            secondaryNeon = 0xFF00E5FF.toInt(),
            tertiaryNeon = 0xFFA3FF00.toInt(),
            textPrimary = 0xFFECFFF2.toInt(),
            textSecondary = 0xFF8AB89B.toInt(),
            textMuted = 0xFF557D63.toInt(),
            onPrimaryColor = 0xFF050D08.toInt()
        ),
        daySpec = PaletteSpec(
            isDark = false,
            backgroundColor = 0xFFF2F6F3.toInt(),
            surfaceColor = 0xFFFFFFFF.toInt(),
            glassSurfaceColor = 0xEBFFFFFF.toInt(),
            surfaceElevatedColor = 0xFFE1EDE5.toInt(),
            rimHighlightColor = 0x59008A3E.toInt(),
            subtleBorderColor = 0x241A1C23.toInt(),
            capsuleFillColor = 0x14008A3E.toInt(),
            primaryNeon = 0xFF008A3E.toInt(),
            secondaryNeon = 0xFF007ACC.toInt(),
            tertiaryNeon = 0xFF4D7C0F.toInt(),
            textPrimary = 0xFF1A1C23.toInt(),
            textSecondary = 0xFF415246.toInt(),
            textMuted = 0xFF6A7D70.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        )
    ),
    SYNTHWAVE_OUTRUN(
        id = "synthwave_outrun",
        displayName = "Synthwave Oro",
        darkSpec = PaletteSpec(
            isDark = true,
            backgroundColor = 0xFF0D0614.toInt(),
            surfaceColor = 0xFF180C24.toInt(),
            glassSurfaceColor = 0xD91F102E.toInt(),
            surfaceElevatedColor = 0xFF241236.toInt(),
            rimHighlightColor = 0x55FF2A85.toInt(),
            subtleBorderColor = 0x33FFFFFF.toInt(),
            capsuleFillColor = 0x26241236.toInt(),
            primaryNeon = 0xFFFF2A85.toInt(),
            secondaryNeon = 0xFFFFAA00.toInt(),
            tertiaryNeon = 0xFF00F0FF.toInt(),
            textPrimary = 0xFFFFF0FA.toInt(),
            textSecondary = 0xFFC59EC4.toInt(),
            textMuted = 0xFF7D5B80.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        ),
        daySpec = PaletteSpec(
            isDark = false,
            backgroundColor = 0xFFF6F2F8.toInt(),
            surfaceColor = 0xFFFFFFFF.toInt(),
            glassSurfaceColor = 0xEBFFFFFF.toInt(),
            surfaceElevatedColor = 0xFFEFE4F3.toInt(),
            rimHighlightColor = 0x59C2185B.toInt(),
            subtleBorderColor = 0x241A1C23.toInt(),
            capsuleFillColor = 0x14C2185B.toInt(),
            primaryNeon = 0xFFC2185B.toInt(),
            secondaryNeon = 0xFFD97706.toInt(),
            tertiaryNeon = 0xFF007ACC.toInt(),
            textPrimary = 0xFF1A1C23.toInt(),
            textSecondary = 0xFF54455E.toInt(),
            textMuted = 0xFF7D6B8A.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        )
    ),
    GHOST_CRIMSON(
        id = "ghost_crimson",
        displayName = "Ghost Carmesí",
        darkSpec = PaletteSpec(
            isDark = true,
            backgroundColor = 0xFF0C080A.toInt(),
            surfaceColor = 0xFF181014.toInt(),
            glassSurfaceColor = 0xD920141A.toInt(),
            surfaceElevatedColor = 0xFF26181F.toInt(),
            rimHighlightColor = 0x55FF1E42.toInt(),
            subtleBorderColor = 0x33FFFFFF.toInt(),
            capsuleFillColor = 0x2626181F.toInt(),
            primaryNeon = 0xFFFF1E42.toInt(),
            secondaryNeon = 0xFFA855F7.toInt(),
            tertiaryNeon = 0xFFFF8800.toInt(),
            textPrimary = 0xFFFFF2F4.toInt(),
            textSecondary = 0xFFBFA0A8.toInt(),
            textMuted = 0xFF7D6068.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        ),
        daySpec = PaletteSpec(
            isDark = false,
            backgroundColor = 0xFFF7F2F3.toInt(),
            surfaceColor = 0xFFFFFFFF.toInt(),
            glassSurfaceColor = 0xEBFFFFFF.toInt(),
            surfaceElevatedColor = 0xFFF2E4E7.toInt(),
            rimHighlightColor = 0x59C8102E.toInt(),
            subtleBorderColor = 0x241A1C23.toInt(),
            capsuleFillColor = 0x14C8102E.toInt(),
            primaryNeon = 0xFFC8102E.toInt(),
            secondaryNeon = 0xFF7E22CE.toInt(),
            tertiaryNeon = 0xFFC2410C.toInt(),
            textPrimary = 0xFF1A1C23.toInt(),
            textSecondary = 0xFF5B474B.toInt(),
            textMuted = 0xFF856D72.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        )
    ),
    ARCTIC_NEURAL(
        id = "arctic_neural",
        displayName = "Ártico Neural",
        darkSpec = PaletteSpec(
            isDark = true,
            backgroundColor = 0xFF070B14.toInt(),
            surfaceColor = 0xFF0F172A.toInt(),
            glassSurfaceColor = 0xD9141F36.toInt(),
            surfaceElevatedColor = 0xFF1A2642.toInt(),
            rimHighlightColor = 0x5538BDF8.toInt(),
            subtleBorderColor = 0x33FFFFFF.toInt(),
            capsuleFillColor = 0x261A2642.toInt(),
            primaryNeon = 0xFF38BDF8.toInt(),
            secondaryNeon = 0xFFF472B6.toInt(),
            tertiaryNeon = 0xFF2DD4BF.toInt(),
            textPrimary = 0xFFF1F5F9.toInt(),
            textSecondary = 0xFF94A3B8.toInt(),
            textMuted = 0xFF64748B.toInt(),
            onPrimaryColor = 0xFF070B14.toInt()
        ),
        daySpec = PaletteSpec(
            isDark = false,
            backgroundColor = 0xFFF2F4F8.toInt(),
            surfaceColor = 0xFFFFFFFF.toInt(),
            glassSurfaceColor = 0xEBFFFFFF.toInt(),
            surfaceElevatedColor = 0xFFE2ECF5.toInt(),
            rimHighlightColor = 0x590284C7.toInt(),
            subtleBorderColor = 0x241A1C23.toInt(),
            capsuleFillColor = 0x140284C7.toInt(),
            primaryNeon = 0xFF0284C7.toInt(),
            secondaryNeon = 0xFFDB2777.toInt(),
            tertiaryNeon = 0xFF0D9488.toInt(),
            textPrimary = 0xFF1A1C23.toInt(),
            textSecondary = 0xFF475569.toInt(),
            textMuted = 0xFF64748B.toInt(),
            onPrimaryColor = 0xFFFFFFFF.toInt()
        )
    );

    fun resolve(isDark: Boolean): ResolvedCyberPalette {
        return (if (isDark) darkSpec else daySpec).toResolved(this)
    }

    companion object {
        fun fromId(id: String?): CyberThemeId {
            return entries.firstOrNull { it.id == id } ?: CYBERPUNK_CLASSIC
        }
    }
}

class ChronosThemeManager(private val appContext: Context) {
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentTheme = MutableStateFlow(
        CyberThemeId.fromId(prefs.getString(KEY_THEME_ID, CyberThemeId.CYBERPUNK_CLASSIC.id))
    )
    val currentTheme: StateFlow<CyberThemeId> = _currentTheme.asStateFlow()

    private val _appearanceMode = MutableStateFlow(
        ThemeAppearanceMode.fromId(prefs.getString(KEY_APPEARANCE_MODE, ThemeAppearanceMode.NIGHT.id))
    )
    val appearanceMode: StateFlow<ThemeAppearanceMode> = _appearanceMode.asStateFlow()

    private val _resolvedPalette = MutableStateFlow(resolveNow(appContext))
    val resolvedPalette: StateFlow<ResolvedCyberPalette> = _resolvedPalette.asStateFlow()

    fun applySavedNightMode() {
        AppCompatDelegate.setDefaultNightMode(_appearanceMode.value.nightModeFlag)
        refreshResolvedPalette(appContext)
    }

    fun setTheme(themeId: CyberThemeId) {
        prefs.edit().putString(KEY_THEME_ID, themeId.id).apply()
        _currentTheme.value = themeId
        refreshResolvedPalette(appContext)
    }

    fun setAppearanceMode(mode: ThemeAppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE_MODE, mode.id).apply()
        _appearanceMode.value = mode
        AppCompatDelegate.setDefaultNightMode(mode.nightModeFlag)
        refreshResolvedPalette(appContext)
    }

    fun refreshResolvedPalette(context: Context = appContext) {
        _resolvedPalette.value = resolveNow(context)
    }

    private fun resolveNow(context: Context): ResolvedCyberPalette {
        val isDark = when (_appearanceMode.value) {
            ThemeAppearanceMode.NIGHT -> true
            ThemeAppearanceMode.DAY -> false
            ThemeAppearanceMode.SYSTEM -> {
                val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
        }
        return _currentTheme.value.resolve(isDark)
    }

    companion object {
        private const val PREFS_NAME = "chronos_theme_prefs"
        private const val KEY_THEME_ID = "selected_theme_id"
        private const val KEY_APPEARANCE_MODE = "selected_appearance_mode"
    }
}
