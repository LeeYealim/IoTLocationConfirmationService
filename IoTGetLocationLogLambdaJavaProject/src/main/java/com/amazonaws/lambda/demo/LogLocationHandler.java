package com.amazonaws.lambda.demo;

// Location 테이블에서 
// from ~ to 까지의 위치 기록 조회


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

public class LogLocationHandler implements RequestHandler<Event, String> {

    private DynamoDB dynamoDb;
    private String DYNAMODB_TABLE_NAME = "Location";

    @Override
    public String handleRequest(Event input, Context context) {
        this.initDynamoDbClient();
        
        Table table = dynamoDb.getTable(DYNAMODB_TABLE_NAME);

        long from=0;
        long to=0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat ( "yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

            from = sdf.parse(input.from).getTime() / 1000;
            to = sdf.parse(input.to).getTime() / 1000;
        } catch (ParseException e1) {
            e1.printStackTrace();
        }
        
        // 쿼리스펙 객체를 이용해 테이블 조회
        // 쿼리스펙 객체를 table.query()의 파라미터로 넣어 조회함
        // 조건문 : 디바이스 ID, #t는 time의미 from~to 사이 객체들만 뽑아오겠다
        // :v_id, :from, :to 는 ValueMap 이라고 해서, 각각 값을 매칭시켜줌
        QuerySpec querySpec = new QuerySpec()
                .withKeyConditionExpression("device = :v_id and #t between :from and :to")	
                .withNameMap(new NameMap().with("#t", "time"))
                .withValueMap(new ValueMap().withString(":v_id",input.device).withNumber(":from", from).withNumber(":to", to)); 

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
    public String from;
    public String to;
}





//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//
//public class LogLocationHandler implements RequestHandler<Object, String> {
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
