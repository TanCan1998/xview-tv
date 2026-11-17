plugins { id("xview.android.compose") }
android { namespace = "io.github.tancan1998.xviewtv.core.designsystem" }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.tv.material)

}
