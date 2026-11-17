plugins { id("xview.android.compose") }
android { namespace = "io.github.tancan1998.xviewtv.feature.home" }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)
    implementation(libs.coroutines.core)
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
}
