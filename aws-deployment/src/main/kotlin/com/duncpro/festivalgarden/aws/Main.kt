package com.duncpro.festivalgarden.aws

import software.amazon.awscdk.App
import software.amazon.awscdk.CfnOutput
import software.amazon.awscdk.CfnOutputProps
import software.amazon.awscdk.Duration
import software.amazon.awscdk.Environment
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.apigateway.Cors
import software.amazon.awscdk.services.apigateway.CorsOptions
import software.amazon.awscdk.services.apigateway.DomainName
import software.amazon.awscdk.services.apigateway.DomainNameOptions
import software.amazon.awscdk.services.apigateway.DomainNameProps
import software.amazon.awscdk.services.apigateway.LambdaRestApi
import software.amazon.awscdk.services.apigateway.LambdaRestApiProps
import software.amazon.awscdk.services.apigateway.Stage
import software.amazon.awscdk.services.apigateway.StageProps
import software.amazon.awscdk.services.applicationautoscaling.Schedule
import software.amazon.awscdk.services.certificatemanager.Certificate
import software.amazon.awscdk.services.ec2.CfnInternetGateway
import software.amazon.awscdk.services.ec2.PublicSubnet
import software.amazon.awscdk.services.ec2.PublicSubnetProps
import software.amazon.awscdk.services.ec2.SubnetConfiguration
import software.amazon.awscdk.services.ec2.SubnetSelection
import software.amazon.awscdk.services.ec2.SubnetType
import software.amazon.awscdk.services.ec2.Vpc
import software.amazon.awscdk.services.ec2.VpcProps
import software.amazon.awscdk.services.ecr.assets.Platform
import software.amazon.awscdk.services.ecs.AssetImageProps
import software.amazon.awscdk.services.ecs.Cluster
import software.amazon.awscdk.services.ecs.ClusterProps
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions
import software.amazon.awscdk.services.ecs.ContainerImage
import software.amazon.awscdk.services.ecs.FargatePlatformVersion
import software.amazon.awscdk.services.ecs.FargateTaskDefinition
import software.amazon.awscdk.services.ecs.FargateTaskDefinitionProps
import software.amazon.awscdk.services.ecs.patterns.ScheduledFargateTask
import software.amazon.awscdk.services.ecs.patterns.ScheduledFargateTaskImageOptions
import software.amazon.awscdk.services.ecs.patterns.ScheduledFargateTaskProps
import software.amazon.awscdk.services.lambda.CfnFunction
import software.amazon.awscdk.services.lambda.Code
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.FunctionProps
import software.amazon.awscdk.services.lambda.Runtime
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSourceProps
import software.amazon.awscdk.services.rds.AuroraCapacityUnit
import software.amazon.awscdk.services.rds.AuroraPostgresClusterEngineProps
import software.amazon.awscdk.services.rds.AuroraPostgresEngineVersion
import software.amazon.awscdk.services.rds.DatabaseClusterEngine
import software.amazon.awscdk.services.rds.ServerlessCluster
import software.amazon.awscdk.services.rds.ServerlessClusterProps
import software.amazon.awscdk.services.rds.ServerlessScalingOptions
import software.amazon.awscdk.services.route53.ARecord
import software.amazon.awscdk.services.route53.ARecordProps
import software.amazon.awscdk.services.route53.HostedZone
import software.amazon.awscdk.services.route53.HostedZoneProps
import software.amazon.awscdk.services.route53.HostedZoneProviderProps
import software.amazon.awscdk.services.route53.RecordTarget
import software.amazon.awscdk.services.route53.targets.ApiGateway
import software.amazon.awscdk.services.sqs.Queue
import software.amazon.awscdk.services.sqs.QueueProps
import software.constructs.Construct


fun main() {
    val app = App()
    FestivalGardenStack(app, "festival-garden-backend-prod", StackProps.builder()
        .env(Environment.builder()
            .region("us-east-1")
            .account("618824625980")
            .build())
        .build())
    app.synth()
}

