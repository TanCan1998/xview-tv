import org.gradle.api.artifacts.VersionCatalogsExtension

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
android {
    compileSdk = catalog.findVersion("android-compile-sdk").get().requiredVersion.toInt()
    defaultConfig {
        minSdk = catalog.findVersion("android-min-sdk").get().requiredVersion.toInt()
        targetSdk = catalog.findVersion("android-target-sdk").get().requiredVersion.toInt()
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    add("coreLibraryDesugaring", catalog.findLibrary("desugar-jdk-libs").get())
}
kotlin { jvmToolchain(17) }
