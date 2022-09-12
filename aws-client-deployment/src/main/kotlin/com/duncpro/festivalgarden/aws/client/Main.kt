package com.duncpro.festivalgarden.aws.client

import software.amazon.awscdk.App
import software.amazon.awscdk.Duration
import software.amazon.awscdk.Environment
import software.amazon.awscdk.RemovalPolicy
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.apigateway.LambdaRestApi
import software.amazon.awscdk.services.apigateway.LambdaRestApiProps
import software.amazon.awscdk.services.certificatemanager.Certificate
import software.amazon.awscdk.services.cloudfront.Behavior
import software.amazon.awscdk.services.cloudfront.CloudFrontWebDistribution
import software.amazon.awscdk.services.cloudfront.CloudFrontWebDistributionProps
import software.amazon.awscdk.services.cloudfront.S3OriginConfig
import software.amazon.awscdk.services.cloudfront.SSLMethod
import software.amazon.awscdk.services.cloudfront.SourceConfiguration
import software.amazon.awscdk.services.cloudfront.ViewerCertificate
import software.amazon.awscdk.services.cloudfront.ViewerCertificateOptions
import software.amazon.awscdk.services.lambda.Code
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.FunctionProps
import software.amazon.awscdk.services.lambda.Runtime
import software.amazon.awscdk.services.rds.AuroraCapacityUnit
import software.amazon.awscdk.services.rds.DatabaseClusterEngine
import software.amazon.awscdk.services.rds.ServerlessCluster
import software.amazon.awscdk.services.rds.ServerlessClusterProps
import software.amazon.awscdk.services.rds.ServerlessScalingOptions
import software.amazon.awscdk.services.route53.ARecord
import software.amazon.awscdk.services.route53.ARecordProps
import software.amazon.awscdk.services.route53.HostedZone
import software.amazon.awscdk.services.route53.HostedZoneProviderProps
import software.amazon.awscdk.services.route53.RecordTarget
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget
import software.amazon.awscdk.services.s3.Bucket
import software.amazon.awscdk.services.s3.BucketProps
import software.amazon.awscdk.services.s3.deployment.BucketDeployment
import software.amazon.awscdk.services.s3.deployment.BucketDeploymentProps
import software.amazon.awscdk.services.s3.deployment.Source
import software.constructs.Construct


fun main() {
    val app = App()
    FestivalGardenClientStack(app, "festival-garden-frontend", StackProps.builder()
        .env(Environment.builder()
            .region("us-east-1")
            .account("618824625980")
            .build())
        .build())
    app.synth()
}

class FestivalGardenClientStack(scope: Construct, id: String, props: StackProps): Stack(scope, id, props) {
    init {
        val frontendBucket = Bucket(this, "WebClientBucket", BucketProps.builder()
            .publicReadAccess(true)
            .removalPolicy(RemovalPolicy.DESTROY)
            .websiteIndexDocument("index.html")
            .websiteErrorDocument("index.html")
            .build()
        )

        val websiteCertificate = Certificate.fromCertificateArn(this, "WebsiteCertificate",
        "arn:aws:acm:us-east-1:618824625980:certificate/81feda86-5f61-4b17-9388-de466fc17b49")

        val cloudfrontDistribution = CloudFrontWebDistribution(this, "WebFrontendCFDist", CloudFrontWebDistributionProps.builder()
            .originConfigs(listOf(
                SourceConfiguration.builder()
                    .s3OriginSource(S3OriginConfig.builder()
                        .s3BucketSource(frontendBucket)
                        .build()
                    )
                    .behaviors(listOf(
                        Behavior.builder()
                            .isDefaultBehavior(true)
                            .build()
                    ))
                    .build()
            ))
            .viewerCertificate(ViewerCertificate.fromAcmCertificate(websiteCertificate, ViewerCertificateOptions.builder()
                .aliases(listOf("festgarden.com"))
                .sslMethod(SSLMethod.SNI)
                .build()))
            .build())

        val websiteZone = HostedZone.fromLookup(this, "WebsiteZone", HostedZoneProviderProps.builder()
            .domainName("festgarden.com")
            .build())

        ARecord(this, "WebsiteARecord", ARecordProps.builder()
            .zone(websiteZone)
            .recordName("festgarden.com")
            .target(RecordTarget.fromAlias(CloudFrontTarget(cloudfrontDistribution)))
            .build())

        BucketDeployment(this, "WebFrontendDeployment", BucketDeploymentProps.builder()
            .sources(listOf(Source.asset(System.getenv("PATH_TO_WEB_UI_PROD_BUILD"))))
            .destinationBucket(frontendBucket)
            .distribution(cloudfrontDistribution)
            .distributionPaths(listOf("/"))
            .build())
    }
}