import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Release line. Bump by hand only when the release is genuinely a new line; minor and patch come
// from git so nobody has to remember to bump anything per build.
val versionMajor = 1

// Commit count on the current branch — monotonic, so every build off a later commit gets a higher
// version code, which is exactly what Play requires. Falls back to 1 when there is no git history
// (source archive, shallow CI clone) so the build still works.
val gitCommitCount: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

// Every 100 commits rolls into the minor number, so the patch number never runs past 99 and the
// version reads like a normal semver string: 127 commits -> 1.1.27.
val versionMinor = gitCommitCount / 100
val versionPatch = gitCommitCount % 100

// --- AdMob ids -------------------------------------------------------------------------------
// Google's public test ids. Debug always uses these, and release falls back to them when no real
// id is configured, so a build never silently ships a half-configured ad setup — it ships an
// obviously-fake one instead. See docs/admob-setup.md.
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdMobRewardedUnitId = "ca-app-pub-3940256099942544/5224354917"
val testAdMobInterstitialUnitId = "ca-app-pub-3940256099942544/1033173712"

// Real ids stay out of git. Looked up in order: local.properties (untracked), then -P gradle
// properties, then the environment — so a workstation uses the file and CI uses secrets.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun configValue(propertyKey: String, environmentKey: String): String? =
    localProperties.getProperty(propertyKey)
        ?: providers.gradleProperty(propertyKey).orNull
        ?: System.getenv(environmentKey)

fun adMobId(propertyKey: String, environmentKey: String, fallback: String): String =
    configValue(propertyKey, environmentKey) ?: fallback

val admobAppId = adMobId("admob.appId", "ADMOB_APP_ID", testAdMobAppId)
val admobRewardedUnitId = adMobId("admob.rewardedUnitId", "ADMOB_REWARDED_UNIT_ID", testAdMobRewardedUnitId)
val admobInterstitialUnitId =
    adMobId("admob.interstitialUnitId", "ADMOB_INTERSTITIAL_UNIT_ID", testAdMobInterstitialUnitId)

if (
    admobAppId == testAdMobAppId ||
    admobRewardedUnitId == testAdMobRewardedUnitId ||
    admobInterstitialUnitId == testAdMobInterstitialUnitId
) {
    logger.lifecycle(
        "AdMob: using Google test ids for release builds — set admob.appId, " +
            "admob.rewardedUnitId and admob.interstitialUnitId in local.properties before " +
            "uploading (docs/admob-setup.md).",
    )
}

// --- Release signing -------------------------------------------------------------------------
// Same lookup order as the ad ids, so the keystore password never reaches git: local.properties on
// a workstation, -P properties or environment secrets on CI. When nothing is configured the release
// build still runs and simply produces an unsigned artifact — a clean checkout must be buildable
// without anyone's keystore. See docs/release-signing.md.
val keystorePath = configValue("signing.storeFile", "ALGORA_STORE_FILE")
val keystoreFile = keystorePath?.let { rootProject.file(it) }?.takeIf { it.isFile }
val keystorePassword = configValue("signing.storePassword", "ALGORA_STORE_PASSWORD")
val releaseKeyAlias = configValue("signing.keyAlias", "ALGORA_KEY_ALIAS")
val releaseKeyPassword = configValue("signing.keyPassword", "ALGORA_KEY_PASSWORD") ?: keystorePassword
val releaseSigningConfigured = keystoreFile != null &&
    keystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

if (!releaseSigningConfigured) {
    // A path that points nowhere is worth its own line: it is otherwise indistinguishable from
    // having configured nothing, and the build would silently emit an unsigned artifact.
    if (keystorePath != null && keystoreFile == null) {
        logger.lifecycle("Signing: keystore not found at $keystorePath — release will be unsigned.")
    } else {
        logger.lifecycle(
            "Signing: no release keystore configured — release artifacts will be unsigned " +
                "(docs/release-signing.md).",
        )
    }
}

android {
    namespace = "com.algora.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.algora.app"
        minSdk = 26
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Debug keeps the test ids regardless of what is configured: AdsProvider swaps in
        // FakeRewardedAds for debug anyway, and a debug build must never be able to touch the real
        // account — self-clicked impressions are what gets AdMob accounts suspended.
        manifestPlaceholders["admobAppId"] = testAdMobAppId
        buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$testAdMobRewardedUnitId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$testAdMobInterstitialUnitId\"")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Null when nothing is configured, which leaves the artifact unsigned instead of
            // failing the build.
            signingConfig = signingConfigs.findByName("release")

            // R8 in release only: debug stays unshrunk so stack traces and the Compose tooling
            // keep working. Everything reflective in the app comes from libraries that ship their
            // own consumer rules (WorkManager, Billing, Play Ads, DataStore); proguard-rules.pro
            // holds the few app-level keeps on top of that.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            manifestPlaceholders["admobAppId"] = admobAppId
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"$admobRewardedUnitId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_UNIT_ID", "\"$admobInterstitialUnitId\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG selects the fake billing/ads implementations (core/billing, core/ads).
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Monetization (Phase 8): one-time premium IAP + rewarded ads.
    implementation("com.android.billingclient:billing-ktx:9.1.0")
    implementation("com.google.android.gms:play-services-ads:24.6.0")

    // Play In-App Review — the rating prompt shown once a learner is genuinely invested.
    implementation("com.google.android.play:review-ktx:2.0.2")

    // Deferred work for the lapsed-learner reminder notification (core/notify).
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Firebase Analytics + Crashlytics. Config: app/google-services.json.
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
