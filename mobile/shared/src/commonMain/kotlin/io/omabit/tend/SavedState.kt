package io.omabit.tend

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString

/** Small editor drafts survive rotation/process recreation; never use for credentials. */
inline fun <reified T> jsonStateSaver(): Saver<T, String> =
    Saver(save = { Wire.encodeToString(it) }, restore = { Wire.decodeFromString<T>(it) })
