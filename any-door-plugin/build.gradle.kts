import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun readRevisionFromRootPom(): String {
    val pomFile = file("../pom.xml")
    val document = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(pomFile)
    val revisionNodes = document.getElementsByTagName("revision")
    require(revisionNodes.length > 0) { "Missing <revision> in ${pomFile.path}" }
    return revisionNodes.item(0).textContent.trim()
}

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.lgp547"
val anyDoorVersion = readRevisionFromRootPom()
val compatibilityVersions = providers.gradleProperty("compatibilityVersions")
    .orElse("2024.3,2025.1,2025.2,2025.3,2026.1,2026.2")
    .get()
    .split(",")
    .map(String::trim)
    .filter(String::isNotEmpty)
val localVerificationIdePath = providers.gradleProperty("localVerificationIdePath").orNull
version = anyDoorVersion

repositories {
    mavenCentral()
    mavenLocal()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("io.github.lgp547:any-door-core:$anyDoorVersion")
    implementation("io.github.lgp547:any-door-attach:$anyDoorVersion")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")

    intellijPlatform {
        intellijIdea("2024.3")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.modules.json")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks {
    processResources {
        filesMatching("anydoor-plugin.properties") {
            filter { line -> line.replace("@revision@", anyDoorVersion) }
        }
    }

    withType<Test> {
        useJUnitPlatform()
    }

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }
}

intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    pluginVerification {
        ides {
            if (localVerificationIdePath != null) {
                local(file(localVerificationIdePath))
            } else {
                compatibilityVersions.forEach { ideVersion ->
                    val platformType = if (ideVersion in setOf("2024.3", "2025.1", "2025.2")) {
                        IntelliJPlatformType.IntellijIdeaCommunity
                    } else {
                        IntelliJPlatformType.IntellijIdea
                    }
                    create(platformType, ideVersion)
                }
            }
        }
    }
}
