import java.util.regex.Pattern

plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
    application
}

group = "com.duncpro"
version = "1.0"

val dockerImage: Configuration by configurations.creating {}
val backendCdkOutputs: Configuration by configurations.creating {}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    backendCdkOutputs(project(":aws-deployment", "backendCdkOutputs"))

    // AWS
    implementation("software.amazon.awssdk:netty-nio-client:2.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")

    // Database
    implementation("com.duncpro:jackal:${Versions.JACKAL}")
    implementation("software.amazon.awssdk:rdsdata:2.17.247")
    testImplementation("com.h2database:h2:2.1.214")

    implementation(project(":spotify-client"))
    implementation(project(":festival-garden-db-models"))

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0-RC")

    // HTTP
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.0.3")
    implementation("io.ktor:ktor-client-cio-jvm:2.0.3")
    implementation("io.ktor:ktor-client-core:2.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:2.0.3")

    // Scraping
    implementation("org.jsoup:jsoup:1.15.2")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.3.0-beta0")
    implementation("org.slf4j:slf4j-api:1.7.36")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")

}

tasks.create("fillRemoteDatabase") {
    dependsOn(tasks.build)
    doLast {
        val backendCdkOutputsJson = backendCdkOutputs.singleFile.readSimpleJsonFileToFlatMap()
        javaexec {
            mainClass.set("com.duncpro.festivalgarden.festivalwizardscraper.MainKt")
            environment(mapOf(
                "PRIMARY_DB_RESOURCE_ARN" to backendCdkOutputsJson["festival-garden-backend-prod.primaryDatabaseArn"],
                "PRIMARY_DB_SECRET_ARN" to backendCdkOutputsJson["festival-garden-backend-prod.primaryDatabaseSecretArn"],
                "SPOTIFY_CREDENTIALS_CLIENT_ID" to Keys.SPOTIFY_APP_ID,
                "SPOTIFY_CREDENTIALS_CLIENT_SECRET" to Keys.SPOTIFY_APP_SECRET,
                "SPOTIFY_CREDENTIALS_FILE_PATH" to file("./../spotify-credentials.json").absolutePath
            ))
            classpath = sourceSets.main.get().runtimeClasspath
        }
    }
}

application {
    mainClass.set("com.duncpro.festivalgarden.festivalwizardscraper.MainKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

val prepareDockerImageContext by tasks.registering {
    dependsOn(tasks.distZip)
    inputs.files(tasks.distZip.get().outputs.files)

    doLast {
        delete {
            delete(buildDir.resolve("docker"))
        }
    }

    doLast {
        copy {
            from(zipTree(tasks.distZip.get().outputs.files.singleFile))
            into(project.buildDir.resolve("docker"))
        }
    }

    doLast {
        """
            FROM amazoncorretto:11-alpine
            ADD "./festival-wizard-scraper-1.0" /scraper
            RUN apk update && apk add bash
            CMD ["bash", "/scraper/bin/festival-wizard-scraper"]
        """.trimIndent()
            .let { dockerFileContents ->
                val dockerFile = buildDir.resolve("docker").resolve("Dockerfile")
                dockerFile.createNewFile()
                dockerFile.writeText(dockerFileContents)
            }
    }

    outputs.dir(buildDir.resolve("docker"))
}

artifacts.add(dockerImage.name, prepareDockerImageContext)