class FestivalGardenStack(scope: Construct, id: String, props: StackProps): Stack(scope, id, props) {
    init {
        val primaryDatabase = ServerlessCluster(this, "PrimaryDatabase", ServerlessClusterProps.builder()
            .enableDataApi(true)
            .scaling(ServerlessScalingOptions.builder()
                .autoPause(Duration.minutes(0))
                .minCapacity(AuroraCapacityUnit.ACU_2)
                .maxCapacity(AuroraCapacityUnit.ACU_4)
                .build()
            )
            .engine(DatabaseClusterEngine.auroraPostgres(AuroraPostgresClusterEngineProps.builder()
                .version(AuroraPostgresEngineVersion.VER_10_16)
                .build()))
            .build()
        )

        CfnOutput(this, "primaryDatabaseArn", CfnOutputProps.builder()
            .value(primaryDatabase.clusterArn)
            .build()
        )

        CfnOutput(this, "primaryDatabaseSecretArn", CfnOutputProps.builder()
            .value(primaryDatabase.secret!!.secretArn)
            .build()
        )

        val primaryQueue = Queue(this, "primary-queue", QueueProps.builder().build())

        val restApiHandlerFunction = Function(this, "RestAPIHandler", FunctionProps.builder()
            .runtime(Runtime.JAVA_11)
            .handler("com.duncpro.festivalgarden.restapi.LambdaRequestHandler")
            .code(Code.fromAsset(System.getenv("PATH_TO_REQUEST_HANDLER_PACKAGE")))
            .memorySize(2048)
            .timeout(Duration.seconds(30))
            .environment(mapOf(
                "PRIMARY_DB_RESOURCE_ARN" to primaryDatabase.clusterArn,
                "PRIMARY_DB_SECRET_ARN" to primaryDatabase.secret!!.secretArn,
                "SPOTIFY_CREDENTIALS_CLIENT_ID" to System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
                "SPOTIFY_CREDENTIALS_CLIENT_SECRET" to System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET"),
                "PRIMARY_QUEUE_URL" to primaryQueue.queueUrl,
                "BACKEND_URL" to "https://api.festgarden.com",
                "FRONTEND_URL" to "https://festgarden.com"
            ))
            .build()
        )

        primaryDatabase.grantDataApiAccess(restApiHandlerFunction)
        primaryQueue.grantSendMessages(restApiHandlerFunction)

        val festGardenCertificate = Certificate.fromCertificateArn(this, "FestGardenCert",
            "arn:aws:acm:us-east-1:618824625980:certificate/286c06bb-ee18-4735-b4cc-08c5c1e182c2")

        val festGardenZone = HostedZone.fromLookup(this, "FestGardenZone", HostedZoneProviderProps.builder()
            .domainName("festgarden.com")
            .build())

        val primaryRestApi = LambdaRestApi(this, "PrimaryRestAPI", LambdaRestApiProps.builder()
            .handler(restApiHandlerFunction)
            .domainName(DomainNameOptions.builder()
                .domainName("api.festgarden.com")
                .certificate(festGardenCertificate)
                .build())
            .defaultCorsPreflightOptions(CorsOptions.builder()
                .allowCredentials(true)
                .allowMethods(Cors.ALL_METHODS)
                .allowHeaders(Cors.DEFAULT_HEADERS)
                .allowOrigins(Cors.ALL_ORIGINS)
                .build())
            .build()
        )

        ARecord(this, "ApiARecord", ARecordProps.builder()
            .zone(festGardenZone)
            .recordName("api")
            .target(RecordTarget.fromAlias(ApiGateway(primaryRestApi)))
            .build())

        CfnOutput(this, "primaryRestApiUrl", CfnOutputProps.builder()
            .value(primaryRestApi.deploymentStage.urlForPath("/"))
            .build()
        )

        val queueConsumerFunction = Function(this, "PrimaryQueueConsumer", FunctionProps.builder()
            .runtime(Runtime.JAVA_11)
            .handler("com.duncpro.festivalgarden.queue.server.LambdaMessageHandler")
            .code(Code.fromAsset(System.getenv("PATH_TO_MESSAGE_HANDLER_PACKAGE")))
            .memorySize(2048)
            .timeout(Duration.seconds(30))
            .environment(mapOf(
                "PRIMARY_DB_RESOURCE_ARN" to primaryDatabase.clusterArn,
                "PRIMARY_DB_SECRET_ARN" to primaryDatabase.secret!!.secretArn,
                "SPOTIFY_CREDENTIALS_CLIENT_ID" to System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
                "SPOTIFY_CREDENTIALS_CLIENT_SECRET" to System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET"),
                "PRIMARY_QUEUE_URL" to primaryQueue.queueUrl,
                "DATABASE_TYPE" to "POSTGRES"
            ))
            .build()
        )

        queueConsumerFunction.addEventSource(SqsEventSource(primaryQueue))
        primaryQueue.grantSendMessages(queueConsumerFunction)
        primaryQueue.grantConsumeMessages(queueConsumerFunction)
        primaryDatabase.grantDataApiAccess(queueConsumerFunction)

        val primaryVpc = Vpc(this, "PrimaryVpc", VpcProps.builder()
            .natGateways(0)
            .subnetConfiguration(listOf(
                SubnetConfiguration.builder()
                    .name("Public")
                    .subnetType(SubnetType.PUBLIC)
                    .cidrMask(28)
                    .build(),
                SubnetConfiguration.builder()
                    .name("Private")
                    .subnetType(SubnetType.PRIVATE_ISOLATED)
                    .cidrMask(28)
                    .build()
            ))
            .build())

        val primaryEcsCluster = Cluster(this, "PrimaryCluster", ClusterProps.builder()
            .vpc(primaryVpc)
            .build())

        val scrapeTask = ScheduledFargateTask(this, "ScraperTask", ScheduledFargateTaskProps.builder()
            .vpc(primaryVpc)
            .subnetSelection(SubnetSelection.builder()
                .subnetType(SubnetType.PUBLIC)
                .build())
            .scheduledFargateTaskImageOptions(ScheduledFargateTaskImageOptions.builder()
                .cpu(256)
                .memoryLimitMiB(1024)
                .image(ContainerImage.fromAsset(
                    System.getenv("SCRAPER_DOCKER_IMAGE"),
                    AssetImageProps.builder()
                        .platform(Platform.LINUX_AMD64)
                        .build()
                ))
                .environment(mapOf(
                    "PRIMARY_DB_RESOURCE_ARN" to primaryDatabase.clusterArn,
                    "PRIMARY_DB_SECRET_ARN" to primaryDatabase.secret!!.secretArn,
                    "SPOTIFY_CREDENTIALS_CLIENT_ID" to System.getenv("SPOTIFY_CREDENTIALS_CLIENT_ID"),
                    "SPOTIFY_CREDENTIALS_CLIENT_SECRET" to System.getenv("SPOTIFY_CREDENTIALS_CLIENT_SECRET"),
                ))
                .build())
            .cluster(primaryEcsCluster)
            .platformVersion(FargatePlatformVersion.LATEST)
            .schedule(Schedule.rate(Duration.days(14)))
            .build())

        primaryDatabase.grantDataApiAccess(scrapeTask.taskDefinition.taskRole)
    }
}