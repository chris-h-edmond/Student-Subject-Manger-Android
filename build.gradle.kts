buildscript {
    configurations.all {
        resolutionStrategy {
            dependencySubstitution {
                substitute(module("org.jetbrains:annotations"))
                    .using(module("org.jetbrains:annotations:23.0.0"))
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}