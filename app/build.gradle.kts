plugins {
    id("xview.android.application")
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "io.github.tancan1998.xviewtv"
    defaultConfig {
        applicationId = "io.github.tancan1998.xviewtv"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "io.github.tancan1998.xviewtv.PlaybackComponentInstrumentation"
    }
    sourceSets.getByName("main").assets.directories.add("../web/pairing")
    androidResources.ignoreAssetsPattern = "!tests:!README.md"
    buildFeatures { compose = true; buildConfig = true }
    bundle { language { enableSplit = false } }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("optimized") {
            initWith(getByName("release"))
            // Local review artifact can update the existing debug-signed install; never a release signing policy.
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}
dependencies {
    implementation(project(":feature:home"))
    implementation(project(":core:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.zxing)
    implementation(project(":core:network"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
}
