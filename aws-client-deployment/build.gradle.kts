plugins {
    kotlin("jvm") version "1.6.10"
}

val webUiProdBuild: Configuration by configurations.creating {}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("software.amazon.awscdk:aws-cdk-lib:2.38.0")
    webUiProdBuild(project(":festival-garden-web-client", "webClientAwsProductionBuild"))
}

// This task synthesizes the AWS CDK/CloudFormation backend deployment package.
// It's not intended for direct invocation by the user, but is instead used by the AWS CDK CLI.
val synth by tasks.registering(JavaExec::class) {
    dependsOn(webUiProdBuild)
    // Configure the entrypoint for the executable CDK/CloudFormation synthesizer application.
    mainClass.set("com.duncpro.festivalgarden.aws.client.MainKt")
    environment("PATH_TO_WEB_UI_PROD_BUILD", webUiProdBuild.singleFile.absolutePath)
    classpath = sourceSets.main.get().runtimeClasspath
}

val deployFrontendCdkStack: Task by tasks.creating {
    dependsOn(":aws-deployment:deployAwsBackend")

    doLast {
        exec {
            commandLine("cdk", "deploy", "--outputs-file", "latest-deployment-cfn-outputs.json",
                "--require-approval", "never")
        }
    }

    outputs.file("./latest-deployment-cfn-outputs.json");
    outputs.upToDateWhen { false } // Defer diffing to AWS CDK CLI
}