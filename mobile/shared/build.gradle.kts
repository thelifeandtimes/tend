plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.library")
}
kotlin {
    compilerOptions { optIn.add("kotlin.time.ExperimentalTime") }
    androidTarget()
    jvm()
    jvmToolchain(21)
    if (System.getProperty("os.name").contains("Mac")) {
        listOf(iosArm64(), iosSimulatorArm64()).forEach {
            it.binaries.framework { baseName = "TendShared"; isStatic = true }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            implementation("io.ktor:ktor-client-core:3.2.3")
        }
        commonTest.dependencies { implementation(kotlin("test")); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2"); implementation("io.ktor:ktor-client-mock:3.2.3") }
        androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.2.3") }
        jvmMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.2.3") }
    }
}
android { namespace = "io.omabit.tend.shared"; compileSdk = 36; defaultConfig { minSdk = 26 } }
