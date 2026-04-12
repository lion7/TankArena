plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":game-content"))
    implementation(project(":game-sim"))
}

