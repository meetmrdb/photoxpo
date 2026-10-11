plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs JVM unit tests for app (debug) and core."
    dependsOn(":app:testDebugUnitTest", ":core:test")
}
