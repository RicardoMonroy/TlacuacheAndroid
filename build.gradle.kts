plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 trae Kotlin integrado; esto solo fija la versión de KGP en el classpath.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
