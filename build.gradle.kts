// Root build.gradle.kts (fallback if DSL fails)
buildscript {
    dependencies {
        classpath(libs.android.gradle.plugin)
        classpath(libs.kotlin.gradle.plugin)
    }
}
