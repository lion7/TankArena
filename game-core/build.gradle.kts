plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
