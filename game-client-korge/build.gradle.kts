plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":game-core"))
    implementation(project(":game-protocol"))
    implementation("com.soywiz.korge:korge-jvm:6.0.0")
}

application {
    mainClass = "com.tankarena.client.MainKt"
}
