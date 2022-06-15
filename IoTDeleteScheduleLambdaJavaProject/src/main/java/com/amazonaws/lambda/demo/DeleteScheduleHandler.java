package com.amazonaws.lambda.demo;





import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.DeleteItemRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteItemResult;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;


public class DeleteScheduleHandler implements RequestHandler<Event, String> {
	private	DynamoDB dynamoDb;

	private	String	TABLE_NAME = "Schedule";
	
	@Override
	public	String	handleRequest(Event	input,	Context	context) {
					this.initDynamoDbClient();

					return persistData(input, context);
	}
	
	private	String	persistData(Event event, Context context) throws ConditionalCheckFailedException	{
					deleteItem(event);
			
					return null;
	}
	
	private void initDynamoDbClient() {
					AmazonDynamoDB	client	=	AmazonDynamoDBClientBuilder.standard().withRegion("ap-northeast-2").build();
					this.dynamoDb	= new DynamoDB(client);
	}
	
	
	private void deleteItem(Event event) {  
		
		long time=0;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

            time = sdf.parse(event.timestamp).getTime() / 1000;
        } catch (ParseException e1) {
            e1.printStackTrace();
        } 
		
	      try {  
	         
	         DeleteItemSpec deleteItemSpec = new DeleteItemSpec() 
	        		 .withPrimaryKey("device", event.device, "time", time)      
	                 .withReturnValues(ReturnValue.ALL_OLD);  
	              DeleteItemOutcome outcome = this.dynamoDb.getTable(TABLE_NAME).deleteItem(deleteItemSpec);
	         
	         // Confirm 
	         System.out.println("Displaying deleted item..."); 
	         System.out.println(outcome.getItem().toJSONPretty());  
	      } catch (Exception e) { 
	         System.err.println("Cannot delete item in " + TABLE_NAME); 
	         System.err.println(e.getMessage()); 
	      } 
	   } 
}

class Event{
	public String device;
	public String timestamp;
}

