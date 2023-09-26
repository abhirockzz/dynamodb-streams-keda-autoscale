package com.abhirockzz.ddbstreamsapp;

import java.util.UUID;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClientBuilder;
import com.amazonaws.services.dynamodbv2.streamsadapter.AmazonDynamoDBStreamsAdapterClient;
import com.amazonaws.services.dynamodbv2.streamsadapter.StreamsWorkerFactory;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;

public class App {
    private static Worker worker;
    private static KinesisClientLibConfiguration workerConfig;
    private static IRecordProcessorFactory recordProcessorFactory;

    private static AmazonDynamoDB dynamoDBClient;
    private static AmazonCloudWatch cloudWatchClient;
    private static AmazonDynamoDBStreams dynamoDBStreamsClient;
    private static AmazonDynamoDBStreamsAdapterClient adapterClient;

    private static AWSCredentialsProvider awsCredentialsProvider = WebIdentityTokenCredentialsProvider.builder()
                                                                                            .roleArn(System.getenv("AWS_ROLE_ARN"))
                                                                                            .webIdentityTokenFile("/var/run/secrets/eks.amazonaws.com/serviceaccount/token")
                                                                                            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("Starting DynamoDB streams KCL adapter client worker....");

        String awsRegion = System.getenv("AWS_REGION");
        if (awsRegion == null) {
            awsRegion = Regions.US_EAST_1.getName();
        }

        dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                                                    .withRegion(awsRegion)
                                                    .withCredentials(awsCredentialsProvider)
                                                    .build();

        cloudWatchClient = AmazonCloudWatchClientBuilder.standard()
                                                        .withRegion(awsRegion)
                                                        .withCredentials(awsCredentialsProvider)
                                                        .build();
        dynamoDBStreamsClient = AmazonDynamoDBStreamsClientBuilder.standard()
                                                                  .withRegion(awsRegion)
                                                                  .withCredentials(awsCredentialsProvider)
                                                                  .build();

        adapterClient = new AmazonDynamoDBStreamsAdapterClient(dynamoDBStreamsClient);

        String tableName = System.getenv("TARGET_TABLE_NAME");
        if (tableName == null) {
            throw new RuntimeException("TARGET_TABLE_NAME environment variable not set");
        }

        String streamArn = System.getenv("SOURCE_TABLE_STREAM_ARN");
        if (streamArn == null) {
            throw new RuntimeException("SOURCE_TABLE_STREAM_ARN environment variable not set");
        }

        String appName = System.getenv("APPLICATION_NAME");
        if (appName == null) {
            throw new RuntimeException("APPLICATION_NAME environment variable not set");
        }

        String workerName = System.getenv("INSTANCE_NAME");
        if (workerName == null) {
            workerName = "worker-"+UUID.randomUUID().toString();
        }
        
       
        recordProcessorFactory = new StreamsRecordProcessorFactory(dynamoDBClient, tableName, workerName);


        
        workerConfig = new KinesisClientLibConfiguration(appName,
                                                         streamArn,
                                                         awsCredentialsProvider,
                                                         "worker-"+workerName)
                .withMaxRecords(1000)
                .withIdleTimeBetweenReadsInMillis(500)
                .withInitialPositionInStream(InitialPositionInStream.LATEST);

        System.out.println("Creating worker for stream: " + streamArn);
        worker = StreamsWorkerFactory.createDynamoDbStreamsWorker(recordProcessorFactory, workerConfig, adapterClient, dynamoDBClient, cloudWatchClient);

        System.out.println("Starting worker...");
        Thread t = new Thread(worker);
        t.start();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down...");
                worker.shutdown();
            }
        });
    }

}


