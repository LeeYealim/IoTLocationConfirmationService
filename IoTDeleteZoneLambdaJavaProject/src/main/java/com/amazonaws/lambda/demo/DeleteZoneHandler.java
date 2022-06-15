package com.amazonaws.lambda.demo;


// uri에 들어온 device, zone 이름 기반으로 DB에서 zone 삭제



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


public class DeleteZoneHandler implements RequestHandler<Event, String> {
	private	DynamoDB dynamoDb;

	private	String	TABLE_NAME = "Zone";
	
	@Override
	public	String	handleRequest(Event	input,	Context	context) {
					this.initDynamoDbClient();

					return persistData(input, context);
	}
	
	private	String	persistData(Event event, Context context) throws ConditionalCheckFailedException	{
					deleteItem(event);
			
					return "OK";
	}
	
	private void initDynamoDbClient() {
					AmazonDynamoDB	client	=	AmazonDynamoDBClientBuilder.standard().withRegion("ap-northeast-2").build();
					this.dynamoDb	= new DynamoDB(client);
	}
	
	
	private void deleteItem(Event event) {  
		
	      try {  
	         
	         DeleteItemSpec deleteItemSpec = new DeleteItemSpec() 
	        		 .withPrimaryKey("device", event.device, "name", event.zone)      
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
	public String zone;
}






//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//
//public class DeleteZoneHandler implements RequestHandler<Object, String> {
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
