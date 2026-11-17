import org.gradle.api.artifacts.VersionCatalogsExtension

plugins { id("com.android.application") }
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
android {
    compileSdk {
        version = release(catalog.findVersion("android-compile-sdk").get().requiredVersion.toInt()) {
            minorApiLevel = 2
        }
    }
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
