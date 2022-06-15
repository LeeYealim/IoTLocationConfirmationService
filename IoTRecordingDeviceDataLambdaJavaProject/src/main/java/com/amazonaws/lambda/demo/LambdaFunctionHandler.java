package com.amazonaws.lambda.demo;



import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;

// Json 파싱에 필요
// pom.xml에도 의존성 추가
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class LambdaFunctionHandler implements RequestHandler<Document, String> {
	private	DynamoDB	dynamoDb;
	AmazonDynamoDB	client;

	private	String	LOCATION_TABLE	= "Location";
	private	String	ISSUE_TABLE	= "Issue";
	private	String	ZONE_TABLE	= "Zone";
	
	@Override
	public	String	handleRequest(Document	input,	Context	context) {
					this.initDynamoDbClient();
					context.getLogger().log("Input:	" +	input);

					return persistData(input, context);
	}
	
	private	String	persistData(Document document, Context context) throws ConditionalCheckFailedException	{
		
		// 1. Location 테이블에 현재 위치 행 추가
		SimpleDateFormat	sdf	= new SimpleDateFormat ( "yyyy-MM-dd	HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
		String	timeString	=	sdf.format(new java.util.Date (document.timestamp*1000));
				
		Double _latitude = Double.parseDouble(document.current.state.reported.latitude);
		Double _longitude = Double.parseDouble(document.current.state.reported.longitude);
				
		this.dynamoDb.getTable(LOCATION_TABLE)
		.putItem(new PutItemSpec().withItem(new Item().withPrimaryKey("device", document.device, "time", document.timestamp)
														.withNumber("latitude",	 _latitude)
														.withNumber("longitude", _longitude)
														.withString("timestamp", timeString))).toString();
		
		String test = "";
		// 2. 현재 위치가 어느 존에 속해있는지 확인
		String where = "";
		Table table = dynamoDb.getTable(ZONE_TABLE);
		ItemCollection<ScanOutcome> items = table.scan();
		Iterator<Item> iter = items.iterator();
		for (int i =0; iter.hasNext(); i++) {
			
			String response = iter.next().toJSON();			// Zone 한 행 가져옴
						
			JSONParser jsonParse = new JSONParser();
			JSONObject jsonObj = null;
			try {
				
				jsonObj = (JSONObject) jsonParse.parse(response);	// Json형식으로 변환
			} catch (ParseException e) {
				e.printStackTrace();
			}
			String name = (String) jsonObj.get("name");
			Double lat = (Double) jsonObj.get("latitude");
			Double lon = (Double) jsonObj.get("longitude");
			String _rad = jsonObj.get("radius").toString();
			Double rad = Double.parseDouble(_rad);
			
			// 안심존 중심 위치와 현재 위치 거리 계산
			Double distanceMeter = distance(lat, lon, _latitude, _longitude, "meter");
			
			// 현재 위치한 존을 알아내면 반복문 종료
			if(distanceMeter <= rad) {
				where = name;
				break;
			}
						
			test += name+"/"+rad+"/"+lat+"/"+lon+"//"+distanceMeter+" !";
		}
		
		// 3. 마지막 이슈 내용 가져옴
		Table issue_table = dynamoDb.getTable(ISSUE_TABLE);
		ItemCollection<ScanOutcome> issue_items = issue_table.scan();
		Iterator<Item> issue_iter = issue_items.iterator();	
		JSONParser jsonParse = new JSONParser();
		JSONObject jsonObj = null;
		String response = "";
		for (int i =0; issue_iter.hasNext(); i++) {	
			response = issue_iter.next().toJSON();			// Issue 한 행 가져옴		
		}
		try {
			jsonObj = (JSONObject) jsonParse.parse(response);	// Json형식으로 변환
		} catch (ParseException e) {
			e.printStackTrace();
		}
		String inout = (String) jsonObj.get("inout");
		String zone = (String) jsonObj.get("zone");
		
		String yy = "";
		// 4. 현재 위치와 마지막 이슈 비교하여 이슈 테이블 항목 삽입
		// 현재 위치가 아무 존에도 속해있지 않고, 마지막 이슈가 in이었을 때 : 이전 구역을 벗어난 것
		if(inout.equals("in") && where.equals("")) {
			yy="1";
			String text = zone+"을 벗어났습니다.";
			this.dynamoDb.getTable(ISSUE_TABLE)
			.putItem(new PutItemSpec().withItem(new Item().withPrimaryKey("device", document.device, "time", document.timestamp)
															.withString("inout", "out")
															.withString("zone", zone)
															.withString("text", text)
															.withString("timestamp", timeString))).toString();
		}
		// 원래 어느 존에 속해있었는데, 다른 존에 속하면 : 이전 이슈 존 벗어나고 다시 다른 존으로 접근한 것(2개 행 추가)
		else if(inout.equals("in") && !where.equals("") && !where.equals(zone)) {
			yy="2";
			String text = zone+"을 벗어났습니다.";
			String	_timeString	=	sdf.format(new java.util.Date ((document.timestamp-1)*1000));
			this.dynamoDb.getTable(ISSUE_TABLE)
			.putItem(new PutItemSpec().withItem(new Item().withPrimaryKey("device", document.device, "time", document.timestamp-1)
															.withString("inout", "out")
															.withString("zone", zone)
															.withString("text", text)
															.withString("timestamp", _timeString))).toString();
			text = where+"에 접근했습니다.";
			this.dynamoDb.getTable(ISSUE_TABLE)
			.putItem(new PutItemSpec().withItem(new Item().withPrimaryKey("device", document.device, "time", document.timestamp)
															.withString("inout", "in")
															.withString("zone", where)
															.withString("text", text)
															.withString("timestamp", timeString))).toString();
		}
		// 존에서 벗어난 상태였는데, 현재 위치가 어떤 존에 속해있을 때 : 존 안으로 들어온 것
		else if(inout.equals("out") && !where.equals("")) {
			yy="3";
			String text = where+"에 접근했습니다.";
			this.dynamoDb.getTable(ISSUE_TABLE)
			.putItem(new PutItemSpec().withItem(new Item().withPrimaryKey("device", document.device, "time", document.timestamp)
															.withString("inout", "in")
															.withString("zone", where)
															.withString("text", text)
															.withString("timestamp", timeString))).toString();
		}
					
		return null;
	}
	
	private void initDynamoDbClient() {
					client	=	AmazonDynamoDBClientBuilder.standard().withRegion("ap-northeast-2").build();
					this.dynamoDb	= new DynamoDB(client);
	}
	
	/**
	 * 두 지점간의 거리 계산
	 * 
	 * @param lat1 지점 1 위도
	 * @param lon1 지점 1 경도 
	 * @param lat2 지점 2 위도
	 * @param lon2 지점 2 경도
	 * @param unit 거리 표출단위 
	 * @return
	 */
	private static double distance(double lat1, double lon1, double lat2, double lon2, String unit) {
		
		double theta = lon1 - lon2;
		double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
		
		dist = Math.acos(dist);
		dist = rad2deg(dist);
		dist = dist * 60 * 1.1515;
		
		if (unit == "kilometer") {
			dist = dist * 1.609344;
		} else if(unit == "meter"){
			dist = dist * 1609.344;
		} 

		return (dist);
	}
	

	// This function converts decimal degrees to radians
	private static double deg2rad(double deg) {
		return (deg * Math.PI / 180.0);
	}
	
	// This function converts radians to decimal degrees
	private static double rad2deg(double rad) {
		return (rad * 180 / Math.PI);
	}
	
}


class Document {				// input 넘어올 때, 4가지 속성을 가진 객체가 넘어온다고 이해하면 됨
	public	Thing	previous;							
	public	Thing	current;
	public long	timestamp;
	public	String	device;		//	AWS	IoT에 등록된 사물 이름
}

class Thing {
	public	State state	= new State();
	public long	timestamp;	
	public String clientToken;	
	
	public class State {
		public Tag reported = new Tag();
		public Tag desired = new Tag();
		
		public class Tag {
			public String latitude;
			public String longitude;
			public String step;
			public String danger;
			public String msg;
		}
	}
}

