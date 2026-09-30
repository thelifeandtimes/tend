package io.omabit.tend

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

class AppearanceTest {
    private val colors =
        Wire.parseToJsonElement(
                """{"mode":"light","background":"#FAFAF5","foreground":"#202024","accent":"#366644"}"""
            )
            .jsonObject

    private fun profile(schema: Int = 1, c: JsonObject = colors) = buildJsonObject {
        put(
            "desk",
            buildJsonObject {
                put(
                    "appearance",
                    buildJsonObject {
                        put(
                            "current",
                            buildJsonObject {
                                    put("schemaVersion", schema)
                                    put("theme", "Test")
                                    put("colors", c)
                                }
                                .toString(),
                        )
                    },
                )
            },
        )
    }

    @Test
    fun fullProfileUsesLightDerivedSurfaces() {
        val theme = profileTheme(profile())!!
        assertEquals("light", theme.palette.mode)
        assertEquals("#fafaf5", theme.palette.colors["background"])
        assertTrue(luminance(theme.palette.colors.getValue("dark_background")) > .7)
        assertEquals("#ffffff", inkFor(theme.palette.colors.getValue("accent")))
    }

    @Test
    fun optionalInvalidColorsAreIgnored() {
        val p =
            profileTheme(
                    profile(
                        c =
                            JsonObject(
                                colors +
                                    mapOf(
                                        "muted" to JsonObject(emptyMap()),
                                        "selection" to JsonPrimitive("url(bad)"),
                                    )
                            )
                    )
                )!!
                .palette
        assertTrue(p.colors.values.all { Regex("#[a-f0-9]{6}").matches(it) })
    }

    @Test
    fun malformedIsNotAbsence() {
        assertNull(profileTheme(Wire.parseToJsonElement("""{"desk":{}}""")))
        assertFails { profileTheme(profile(2)) }
        assertFails {
            profileTheme(profile(c = JsonObject(colors + ("mode" to JsonPrimitive("auto")))))
        }
        assertFails {
            profileTheme(profile(c = JsonObject(colors + ("background" to JsonPrimitive("red")))))
        }
        assertFails {
            profileTheme(
                Wire.parseToJsonElement("""{"desk":{"appearance":{"current":"invalid"}}}""")
            )
        }
    }

    @Test
    fun legacyBridgeDoesNotDependOnTalonActiveTheme() {
        val theme =
            bridgeTheme(
                Wire.parseToJsonElement(
                    """{"desk":{"ui-prefs":{"themes":{"activeId":"other","themes":[{"id":"omarchy-urbit-theme","name":"Bridge","dark":false,"background":"#fafaf5","surface":"#eeeeea","primary":"#366644","text":"","muted":""}]}}}}"""
                )
            )!!
        assertEquals("Talon bridge", theme.source)
        assertEquals("light", theme.palette.mode)
        assertEquals("#eeeeea", theme.palette.colors["dark_background"])
        assertEquals("#202024", theme.palette.colors["foreground"])
    }

    @Test
    fun cacheAndLocalOverrideRoundTrip() {
        val data = TendState(appearance = AppearanceState(false, profileTheme(profile())))
        val decoded = Wire.decodeFromString<TendState>(Wire.encodeToString(data))
        assertEquals(data, decoded)
        assertEquals(OmarchyPalette(), decoded.appearance.palette)
        assertEquals("light", decoded.appearance.copy(followShip = true).palette.mode)
        assertEquals(AppearanceState(), Wire.decodeFromString<TendState>("{}").appearance)
    }

    @Test
    fun selectionInkAndOpaqueIdentity() {
        assertEquals("#000000", inkFor("#ffffff"))
        assertEquals("#ffffff", inkFor("#000000"))
        // Identity ordering is deliberately absent: snapshots alone are authoritative.
        assertEquals(
            profileTheme(profile()),
            profileTheme(JsonObject(profile() + ("updateId" to JsonPrimitive("0")))),
        )
    }
}
