plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":game-core"))
    implementation(project(":game-content"))
    implementation(project(":game-sim"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kubriko.engine)
    implementation(libs.kubriko.plugin.keyboard.input)
    implementation(libs.kubriko.plugin.sprites)
}
