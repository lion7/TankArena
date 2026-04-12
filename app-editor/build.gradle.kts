plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":game-content"))
    implementation(project(":game-legacy"))
    implementation(project(":game-render-kubriko"))
    implementation(project(":game-sim"))
    implementation(project(":game-ui-compose"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.tankarena.app.editor.MainKt"
    }
}
