plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.recapmaker.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.recapmaker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.2.0"
        buildConfigField("String", "API_BASE_URL", "\"https://zzzzz-mu.vercel.app\"")
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
            // If no KEYSTORE_FILE env → signing config is empty → release will be unsigned
            // (Gradle will still produce an APK; it just won't be signed with a release key)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Always apply the release signingConfig.
            // If KEYSTORE_FILE is not set the config is empty and Gradle uses its own
            // default signing (produces an unsigned APK) — build does NOT fail/skip.
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Media
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // FFmpeg-Kit — on-device video processing (community fork, Maven Central)
    // Same API as com.arthenica:ffmpeg-kit, compatible with API 35 + 16KB pages
    implementation("com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1")
    implementation("com.arthenica:smart-exception-java:0.2.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")

    // yt-dlp for Android — download from YouTube, TikTok, Facebook, 1000+ sites
    val ytdlAndroid = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlAndroid")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$ytdlAndroid")
}
