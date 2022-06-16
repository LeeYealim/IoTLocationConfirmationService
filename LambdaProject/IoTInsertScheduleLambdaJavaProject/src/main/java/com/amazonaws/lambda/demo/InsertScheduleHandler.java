package com.amazonaws.lambda.demo;



import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;


public class InsertScheduleHandler implements RequestHandler<Event, String> {
	private	DynamoDB dynamoDb;

	private	String	TABLE_NAME = "Schedule";
	
	@Override
	public	String	handleRequest(Event	input,	Context	context) {
					this.initDynamoDbClient();

					return persistData(input, context);
	}
	
	private	String	persistData(Event event, Context context) throws ConditionalCheckFailedException	{
			
		long time=0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

            time = sdf.parse(event.timestamp).getTime() / 1000;
        } catch (ParseException e1) {
            e1.printStackTrace();
        }      			

		this.dynamoDb.getTable(TABLE_NAME)
		.putItem(new PutItemSpec().withItem(new Item()
										.withPrimaryKey("device", event.device)
										.withPrimaryKey("time", time)
										.withString("msg", event.msg)
										.withString("timestamp", event.timestamp))).toString();	
					
					//return null;
					return "OK";
	}
	
	private void initDynamoDbClient() {
					AmazonDynamoDB	client	=	AmazonDynamoDBClientBuilder.standard().withRegion("ap-northeast-2").build();
					this.dynamoDb	= new DynamoDB(client);
	}
}

class Event{
	public String device;
	public String msg;
	public String timestamp;
}


