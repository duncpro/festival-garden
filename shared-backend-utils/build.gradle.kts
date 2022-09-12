plugins {
    kotlin("jvm") version "1.6.10"
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    implementation("com.duncpro:jackal:${Versions.JACKAL}")
    implementation(project(":queue-service-abstraction"))
    implementation(project(":festival-garden-db-models"))
    implementation(project(":spotify-client"))
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.github.awslabs:amazon-dynamodb-lock-client:5d4f4a33ee")
    implementation(kotlin("stdlib"))
}


tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}
