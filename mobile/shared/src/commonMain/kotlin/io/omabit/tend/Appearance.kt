package io.omabit.tend

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Client-only, cached with the account. No theme preference is written to Gall. */
@Serializable
data class AppearanceState(val followShip: Boolean = true, val shipTheme: ShipTheme? = null) {
    val palette: OmarchyPalette
        get() = if (followShip) shipTheme?.palette ?: OmarchyPalette() else OmarchyPalette()

    val description: String
        get() =
            if (!followShip) "Tend default · this device"
            else
                shipTheme?.let { "${it.name} · ${it.palette.mode} · ${it.source}" }
                    ?: "Tend default · no ship theme"
}

@Serializable
data class ShipTheme(val name: String, val source: String, val palette: OmarchyPalette)

private fun JsonElement?.text() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun hex(value: JsonElement?) =
    value.text()?.takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) }?.lowercase()

fun mixHex(a: String, b: String, amount: Double): String =
    "#" +
        (1..5 step 2).joinToString("") { i ->
            (a.substring(i, i + 2).toInt(16) * (1 - amount) +
                    b.substring(i, i + 2).toInt(16) * amount)
                .roundToInt()
                .coerceIn(0, 255)
                .toString(16)
                .padStart(2, '0')
        }

fun luminance(hex: String): Double =
    (1..5 step 2)
        .map { i ->
            val v = hex.substring(i, i + 2).toInt(16) / 255.0
            if (v <= .04045) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        }
        .let { it[0] * .2126 + it[1] * .7152 + it[2] * .0722 }

fun inkFor(background: String): String = if (luminance(background) > .179) "#000000" else "#ffffff"

/** Derive missing light surfaces from the supplied palette, never from dark defaults. */
fun normalizedPalette(mode: String, values: JsonObject): OmarchyPalette {
    require(mode in setOf("light", "dark")) { "Unsupported appearance mode" }
    val bg = hex(values["background"]) ?: error("Missing theme background")
    val fg = hex(values["foreground"]) ?: error("Missing theme foreground")
    val accent = hex(values["accent"]) ?: hex(values["blue"]) ?: fg
    val colors = OmarchyPalette.defaultColors.mapValues { (_, value) -> value }.toMutableMap()
    colors.putAll(
        mapOf(
            "background" to bg,
            "foreground" to fg,
            "accent" to accent,
            "dark_background" to mixHex(bg, fg, .035),
            "darker_background" to bg,
            "lighter_background" to mixHex(bg, fg, .07),
            "selection" to mixHex(bg, accent, .22),
            "muted" to mixHex(bg, fg, .65),
            "dark_foreground" to mixHex(bg, fg, .65),
            "light_foreground" to fg,
            "bright_foreground" to fg,
            "blue" to accent,
        )
    )
    for ((key, value) in values) if (key in colors) hex(value)?.let { colors[key] = it }
    return OmarchyPalette(mode, colors)
}

/** Settings facts invalidate these scries. updateId is opaque, NOT an ordered revision. */
fun settingsEntry(body: JsonElement, bucket: String, entry: String): JsonElement? {
    val root = body as? JsonObject ?: error("Invalid settings desk")
    val desk = (root["desk"] ?: root) as? JsonObject ?: error("Invalid settings desk")
    val bucketValue = desk[bucket] ?: return null
    val value =
        (bucketValue as? JsonObject ?: error("Invalid settings bucket"))[entry] ?: return null
    if (value == JsonNull) return null
    require(value.toString().length <= 262144) { "Theme exceeds size limit" }
    return if (value is JsonPrimitive && value.isString) Wire.parseToJsonElement(value.content)
    else value
}

fun profileTheme(body: JsonElement): ShipTheme? {
    val value = settingsEntry(body, "appearance", "current") ?: return null
    val profile = value as? JsonObject ?: error("Invalid appearance profile")
    require(
        (profile["schemaVersion"] as? JsonPrimitive)?.let { !it.isString && it.intOrNull == 1 } ==
            true
    ) {
        "Unsupported appearance schema"
    }
    val colors = profile["colors"] as? JsonObject ?: error("Missing theme colors")
    return ShipTheme(
        themeName(profile["theme"]),
        "Omarchy",
        normalizedPalette(colors["mode"].text() ?: "", colors),
    )
}

fun bridgeTheme(body: JsonElement): ShipTheme? {
    val entry = settingsEntry(body, "ui-prefs", "themes") ?: return null
    val root = entry as? JsonObject ?: error("Invalid Talon themes")
    val themes = root["themes"] as? JsonArray ?: error("Invalid Talon themes")
    val theme =
        themes.filterIsInstance<JsonObject>().firstOrNull {
            it["id"].text() == "omarchy-urbit-theme"
        } ?: return null
    val dark =
        (theme["dark"] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
            ?: error("Missing theme mode")
    val bg = hex(theme["background"]) ?: error("Missing theme background")
    val mapped = buildJsonObject {
        put("background", bg)
        put("foreground", hex(theme["text"]) ?: if (dark) "#ecece8" else "#202024")
        for ((target, source) in
            mapOf(
                "accent" to "primary",
                "blue" to "link",
                "green" to "tertiary",
                "red" to "error",
                "muted" to "muted",
                "selection" to "selection",
                "dark_background" to "surface",
                "lighter_background" to "raised",
            )) hex(theme[source])?.let { put(target, it) }
    }
    return ShipTheme(
        themeName(theme["name"]),
        "Talon bridge",
        normalizedPalette(if (dark) "dark" else "light", mapped),
    )
}

private fun themeName(value: JsonElement?): String =
    value.text()?.take(120)?.filter { it >= ' ' }?.takeIf { it.isNotBlank() } ?: "Ship theme"
