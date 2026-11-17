plugins { id("xview.kotlin.library") }
dependencies {
    api(libs.okhttp)
    implementation(libs.coroutines.core)
    implementation(libs.json)
    implementation(project(":core:domain"))
    testImplementation(libs.junit)
}
