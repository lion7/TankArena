plugins {
    base
    kotlin("multiplatform") version "2.0.21" apply false
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
}

allprojects {
    group = "com.tankarena"
    version = "0.1.0"
}
