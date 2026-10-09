import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The upload key for Google Play. Neither the keystore nor its passwords are
// in the repo: locally they come from `keystore.properties`, on CI from the
// environment (see .github/workflows/play-internal.yml). With neither, a
// release build is signed with the debug key — enough to smoke-test the
// shrunk build locally, but Play will not accept it.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun uploadKey(property: String, environment: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(environment)

val uploadKeystore = uploadKey("storeFile", "PLAY_UPLOAD_KEYSTORE_FILE")

android {
    namespace = "com.thehumaninternet.app"
    compileSdk = 37

    defaultConfig {
        // Same identifier as the iOS bundle id.
        applicationId = "com.thehumaninternet.app"
        // 29 is the first release where saving to the shared Pictures
        // collection needs no storage permission (see PhotoLibrarySaver).
        minSdk = 29
        targetSdk = 37
        // CI passes -PversionCode, derived from the workflow run number, so
        // every upload to Play is higher than the last.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = "0.1.0"
    }

    signingConfigs {
        if (uploadKeystore != null) {
            create("release") {
                storeFile = rootProject.file(uploadKeystore)
                storePassword = uploadKey("storePassword", "PLAY_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = uploadKey("keyAlias", "PLAY_UPLOAD_KEY_ALIAS")
                keyPassword = uploadKey("keyPassword", "PLAY_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Lets plain JVM tests touch code that logs through android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.integrity)

    testImplementation(libs.junit)
}
