plugins { id("xview.kotlin.library") }
dependencies {
    api(project(":core:provider"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
