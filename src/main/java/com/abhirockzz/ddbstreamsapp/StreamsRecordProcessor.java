package com.abhirockzz.ddbstreamsapp;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.streamsadapter.model.RecordAdapter;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.ShutdownReason;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.amazonaws.services.kinesis.model.Record;

import java.nio.charset.Charset;
import java.util.Map;

public class StreamsRecordProcessor implements IRecordProcessor {
    private Integer checkpointCounter;

    private final AmazonDynamoDB dynamoDBClient;
    private final String tableName;
    private final String workerName;

    public StreamsRecordProcessor(AmazonDynamoDB dynamoDBClient2, String tableName, String workerName) {
        this.dynamoDBClient = dynamoDBClient2;
        this.tableName = tableName;
        this.workerName = workerName;
    }

    @Override
    public void initialize(InitializationInput initializationInput) {
        checkpointCounter = 0;
    }

    @Override
    public void processRecords(ProcessRecordsInput processRecordsInput) {
        for (Record record : processRecordsInput.getRecords()) {
            String data = new String(record.getData().array(), Charset.forName("UTF-8"));

            System.out.println("change stream data:\n" + data);

            if (record instanceof RecordAdapter) {
                com.amazonaws.services.dynamodbv2.model.Record streamRecord = ((RecordAdapter) record)
                        .getInternalObject();

                switch (streamRecord.getEventName()) {
                    case "INSERT":
                        Map<String, AttributeValue> item = streamRecord.getDynamodb().getNewImage();
                        item.put("processed_by", new AttributeValue(workerName));

                        PutItemRequest putItemRequest = new PutItemRequest().withTableName(tableName).withItem(item);

                        try {
                            dynamoDBClient.putItem(putItemRequest);
                            System.out.println("saved item to table " + tableName + " "+ item.get("email"));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;

                    case "REMOVE":
                    Map<String, AttributeValue> keys = streamRecord.getDynamodb().getKeys();
                    DeleteItemRequest deleteItemRequest = new DeleteItemRequest().withTableName(tableName)
                            .withKey(keys);

                            try {
                                dynamoDBClient.deleteItem(deleteItemRequest);
                                System.out.println("deleted item from table " + tableName + " "+ keys.get("email"));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            break;
                }
            }
            checkpointCounter += 1;
            if (checkpointCounter % 10 == 0) {
                try {
                    processRecordsInput.getCheckpointer().checkpoint();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    @Override
    public void shutdown(ShutdownInput shutdownInput) {
        if (shutdownInput.getShutdownReason() == ShutdownReason.TERMINATE) {
            try {
                shutdownInput.getCheckpointer().checkpoint();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}
