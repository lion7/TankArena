plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":game-content"))
    implementation(project(":game-legacy"))
    implementation(libs.kotlinx.serialization.json)
}

application {
    mainClass.set("com.tankarena.tools.mapconv.MainKt")
}

