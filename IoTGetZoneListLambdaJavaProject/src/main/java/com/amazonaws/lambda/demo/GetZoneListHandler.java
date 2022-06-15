package com.amazonaws.lambda.demo;



import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.TimeZone;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class GetZoneListHandler implements RequestHandler<Event, String> {

    private DynamoDB dynamoDb;
    private String DYNAMODB_TABLE_NAME = "Zone";

    @Override
    public String handleRequest(Event input, Context context) {
        this.initDynamoDbClient();
        
        Table table = dynamoDb.getTable(DYNAMODB_TABLE_NAME);

        QuerySpec querySpec = new QuerySpec()
                .withKeyConditionExpression("device = :v_id")	
                .withValueMap(new ValueMap().withString(":v_id",input.device)); 

        ItemCollection<QueryOutcome> items=null;
        try {           
            items = table.query(querySpec);
        }
        catch (Exception e) {
            System.err.println("Unable to scan the table:");
            System.err.println(e.getMessage());
        }

        return getResponse(items);
    }

    private String getResponse(ItemCollection<QueryOutcome> items) {
    	
    	// "data" 에 배열로 리턴되도록 함
        Iterator<Item> iter = items.iterator();
        String response = "{ \"data\": [";
        for (int i =0; iter.hasNext(); i++) {
            if (i!=0) 
                response +=",";
            response += iter.next().toJSON();
        }
        response += "]}";
        return response;
    }

    private void initDynamoDbClient() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();

        this.dynamoDb = new DynamoDB(client);
    }
}


class Event {
    public String device;
}








//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//
//public class GetZoneListHandler implements RequestHandler<Object, String> {
//
//    @Override
//    public String handleRequest(Object input, Context context) {
//        context.getLogger().log("Input: " + input);
//
//        // TODO: implement your handler
//        return "Hello from Lambda!";
//    }
//
//}
