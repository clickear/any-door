import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import java.util.zip.ZipFile
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

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

// Upstream builds this once, so Run/Debug no longer rewrites jars on the IDE UI thread.
val buildAnyDoorAllDependenceJar by tasks.registering(Jar::class) {
    group = "build"
    archiveFileName.set("any-door-all-dependence.jar")
    destinationDirectory.set(layout.buildDirectory.dir("generated/any-door-agent"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from({
        configurations.runtimeClasspath.get().filter { dependency ->
            dependency.isFile && listOf("any-door", "arthas").none(dependency.name::startsWith)
        }.map(::zipTree)
    })
    exclude("META-INF/MANIFEST.MF", "META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA",
        "module-info.class", "META-INF/versions/*/module-info.class")
}

val verifyAnyDoorAllDependenceJar by tasks.registering {
    group = "verification"
    dependsOn(buildAnyDoorAllDependenceJar)
    inputs.file(buildAnyDoorAllDependenceJar.flatMap { it.archiveFile })
    doLast {
        ZipFile(buildAnyDoorAllDependenceJar.get().archiveFile.get().asFile).use { archive ->
            listOf("com/fasterxml/jackson/databind/ObjectMapper.class", "org/springframework/context/ApplicationContext.class").forEach {
                check(archive.getEntry(it) != null) { "Missing required bundle entry: $it" }
            }
            listOf("io/github/lgp547/anydoor/core/AnyDoorService.class", "io/github/lgp547/anydoor/attach/AnyDoorAttach.class", "arthas/VmTool.class").forEach {
                check(archive.getEntry(it) == null) { "Forbidden bundle entry: $it" }
            }
        }
    }
}

tasks.withType<PrepareSandboxTask>().configureEach {
    dependsOn(verifyAnyDoorAllDependenceJar)
    into(pluginName.map { "$it/agent" }) {
        from(buildAnyDoorAllDependenceJar.flatMap { it.archiveFile })
    }
}

tasks.named("check") { dependsOn(verifyAnyDoorAllDependenceJar) }

tasks.named<BuildPluginTask>("buildPlugin") {
    doLast {
        ZipFile(archiveFile.get().asFile).use { archive ->
            check(archive.entries().asSequence().any { it.name.endsWith("/agent/any-door-all-dependence.jar") }) {
                "Plugin ZIP is missing the isolated agent dependency bundle"
            }
        }
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
