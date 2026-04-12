plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":game-content"))
    implementation(project(":game-input"))
    implementation(project(":game-render-kubriko"))
    implementation(project(":game-sim"))
    implementation(project(":game-ui-compose"))
}

application {
    mainClass.set("com.tankarena.app.desktop.MainKt")
}

