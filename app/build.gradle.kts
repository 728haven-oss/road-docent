plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "kr.roaddocent"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.roaddocent"
        minSdk = 33            // Pleos Connect AVD = API 34 (Pleos guide: minSdk >= 28)
        targetSdk = 35
        // Google Sign-In needs a Web client ID from Google Cloud Console (OAuth 2.0 → Web application). Empty = button explains setup.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"" + (project.findProperty("GOOGLE_WEB_CLIENT_ID") ?: "") + "\"")
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            // Demo keystore for contest submission; replace with a private one before store release.
            storeFile = file("../keystore/roaddocent-release.jks")
            storePassword = "roaddocent"
            keyAlias = "roaddocent"
            keyPassword = "roaddocent"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Pleos Playground SDK
    implementation(libs.bundles.pleos.sdk)
}
