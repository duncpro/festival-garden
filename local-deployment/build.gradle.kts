plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.21"
}

repositories {
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("com.duncpro:restk:${Versions.RESTK}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.duncpro:jackal:${Versions.JACKAL}")
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0-RC")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.0.3")
    implementation("io.ktor:ktor-client-cio-jvm:2.0.3")
    implementation("io.ktor:ktor-client-core:2.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:2.0.3")


    implementation(project(":festival-wizard-scraper"))
    implementation(project(":festival-garden-api"))
    implementation(project(":festival-garden-db-models"))
    implementation(project(":spotify-client"))
    implementation(project(":queue-service-abstraction"))
    implementation(project(":festival-garden-message-handler"))
    implementation(project(":shared-backend-utils"))
}

val databaseEnvironmentVariable =  Pair("LOCAL_DATABASE_URL",
    "jdbc:h2:tcp://localhost:9123/${projectDir.absolutePath}/local-testing-database;database_to_upper=false;MODE=PostgreSQL")

tasks.create("fillLocalDatabase") {
    dependsOn(tasks.build)
    doLast {
        javaexec {
            environment(mapOf(
                Pair("SPOTIFY_CREDENTIALS_FILE_PATH", file("./../spotify-credentials.json").absolutePath),
                databaseEnvironmentVariable
            ))
            mainClass.set("com.duncpro.festivalgarden.local.FillLocalDatabaseMainKt")
            classpath = sourceSets.test.get().runtimeClasspath
            standardInput = System.`in`
        }
    }
}

tasks.create("runLocalServer") {
    dependsOn(tasks.build)
    doLast {
        javaexec {
            environment(mapOf(
                Pair("DATABASE_TYPE", "H2"),
                Pair("FRONTEND_URL", "http://localhost:8080"),
                Pair("BACKEND_URL", "http://localhost:8084"),
                Pair("SPOTIFY_CREDENTIALS_CLIENT_ID", Keys.SPOTIFY_APP_ID_DEV),
                Pair("SPOTIFY_CREDENTIALS_CLIENT_SECRET", Keys.SPOTIFY_APP_SECRET_DEV),
                databaseEnvironmentVariable
            ))
            mainClass.set("com.duncpro.festivalgarden.local.MainKt")
            classpath = sourceSets.test.get().runtimeClasspath
            standardInput = System.`in`
        }
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}
