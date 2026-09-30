@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.omabit.tend

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Public palette vocabulary matches the proposed shared Omarchy settings contract verbatim. */
@Serializable
data class OmarchyPalette(
    val mode: String = "dark",
    val colors: Map<String, String> = defaultColors,
) {
    fun color(name: String): Color =
        parseColor(colors[name]) ?: parseColor(defaultColors.getValue(name))!!

    companion object {
        val defaultColors =
            mapOf(
                "accent" to "#c5ccd6",
                "selection" to "#303238",
                "muted" to "#8e8e87",
                "background" to "#0c0c0d",
                "dark_background" to "#141416",
                "darker_background" to "#09090a",
                "lighter_background" to "#1c1c1f",
                "foreground" to "#ecece8",
                "dark_foreground" to "#8e8e87",
                "light_foreground" to "#f3f3ef",
                "bright_foreground" to "#ffffff",
                "red" to "#c45c4a",
                "yellow" to "#cbb679",
                "orange" to "#c28f68",
                "green" to "#7d9a7a",
                "cyan" to "#83aaa7",
                "blue" to "#8ba5c6",
                "magenta" to "#b393b9",
                "brown" to "#9d8775",
                "bright_red" to "#e18a7c",
                "bright_yellow" to "#e0cc95",
                "bright_green" to "#a4bea0",
                "bright_cyan" to "#afd1ca",
                "bright_blue" to "#b7cbe5",
                "bright_magenta" to "#d2b5d7",
            )

        fun fromJson(value: JsonObject): OmarchyPalette =
            normalizedPalette((value["mode"] as? JsonPrimitive)?.contentOrNull ?: "dark", value)
    }
}

fun parseColor(value: String?): Color? =
    value
        ?.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }
        ?.substring(1)
        ?.toLongOrNull(16)
        ?.let { Color(0xff000000L or it) }

@Composable
fun TendTheme(palette: OmarchyPalette = OmarchyPalette(), content: @Composable () -> Unit) {
    val c = palette::color
    val base = if (palette.mode == "light") lightColorScheme() else darkColorScheme()
    val scheme =
        base.copy(
            primary = c("accent"),
            onPrimary = parseColor(inkFor(palette.colors.getValue("accent")))!!,
            primaryContainer = c("selection"),
            onPrimaryContainer = parseColor(inkFor(palette.colors.getValue("selection")))!!,
            secondary = c("muted"),
            onSecondary = parseColor(inkFor(palette.colors.getValue("muted")))!!,
            secondaryContainer = c("selection"),
            onSecondaryContainer = parseColor(inkFor(palette.colors.getValue("selection")))!!,
            tertiary = c("blue"),
            onTertiary = parseColor(inkFor(palette.colors.getValue("blue")))!!,
            tertiaryContainer = c("selection"),
            onTertiaryContainer = parseColor(inkFor(palette.colors.getValue("selection")))!!,
            background = c("background"),
            onBackground = c("foreground"),
            surface = c("dark_background"),
            onSurface = c("foreground"),
            surfaceVariant = c("lighter_background"),
            onSurfaceVariant = c("muted"),
            surfaceContainer = c("dark_background"),
            surfaceContainerLow = c("dark_background"),
            surfaceContainerHigh = c("lighter_background"),
            surfaceContainerHighest = c("selection"),
            surfaceContainerLowest = c("darker_background"),
            surfaceTint = Color.Transparent,
            outline = c("selection"),
            outlineVariant =
                parseColor(
                    mixHex(
                        palette.colors.getValue("background"),
                        palette.colors.getValue("foreground"),
                        .18,
                    )
                )!!,
            error = c("red"),
            onError = parseColor(inkFor(palette.colors.getValue("red")))!!,
            errorContainer = c("red").copy(alpha = .15f),
            onErrorContainer = c("foreground"),
            inverseSurface = c("foreground"),
            inverseOnSurface = c("background"),
            inversePrimary = c("background"),
        )
    val type =
        Typography(
            displaySmall =
                TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 38.sp,
                    lineHeight = 43.sp,
                    fontWeight = FontWeight.Medium,
                ),
            headlineLarge =
                TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 38.sp),
            headlineMedium =
                TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge =
                TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Medium),
            titleMedium =
                TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
            labelSmall =
                TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 1.sp,
                ),
        )
    CompositionLocalProvider(LocalTendPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = type, content = content)
    }
}

val LocalTendPalette = staticCompositionLocalOf { OmarchyPalette() }

/** Android sheets own a separate window; the host supplies its system-bar policy. */
val LocalSheetAppearance =
    staticCompositionLocalOf<(Boolean) -> ModalBottomSheetProperties> {
        { ModalBottomSheetProperties() }
    }

@Composable
fun tendSheetProperties(): ModalBottomSheetProperties =
    LocalSheetAppearance.current(
        luminance(LocalTendPalette.current.colors.getValue("dark_background")) > .179
    )

val LocalSheetSystemBars = staticCompositionLocalOf<@Composable (Boolean) -> Unit> { {} }

@Composable
fun TendSheetSystemBars() {
    LocalSheetSystemBars.current(
        luminance(LocalTendPalette.current.colors.getValue("dark_background")) > .179
    )
}
