plugins { id("xview.kotlin.library") }
dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
