plugins {
    kotlin("jvm") version "1.6.10"
}

val lambdaPackage: Configuration by configurations.creating

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}


dependencies {
    implementation(project(":shared-backend-utils"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    implementation(project(":festival-garden-db-models"))
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.duncpro:jackal:${Versions.JACKAL}")
    implementation("software.amazon.awssdk:rdsdata:2.17.247")
    implementation(project(":queue-service-abstraction"))
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation(project(":spotify-client"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}


val buildLambdaPackage by tasks.registering(Zip::class) {
    from(tasks.compileKotlin)
    from(tasks.compileJava)
    from(tasks.processResources)
    into("lib") {
        from(configurations.runtimeClasspath)
    }
}
artifacts.add(lambdaPackage.name, buildLambdaPackage)