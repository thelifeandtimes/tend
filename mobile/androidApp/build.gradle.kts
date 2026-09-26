plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.omabit.tend"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.omabit.tend"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val keyPath = providers.environmentVariable("TEND_KEYSTORE").orNull
    val keyPasswordFile = providers.environmentVariable("TEND_KEY_PASSWORD_FILE").orNull
    if (keyPath != null && keyPasswordFile != null) {
        signingConfigs.create("alpha") {
            storeFile = file(keyPath)
            storePassword = file(keyPasswordFile).readText().trim()
            keyAlias = "tend-alpha"
            keyPassword = storePassword
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keyPath != null && keyPasswordFile != null)
                signingConfig = signingConfigs.getByName("alpha")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")
    ksp("androidx.room:room-compiler:2.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.ktor:ktor-client-okhttp:3.2.3")
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.material3:material3")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation(kotlin("test"))
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
