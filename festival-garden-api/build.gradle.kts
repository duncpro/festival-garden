plugins {
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

/**
 * This configuration is associated with the specialized AWS Lambda version of the Festival Garden
 * REST API request handler. This package is sent to Amazon when deploying in production, and it processes
 * all Festival Garden API requests which are sent from production clients.
 */
val lambdaPackage: Configuration by configurations.creating

repositories {
    mavenCentral()
    maven("https://jitpack.io")

}
dependencies {
    implementation(project(":shared-backend-utils"))
    implementation(project(":festival-garden-interchange"))
    implementation(project(":festival-garden-db-models"))
    implementation(project(":spotify-client"))
    implementation(project(":queue-service-abstraction"))

    implementation("com.duncpro:restk:${Versions.RESTK}")
    implementation("com.duncpro:jackal:${Versions.JACKAL}")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    implementation("software.amazon.awssdk:rdsdata:2.17.247")
    implementation(kotlin("stdlib"))

    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("org.slf4j:slf4j-api:1.7.36")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("com.h2database:h2:2.1.214")

    testImplementation("io.ktor:ktor-serialization-kotlinx-json:2.0.3")
    testImplementation("io.ktor:ktor-client-cio-jvm:2.0.3")
    testImplementation("io.ktor:ktor-client-core:2.0.3")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.0.3")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "11"
    }
}

/**
 * This Gradle Task generates the AWS Lambda deployment package which is executed by the AWS Lambda runtime
 * anytime a Festival Garden API request is received. This package is not used when deploying locally
 * to the development machine.
 */
val buildLambdaPackage by tasks.registering(Zip::class) {
    from(tasks.compileKotlin)
    from(tasks.compileJava)
    from(tasks.processResources)
    into("lib") {
        from(configurations.runtimeClasspath)
    }
}
artifacts.add(lambdaPackage.name, buildLambdaPackage)

tasks.test {
    useJUnitPlatform()
}