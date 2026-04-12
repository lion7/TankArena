plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":game-content"))
    implementation(project(":game-input"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}
