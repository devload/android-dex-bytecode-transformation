plugins {
    id("java")
    id("application")
}

group = "io.devload.dex"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

dependencies {
    // dexlib2 for DEX manipulation
    implementation("com.android.tools.smali:smali-dexlib2:3.0.3")

    // Guava (dexlib2 dependency)
    implementation("com.google.guava:guava:31.1-jre")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

application {
    mainClass.set("io.devload.dex.LifecycleLogger")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.devload.dex.LifecycleLogger"
    }

    // Create fat JAR with all dependencies
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
