plugins {
    id("java")
}

import org.gradle.api.GradleException

group = "HyProTechTeam"
//version = "1.5.0-SNAPSHOT"
version = "1.5.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

sourceSets {
    main {
        java.srcDirs("src/main/java")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://www.cursemaven.com") } // Main repo
    maven { url = uri("https://jitpack.io") } // Can be useful to compatibility
}

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

val hytalePath = hytaleJarCandidates.firstOrNull(File::exists)
    ?: throw GradleException(
        "HytaleServer.jar not found. Set HYTALE_SERVER_JAR or place the jar at one of: " +
            hytaleJarCandidates.joinToString { it.absolutePath },
    )

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

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}
