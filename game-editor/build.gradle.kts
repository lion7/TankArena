plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":game-core"))
        }
    }
}
