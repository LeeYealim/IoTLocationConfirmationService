package com.amazonaws.lambda.demo;

// api로 람다함수 호출하여 디바이스 최신 상태 가져오기

// api uri에서 전달받은 디바이스 이름으로 디바이스 섀도우 최신상태 리턴해주는 듯


import com.amazonaws.services.iotdata.AWSIotData;
import com.amazonaws.services.iotdata.AWSIotDataClientBuilder;
import com.amazonaws.services.iotdata.model.GetThingShadowRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class GetDeviceHandler implements RequestHandler<Event, String> {

    @Override
    public String handleRequest(Event event, Context context) {
        AWSIotData iotData = AWSIotDataClientBuilder.standard().build();
        
        GetThingShadowRequest getThingShadowRequest  = 
        new GetThingShadowRequest()
            .withThingName(event.device);

        iotData.getThingShadow(getThingShadowRequest);

        return new String(iotData.getThingShadow(getThingShadowRequest).getPayload().array());
    }
}

class Event {
    public String device;
}
















//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//
//public class GetDeviceHandler implements RequestHandler<Object, String> {
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
