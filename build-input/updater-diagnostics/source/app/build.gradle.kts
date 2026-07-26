plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("B33R_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("B33R_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("B33R_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("B33R_RELEASE_KEY_PASSWORD").orNull
val releaseSigningAvailable = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
if (releaseTaskRequested && !releaseSigningAvailable) {
    error(
        "Release signing is required. Set B33R_RELEASE_STORE_FILE, " +
            "B33R_RELEASE_STORE_PASSWORD, B33R_RELEASE_KEY_ALIAS, and " +
            "B33R_RELEASE_KEY_PASSWORD.",
    )
}

android {
    namespace = "com.streamdeck.iptv"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.streamdeck.iptv"
        minSdk = 21
        targetSdk = 36
        versionCode = 26
        versionName = "1.9.12"

        buildConfigField("String", "XTREAM_BASE_URL", "\"http://tv.b33r.top:59828\"")
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/uhuhuhuhuhuhuhuh/b33r/main/versions/latest.json\"",
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("production") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("production")
        }
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        if (!releaseSigningAvailable) {
            // Umbrella tasks such as `assemble` must never emit an unsigned
            // artifact that could be mistaken for a published update.
            variant.enable = false
        }
    }
}

dependencies {
    // The July 2025 Compose line is the newest stable line that still supports
    // Android 5.x devices such as the first-generation Fire TV.
    val composeBom = platform("androidx.compose:compose-bom:2025.07.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.1")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:1.8.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.8.1")
    implementation("androidx.media3:media3-datasource-rtmp:1.8.1")
    implementation("androidx.media3:media3-ui:1.8.1")
    implementation("org.videolan.android:libvlc-all:3.6.5")

    testImplementation("junit:junit:4.13.2")
}
