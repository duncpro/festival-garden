plugins {
    kotlin("jvm") version "1.6.10"
    application
}

val dockerImage: Configuration by configurations.creating {}
val backendCdkOutputs: Configuration by configurations.creating {}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.3.0-beta0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    backendCdkOutputs(project(":aws-deployment", "backendCdkOutputs"))
    implementation(kotlin("stdlib"))
    implementation("com.duncpro:jackal:${Versions.JACKAL}")
    implementation("software.amazon.awssdk:rdsdata:2.17.247")
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