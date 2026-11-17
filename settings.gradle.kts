pluginManagement {
    includeBuild("build-logic")
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "xview-tv"
include(":app", ":core:model", ":core:domain", ":core:provider", ":core:data")
include(":core:designsystem", ":core:network", ":feature:home", ":provider:demo")
