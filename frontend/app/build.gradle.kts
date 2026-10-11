import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tinycrap.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tinycrap.app"
        minSdk = 29          // Android 10+: saves to MediaStore without storage permissions
        targetSdk = 35
        versionCode = 3
        versionName = "2.1.0"
    }

    buildTypes {
        debug {
            // Lets you talk to a local http:// backend (emulator 10.0.2.2 or LAN IP).
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            // Release builds only talk to https:// servers (e.g. your cPanel domain).
            manifestPlaceholders["usesCleartext"] = "false"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Personal use: sign with the debug key so the APK installs directly.
            // For Play Store / your own release key, replace this with a real signingConfig.
            signingConfig = signingConfigs.getByName("debug")
        }
        // "perf": exactly what a release build runs (R8 shrinking, no debug overhead) but still allows a local
        // http:// backend. Pick this in Build Variants when you want to feel the real speed of the app.
        create("perf") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            manifestPlaceholders["usesCleartext"] = "true"
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)   // applies the baseline profiles that ship with Compose: faster start, smoother first frames
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}
