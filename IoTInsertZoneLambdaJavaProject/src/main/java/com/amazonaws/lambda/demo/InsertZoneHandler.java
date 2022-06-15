package com.amazonaws.lambda.demo;


// 들어온 인자를 바탕으로 Zone 테이블에 새로운 존을 Insert 하는 람다 함수



import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;


public class InsertZoneHandler implements RequestHandler<Event, String> {
	private	DynamoDB dynamoDb;

	private	String	TABLE_NAME = "Zone";
	
	@Override
	public	String	handleRequest(Event	input,	Context	context) {
					this.initDynamoDbClient();

					return persistData(input, context);
	}
	
	private	String	persistData(Event event, Context context) throws ConditionalCheckFailedException	{
			

		this.dynamoDb.getTable(TABLE_NAME)
		.putItem(new PutItemSpec().withItem(new Item()
										.withPrimaryKey("device",	event.device)
										.withPrimaryKey("name",	event.name)		
										.withDouble("latitude",	event.latitude)
										.withDouble("longitude",	event.longitude)
										.withDouble("radius",	event.radius))).toString();	
					
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
	public String name;
	public double latitude;
	public double longitude;
	public double radius;
}



