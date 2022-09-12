import com.duncpro.jackal.aws.AuroraServerlessCredentials
import com.duncpro.jackal.aws.AuroraServerlessDatabase
import com.duncpro.jackal.compileSQLScript
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.services.rdsdata.RdsDataAsyncClient
import software.amazon.awssdk.services.rdsdata.RdsDataClient

/**
 * This gradle.build file is responsible for compiling the AWS CDK infrastructure as code module,
 * and deploying the described infrastructure to the AWS platform.
 */

plugins {
    kotlin("jvm") version "1.6.10"
    application
}

/**
 * This configuration is linked to the compiled Festival Garden REST-ful Web API Serverless Lambda Function.
 * The artifact is sent as-is to AWS upon deployment, it is not included in the implementation of the CDK/CF
 * synthesizer application.
 */
val restRequestHandlerPackage: Configuration by configurations.creating {}

val queueMessageHandlerPackage: Configuration by configurations.creating {}

val scraperDockerImage: Configuration by configurations.creating {}


/**
 * This configuration is linked to the CDK Output file which is generated after each deployment of the backend.
 * The CDK Output file contains values which are depended on by the frontend deployment build step,
 * such as the backend server URL.
 */
val backendCdkOutputs by configurations.registering {}

buildscript {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.duncpro:jackal:1.0-SNAPSHOT-28")
    }
}

repositories {
    mavenCentral()
}
dependencies {
    implementation("software.amazon.awscdk:aws-cdk-lib:2.38.0")
    implementation(kotlin("stdlib"))

    // Backend Deployment to AWS depends on the actual Festival Garden REST-ful Web API request handler package.
    // By pulling in the dependency here, we ensure that the handler is compiled and packaged for AWS deployment
    // before synthesizing the CDK/CloudFormation deployment package.
    restRequestHandlerPackage(project(":festival-garden-api", "lambdaPackage"))
    queueMessageHandlerPackage(project(":festival-garden-message-handler", "lambdaPackage"))
    scraperDockerImage(project(":festival-wizard-scraper", "dockerImage"))
}

// This task synthesizes the AWS CDK/CloudFormation backend deployment package.
// It's not intended for direct invocation by the user, but is instead used by the AWS CDK CLI.
val synth by tasks.registering(JavaExec::class) {
    // Expose the path to Festival Wizard REST Request Handler package to the CDK/CloudFormation infrastructure as code
    // synthesizer application so that it can be included in the final CDK/CloudFormation deployment package which
    // is zipped and sent to AWS at deployment time.
    environment("PATH_TO_REQUEST_HANDLER_PACKAGE", restRequestHandlerPackage.singleFile.absolutePath)
    environment("PATH_TO_MESSAGE_HANDLER_PACKAGE", queueMessageHandlerPackage.singleFile.absolutePath)
    environment("SCRAPER_DOCKER_IMAGE", scraperDockerImage.singleFile.absolutePath)

    // These environmental variables contain values which need to be passed to the CDK/CloudFormation
    // infrastructure as code synthesizer application so that they may be included in the deployment package
    // so that they can be consumed by the Festival Garden REST Request Handler Lambda at execution time.
    environment("SPOTIFY_CREDENTIALS_CLIENT_ID", Keys.SPOTIFY_APP_ID)
    environment("SPOTIFY_CREDENTIALS_CLIENT_SECRET", Keys.SPOTIFY_APP_SECRET)

    // Configure the entrypoint for the executable CDK/CloudFormation synthesizer application.
    mainClass.set("com.duncpro.festivalgarden.aws.MainKt")

    classpath = sourceSets.main.get().runtimeClasspath
}

val jsonCDKDeploymentOutputsFileName = "latest-deployment-cfn-outputs.json"
val jsonCDKDeploymentOutputsFile = projectDir.resolve(jsonCDKDeploymentOutputsFileName)

val initializeAwsProdDatabase: Task by tasks.creating {
   doFirst {
       val sqlInitScriptFile = rootProject.projectDir.resolve("init-database.sql")
       val cdkDeploymentOutputs = jsonCDKDeploymentOutputsFile.readSimpleJsonFileToFlatMap()
       val databaseArn = cdkDeploymentOutputs["festival-garden-backend-prod.primaryDatabaseArn"]!!
       val databaseSecretArn = cdkDeploymentOutputs["festival-garden-backend-prod.primaryDatabaseSecretArn"]!!
       RdsDataAsyncClient.create().use { rdsDataClient ->
           val database = AuroraServerlessDatabase(rdsDataClient,
               AuroraServerlessCredentials(databaseArn, databaseSecretArn))
           sqlInitScriptFile.inputStream().use { inputStream ->
               runBlocking {
                   val transaction = database.startTransactionAsync().await()
                   try {
                       compileSQLScript(inputStream)
                           .onEach { println("Executing SQL statement: $it") }
                           .onEach { it.executeUpdateAsync(database).await() }
                           .onEach { println("Executed update successfully!") }
                           .collect()
                       transaction.commitAsync().await()
                   } finally {
                       transaction.closeAsync().await()
                   }
               }
           }
       }
   }
}

val deployAwsBackend by tasks.registering {
    dependsOn(restRequestHandlerPackage)
    dependsOn(queueMessageHandlerPackage)
    dependsOn(scraperDockerImage)
    finalizedBy(initializeAwsProdDatabase)

    doLast {
        exec {
            commandLine("cdk", "deploy", "--outputs-file", jsonCDKDeploymentOutputsFileName,
                "--require-approval", "never")
        }
    }


    outputs.file(jsonCDKDeploymentOutputsFile)

    // The freshness of this task is dependent on the current state of the deployed AWS cloud infrastructure.
    // Therefore, diffing must be done by the AWS CDK CLI.
    outputs.upToDateWhen { false }
}

// The CDK outputs file is produced by the deployAwsBackend build step.
artifacts.add(backendCdkOutputs.name, deployAwsBackend)