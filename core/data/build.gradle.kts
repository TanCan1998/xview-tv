plugins { id("xview.kotlin.library") }
dependencies {
    api(project(":core:domain"))
    implementation(project(":core:provider"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
}
