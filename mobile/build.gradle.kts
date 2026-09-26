plugins {
    kotlin("multiplatform") version "2.2.20" apply false
    kotlin("android") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
    kotlin("plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.compose") version "1.9.1" apply false
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.3" apply false
}
