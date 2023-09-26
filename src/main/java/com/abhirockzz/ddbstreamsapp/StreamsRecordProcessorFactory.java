package com.abhirockzz.ddbstreamsapp;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;

public class StreamsRecordProcessorFactory implements IRecordProcessorFactory {
    private final String tableName;
    private final AmazonDynamoDB dynamoDBClient;
    private final String workerName;

    public StreamsRecordProcessorFactory(AmazonDynamoDB dynamoDBClient, String tableName, String workerName) {
        this.tableName = tableName;
        this.dynamoDBClient = dynamoDBClient;
        this.workerName = workerName;
    }

    @Override
    public IRecordProcessor createProcessor() {
        return new StreamsRecordProcessor(dynamoDBClient, tableName, workerName);
    }
}
