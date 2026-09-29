import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Q14: optional release signing. The owner creates the keystore in Android
// Studio (Build > Generate Signed Bundle/APK > Create new…) and records the
// four values in /keystore.properties (gitignored — NEVER commit it):
//   storeFile=../keystore.jks  storePassword=…  keyAlias=…  keyPassword=…
// When the file is absent (this sandbox), release builds stay unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// A13-01 guard: fail the RELEASE build while the base URL placeholder ships.
// The placeholder is a deploy-time string resource (README STEP 1); forgetting
// it used to produce an APK where every request dies against .example.com.
// Debug builds are exempt (development keeps working); release builds fail
// unless the real domain is set OR the build is an explicitly flagged sandbox
// verification run (-PallowPlaceholderBaseUrl=true).
val rawBaseUrl = file("src/main/res/values/strings.xml").readText()
val baseUrlIsPlaceholder = rawBaseUrl.contains("YOUR-AREENAX-DOMAIN")
val allowPlaceholder = providers.gradleProperty("allowPlaceholderBaseUrl").isPresent

android {
    namespace = "com.areenax.app"
    compileSdk = 36      // A11-02/A10-01: target API 36 (Play requirement since 2026-08-31)

    defaultConfig {
        applicationId = "com.areenax.app"
        minSdk = 24          // Android 7.0+
        targetSdk = 36       // Android 16 (Google Play 2026 requirement)
        versionCode = 1      // Q11/A13-04: first Play release consumes versionCode 1
        versionName = "1.0.0" // Q11-PREFER-1.0.0 (nothing on Play yet, so 1.0.0 is free)
        resourceConfigurations += "en"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 code shrinking + resource shrinking (latest standard).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystorePropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                null // unsigned; owner signs per Q14
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    afterEvaluate {
        if (baseUrlIsPlaceholder && !allowPlaceholder) {
            tasks.matching { it.name in setOf("assembleRelease", "bundleRelease") }.configureEach {
                doFirst {
                    throw GradleException(
                        "A13-01: app_base_url is still the placeholder " +
                        "https://YOUR-AREENAX-DOMAIN.example.com — set the live domain in " +
                        "app/src/main/res/values/strings.xml before building a release " +
                        "(sandbox verification builds may pass -PallowPlaceholderBaseUrl=true)."
                    )
                }
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = false
    }

    // Gradle Managed Devices (owner command 2026-09-21): local managed test device
    // "Pixel 5, API 33, AOSP image". NOTE: localDevices run on the BUILD MACHINE,
    // not in Google's cloud — see audit/04-verification/CLOUD_EMULATOR_RESULTS.md.
    testOptions {
        managedDevices {
            localDevices {
                create("pixel5api33") {
                    device = "Pixel 5"
                    apiLevel = 33
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Compose (BOM-managed)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)

    // Images
    implementation(libs.coil.compose)

    // Networking — mirrors the web client exactly (JSON bodies, x-token, Idempotency-Key)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // QR: generate (core) + scan (embedded)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)

    // Firebase Push Notifications (FCM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
