plugins { id("xview.kotlin.library") }
dependencies {
    api(project(":core:model"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
}
