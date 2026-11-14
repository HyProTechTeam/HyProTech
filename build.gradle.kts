// Apply the Java plugin to compile and package the mod.
plugins {
    id("java")
}

import org.gradle.api.GradleException

group = "HyProTechTeam"
//version = "1.5.0-SNAPSHOT"
version = "1.5.0"

// Keep compilation and the selected toolchain on the server's Java version.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// Compile the project's Java implementation from the main source tree.
sourceSets {
    main {
        java.srcDirs("src/main/java")
    }
}

// Resolve published dependencies from Maven Central and the listed mod repositories.
repositories {
    mavenCentral()
    maven { url = uri("https://www.cursemaven.com") } // Main repo
    maven { url = uri("https://jitpack.io") } // Can be useful to compatibility
}

// Try an explicit override, common installation locations, and finally a local libs directory.
val roamingHytaleJarSuffix = "Users/${System.getProperty("user.name")}/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar"
val hytaleJarCandidates = buildList {
    System.getenv("HYTALE_SERVER_JAR")?.takeIf { it.isNotBlank() }?.let(::File)?.let(::add)
    System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
        ?.let { File(it, "Hytale/install/release/package/game/latest/Server/HytaleServer.jar") }
        ?.let(::add)
    add(File(System.getProperty("user.home"), "AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar"))
    File.listRoots()?.forEach { root -> add(File(root, roamingHytaleJarSuffix)) }
    add(File(rootDir, "libs/HytaleServer.jar"))
}.distinctBy { it.absolutePath }

// Fail during configuration with all searched paths when the server API is unavailable.
val hytalePath = hytaleJarCandidates.firstOrNull(File::exists)
    ?: throw GradleException(
        "HytaleServer.jar not found. Set HYTALE_SERVER_JAR or place the jar at one of: " +
            hytaleJarCandidates.joinToString { it.absolutePath },
    )

// Server and optional mod APIs are supplied at runtime; JUnit is used by the test source set.
dependencies {
    compileOnly(files(hytalePath))
    compileOnly(fileTree("libs") {
        include("**/TuTBooKs*.jar", "**/TutBooks*.jar")
    })
    compileOnly("curse.maven:hyui-1431415:7548594")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Run tests on the JUnit Platform.
tasks.test {
    useJUnitPlatform()
}

// Compile consistently with Java 25 APIs and UTF-8 source encoding.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}
