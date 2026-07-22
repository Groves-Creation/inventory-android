plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

val convexUrl = providers.gradleProperty("INVENTORY_CONVEX_URL")
    .orElse(providers.environmentVariable("INVENTORY_CONVEX_URL"))
    .orNull
    .orEmpty()
val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
val releaseChannel = "brutalist"
val baselineBranch = "Master"
val baselineVersion = "0.3.0"
val baselineCommit = "af505ea"
val buildRevision = providers.gradleProperty("INVENTORY_BUILD_REVISION")
    .orElse(providers.environmentVariable("GITHUB_SHA"))
    .orElse(
        providers.provider {
            runCatching {
                ProcessBuilder("git", "rev-parse", "--short=12", "HEAD")
                    .directory(rootDir)
                    .start()
                    .inputStream
                    .bufferedReader()
                    .use { it.readText().trim() }
            }.getOrDefault("unknown")
        },
    )

val appVersionCode = 13
val appVersionName = "v0.3.3-0721262004-brut"
val expectedReleaseTag = appVersionName

android {
    namespace = "com.inventory.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inventory.mobile"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "CONVEX_URL", "\"${convexUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"mtdewwolf/inventory-android\"")
        buildConfigField("String", "UPDATE_CHANNEL", "\"$releaseChannel\"")
        buildConfigField("String", "BASELINE_BRANCH", "\"$baselineBranch\"")
        buildConfigField("String", "BASELINE_VERSION", "\"$baselineVersion\"")
        buildConfigField("String", "BASELINE_COMMIT", "\"$baselineCommit\"")
        buildConfigField("String", "BUILD_REVISION", "\"${buildRevision.get().take(12)}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseKeystorePath.orNull?.let { storeFile = file(it) }
            storePassword = releaseKeystorePassword.orNull
            keyAlias = releaseKeyAlias.orNull
            keyPassword = releaseKeyPassword.orNull
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

tasks.register("verifyReleaseTag") {
    val releaseTag = providers.environmentVariable("GITHUB_REF_NAME").orNull
    onlyIf { !releaseTag.isNullOrBlank() }
    doLast {
        check(releaseTag == expectedReleaseTag) {
            "Release tag '$releaseTag' does not match app version '$appVersionName'. Use '$expectedReleaseTag'."
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("verifyReleaseTag")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.6")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.camera:camera-camera2:1.5.3")
    implementation("androidx.camera:camera-lifecycle:1.5.3")
    implementation("androidx.camera:camera-view:1.5.3")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.4")

    implementation("dev.convex:android-convexmobile:0.8.0@aar") { isTransitive = true }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
