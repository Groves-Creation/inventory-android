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

android {
    namespace = "com.inventory.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inventory.mobile"
        minSdk = 31
        targetSdk = 36
        versionCode = 21
        versionName = "0.3.11"
        buildConfigField("String", "CONVEX_URL", "\"${convexUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"mtdewwolf/inventory-android\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // CI sets ANDROID_KEYSTORE_* (see .github/workflows/release.yml). Local
            // assembleRelease without those env vars stays unsigned instead of failing package.
            val path = releaseKeystorePath.orNull
            if (path != null) {
                storeFile = file(path)
                storePassword = releaseKeystorePassword.orNull
                keyAlias = releaseKeyAlias.orNull
                keyPassword = releaseKeyPassword.orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            if (releaseKeystorePath.orNull != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    // Compile-only stand-in for BrotherPrintLibrary.aar when -PbrotherStub=true.
    @Suppress("DEPRECATION")
    if (providers.gradleProperty("brotherStub").isPresent) {
        sourceSets.getByName("main").java.srcDir("../brother-stub")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Brother distributes the print SDK only as a download behind their developer licence, so it
// cannot be resolved from a repository. Fail early with instructions rather than letting the
// Kotlin compiler report dozens of unresolved-reference errors.
val brotherSdkAar = layout.projectDirectory.file("libs/BrotherPrintLibrary.aar")
val verifyBrotherSdk = tasks.register("verifyBrotherSdk") {
    val aar = brotherSdkAar.asFile
    outputs.upToDateWhen { aar.exists() }
    doLast {
        if (!aar.exists()) {
            throw GradleException(
                """
                Missing ${aar.relativeTo(rootDir)}.

                Download "Brother Print SDK for Android" (v4.13.0 or newer) from
                https://support.brother.com/g/s/es/dev/en/mobilesdk/android/index.html
                and copy BrotherPrintLibrary.aar into app/libs/.

                See the "Label printer" section of README.md.
                """.trimIndent(),
            )
        }
    }
}
if (!providers.gradleProperty("brotherStub").isPresent) tasks.named("preBuild") { dependsOn(verifyBrotherSdk) }

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

    // Brother Print SDK is not published to any Maven repository; see README "Label printer".
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
