import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig.Mode.*

plugins {
    kotlin("js") version "1.6.21"
    kotlin("plugin.serialization") version "1.6.21"
}

repositories {
    mavenCentral()
}

val backendCdkOutputs: Configuration by configurations.creating {}
val webClientAwsProductionBuild: Configuration by configurations.creating {}

dependencies {
    backendCdkOutputs(project(":aws-deployment", "backendCdkOutputs"))
    implementation(project(":festival-garden-interchange"))

    //React, React DOM + Wrappers (chapter 3)
    implementation(enforcedPlatform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:1.0.0-pre.354"))
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom")

    //Kotlin React Emotion (CSS) (chapter 3)
    implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion")

    //Coroutines & serialization (chapter 8)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.0")

    implementation(npm("three", "0.143.0"))
    implementation(npm("@popperjs/core", "2.11.6"))
    implementation(npm("@tweenjs/tween.js", "18.6.4"))
    implementation(npm("circletype", "2.3.0"))

    implementation("io.ktor:ktor-serialization-kotlinx-json:2.0.3")
    implementation("io.ktor:ktor-client-js:2.0.3")
    implementation("io.ktor:ktor-client-core:2.0.3")
    implementation("io.ktor:ktor-client-content-negotiation:2.0.3")
}

fun specializeFrontendBuild(webpack: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack) {
    val backendUrl = when (webpack.mode) {
        DEVELOPMENT -> "http://localhost:8084"
        PRODUCTION -> "https://api.festgarden.com"
    }
    webpack.args.addAll(listOf("--env", "backendUrl=${backendUrl}"))
    webpack.webpackConfigApplier {
        export = false
    }
}

kotlin {
    js {
        browser {
            runTask { specializeFrontendBuild(this) }
            webpackTask { specializeFrontendBuild(this) }
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
        useCommonJs()
        binaries.executable()
    }
}

tasks.build.get().enabled = false
tasks.build.get().dependsOn.clear()
tasks.assemble.get().enabled = false
tasks.assemble.get().dependsOn.clear()

rootProject.extensions.configure<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension> {
    versions.webpackCli.version = "4.10.0"
}

artifacts.add(webClientAwsProductionBuild.name, buildDir.resolve("distributions")) {
    builtBy(tasks.getByPath(":festival-garden-web-client:browserDistribution"))
}