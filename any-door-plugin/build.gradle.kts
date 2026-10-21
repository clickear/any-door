import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = providers.gradleProperty(key)

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
version = anyDoorVersion

repositories {
    mavenCentral()
    mavenLocal()
    intellijPlatform {
        defaultRepositories()
    }
}

val platformVersion = properties("platformVersion").get()
val platformType = IntelliJPlatformType.valueOf(properties("platformType").get())

dependencies {
    implementation("io.github.lgp547:any-door-core:$anyDoorVersion")
    implementation("io.github.lgp547:any-door-attach:$anyDoorVersion")

    intellijPlatform {
        create(platformType, platformVersion)
        bundledPlugin("com.intellij.java")
        bundledModule("com.intellij.modules.json")
        testFramework(TestFrameworkType.Bundled)
    }

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
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
        options.release.set(17)
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild").map { it.ifBlank { null } })
    }

//    signPlugin {
//        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
//        privateKey.set(System.getenv("PRIVATE_KEY"))
//        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
//    }
//
//    publishPlugin {
//        token.set(System.getenv("PUBLISH_TOKEN"))
//    }
}
