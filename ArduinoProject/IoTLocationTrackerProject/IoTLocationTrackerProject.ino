#include <Arduino_LSM6DS3.h>    // IMU
#include <ArduinoJson.h>        // Json
#include <SPI.h>                // OLED
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Arduino.h>            // GPS
#include "wiring_private.h"
#include <TinyGPS++.h>
#include <UnixTime.h>           // https://github.com/GyverLibs/UnixTime
#include "TYPE1SC.h"            // LTE-Cat.M1
#if !defined(__AVR_ATmega4809__)
#include <avr/dtostrf.h>
#endif


// OLED Display 설정
#define SCREEN_WIDTH 128 
#define SCREEN_HEIGHT 64 
// OLED 핀 설정
#define OLED_MOSI   9
#define OLED_CLK   10
#define OLED_DC    11
#define OLED_CS    12
#define OLED_RESET 13
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT,
  OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS);

// GPS 객체 설정
TinyGPSPlus gps;
static const uint32_t GPSBaud = 9600;
Uart mySerial (&sercom0, 5, 6, SERCOM_RX_PAD_1, UART_TX_PAD_0);

UnixTime stamp(9);  // Seoul GMT + 09

// LTE 통신 시리얼 설정
#define DebugSerial Serial
#define M1Serial Serial1
TYPE1SC TYPE1SC(M1Serial, DebugSerial);

/* Device Data EndPoint Address AWS IoT > Settings > Device data endpoint >
 * Copy&Paste */
char _IP[] = "a1o84uhirij3ga-ats.iot.ap-northeast-2.amazonaws.com";
char _NodeID[] = "MyNode02";
char _Topic[] = "$aws/things/MyNode02/shadow/update/accepted";    // accepted 주제 내용 수신 가능
//char _Topic[] = "$aws/things/MyNode02/shadow/update/delta";     // delta 내용 못 받아옴
char _message[64];
int tlsProfile = 9;
int conn_timeout = 1200;

// 위급 호출 관련
const byte ledPin = 3;
const byte interruptPin = 2;
volatile byte state = LOW;
int _pub = 0;

// 가속도 변수
float _ay = 0;

// AWS PUBLISH 주기
unsigned long p_periodTime = 1000*60*10;  // 데이터 전송 주기 (초기 : 10분마다 전송) // 잠시 30초로 해놓음
unsigned long p_pubTime = 0;
// AWS SUSCRIBE 주기
unsigned long s_periodTime = 1000*600;  // 데이터 수신 주기 (초기 : 60초마다 수신)
unsigned long s_pubTime = 0;

char recvBuffer[300];
int recvSize;

// 응급호출 관련 변수
int _DAN_PUB = 0;

// 상태 전역변수
char _MSG[30];
int _STEP = 0;
double _LATITUDE = 0;   //37.583458;
double _LONGITUDE = 0;  //127.009178;
int _DANGER = 0;


// GPS - Attach the interrupt handler to the SERCOM
void SERCOM0_Handler()
{
    mySerial.IrqHandler();
}

void setup() {

  // GPS 핀 설정
  // Reassign pins 5 and 6 to SERCOM alt
  pinPeripheral(5, PIO_SERCOM_ALT);
  pinPeripheral(6, PIO_SERCOM_ALT);
  // Start my new hardware serial
  mySerial.begin(9600);

  // 위급 호출 LED, BUTTON 설정
  pinMode(ledPin, OUTPUT);
  pinMode(interruptPin, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(interruptPin), blink, RISING);

  // IMU 설정
  IMU.begin();

  // LTE 통신 설정
  M1Serial.begin(115200);
  DebugSerial.begin(115200);
  DebugSerial.println("TYPE1SC Module Start!!!");
  TYPE1SC.reset();
  delay(2000);
  // 네트워크 및 AWS 연결 셋팅
  settingAws();

  // OLED 초기화
  memset(_MSG, 0x0, sizeof(_MSG));
  sprintf(_MSG,"device start");
  display.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  display.clearDisplay();
  display.display();
  displayOled(_MSG);

  // 업데이트
  char payload[512];
  memset(payload, 0x0, sizeof(payload));
  sprintf(payload,"{\\\"state\\\":{\\\"reported\\\":{\\\"msg\\\":\\\"%s\\\"}}}", _MSG);
  publishAws(payload);

}

void loop() { 
  // 걸음 수 측정
  float x, y, z;
  if (IMU.accelerationAvailable()) {
    IMU.readAcceleration(x, y, z);
    if(abs(_ay - y) > 0.45){
      _STEP += 1;
      _ay = y;
      DebugSerial.print("STEP : ");
      DebugSerial.println(_STEP);
    }
  }

  // LED 불빛 제어
  digitalWrite(ledPin, state);
  if(_DAN_PUB == 1){    // 위급 종료
    _DAN_PUB = 0;
    char payload[512];
    memset(payload, 0x0, sizeof(payload));
    sprintf(payload,"{\\\"state\\\":{\\\"reported\\\":{\\\"danger\\\":\\\"OFF\\\"}}}",_LATITUDE);
    DebugSerial.println("-- DANGER OFF --");
    publishAws(payload);
  }
  else if(_DAN_PUB == 2){
    _DAN_PUB = 0;
    char payload[512];
    memset(payload, 0x0, sizeof(payload));
    sprintf(payload,"{\\\"state\\\":{\\\"reported\\\":{\\\"danger\\\":\\\"ON\\\"}}}",_LATITUDE);
    DebugSerial.println("-- DANGER ON --");
    publishAws(payload);
  }

  // GPS 신호 수신
  int r_gps = getGPSLocation();

  // 주기마다 메세지 수신 확인
  if (millis() - s_pubTime > s_periodTime) {
    s_pubTime = millis();
    DebugSerial.println("TYPE1SC.AWSIOT_RECV()...");
    
    // 메세지 수신 확인
    int ret = TYPE1SC.AWSIOT_RECV(recvBuffer, sizeof(recvBuffer));
     //char ss[100];
     //sprintf(ss, "AWSIOT_RECV : %d", ret);
     //DebugSerial.println(ss);                // 리턴값(정수) 확인용
      
     if (ret == 0){ 
       DebugSerial.println("-- AWS SUBSCRIBE... --");
       DebugSerial.println(recvBuffer);
       
       DynamicJsonDocument doc(1024);
       deserializeJson(doc, recvBuffer);
       JsonObject root = doc.as<JsonObject>();
       JsonObject state = root["state"];
       JsonObject desired = state["desired"];
       const char* _msg = desired["msg"];   
  
       // msg에 전달받은 내용이 있으면 OLED 변경
       if(_msg != NULL){    
        onAlarm();        
        sprintf(_MSG, _msg);       // OLED 전역변수 변경  
        displayOled(_MSG);         // OLED 변경 함수 호출
  
        char payload[512];
        memset(payload, 0x0, sizeof(payload));
        sprintf(payload,"{\\\"state\\\":{\\\"reported\\\":{\\\"msg\\\":\\\"%s\\\"}}}",_MSG);
        publishAws(payload);
       }
     }  
  }
  
    // 일정 시간마다 센서값 수집하여 디바이스 섀도우 업데이트
   if (millis() - p_pubTime > p_periodTime) {
        p_pubTime = millis(); 
        
        char payload[512];
        memset(payload, 0x0, sizeof(payload));     
        //if(getGPSLocation()>0){  // GPS 값 수신되면 디바이스 섀도우 업데이트, 위도 0은 초기 상태
        sprintf(payload,"{\\\"state\\\":{\\\"reported\\\":{\\\"step\\\":\\\"%d\\\", \\\"latitude\\\":%f, \\\"longitude\\\":%f}}}",_STEP, _LATITUDE, _LONGITUDE);
        //} else{  // 아니면 step 값만 업데이트
        //  sprintf(payload,"{\\\"state\\\":{\\\"reported\\\":{\\\"step\\\":\\\"%d\\\"}}}",_STEP);
        //}
        DebugSerial.println("-- AWS PUBLISH... --");
        publishAws(payload);
   }
}

// 알림음 출력
void onAlarm(){
  int tones[] = {261, 294, 330};
  for(int i = 0; i < 3; i++)
  {
    tone(8, tones[i]);
    delay(200);
  }
  noTone(8);
  delay(10);
}


// GPS 신호 수신 (0: 값없음, 1: 정상수신)
int getGPSLocation(){
  while (mySerial.available() > 0){
    if (gps.encode(mySerial.read())){
      DebugSerial.print(F("Location: ")); 
      if (gps.location.isValid()){
        // 전역 상태변수에 위도, 경도 저장
        _LATITUDE = gps.location.lat();
        _LONGITUDE = gps.location.lng();
        
        DebugSerial.print(gps.location.lat(), 6);
        DebugSerial.print(F(","));
        DebugSerial.print(gps.location.lng(), 6);
        DebugSerial.println(" ");
        return 1;
      }
      else{
        DebugSerial.print(F("INVALID"));
      }
      DebugSerial.println(" ");     
    } 
  }
  return 0;
}

// LED 불빛 제어
void blink() {
  //state = !state;
  if(state==HIGH){  // 위급 종료
    state = LOW;
    _DAN_PUB = 1;  
  }
  else{           // 위급 호출
    state = HIGH;
    _DAN_PUB = 2;
  } 
}

// OLED 변경
void displayOled(char* str){
  DebugSerial.println("displayOled(char* str)...  ");
    // 문자표시하기
  display.clearDisplay();
  display.setTextSize(2);
  display.setTextColor(WHITE);
  display.setCursor(0,0);
  display.println(str);
  display.display();
}

// AWS
void publishAws(char* payload){
  DebugSerial.println("publishAws(char* payload)...");
  if (TYPE1SC.AWSIOT_Publish("$aws/things/MyNode02/shadow/update", payload) == 0){
    DebugSerial.println("-- shadow publish - ing--");
  }
}

void settingAws(){
  /* TYPE1SC Module Initialization */
  if (TYPE1SC.init()) {
    DebugSerial.println("TYPE1SC Module Error!!!");
  }

  /* Network Regsistraiton Check */
  while (TYPE1SC.canConnect() != 0) {
    DebugSerial.println("Network not Ready !!!");
    delay(2000);
  }

  DebugSerial.println("TYPE1SC Module Ready!!!");

  /* 1 : Configure AWS_IOT parameters (ID, Address, tlsProfile) */
  if (TYPE1SC.setAWSIOT_CONN(_NodeID, _IP, tlsProfile) == 0)
    DebugSerial.println(
        "1.Configure AWS_IOT parameter:ID, Address, tls Profile");

  /* 2 : Configure AWS_IOT parameters (Connection Timeout) */
  if (TYPE1SC.setAWSIOT_TIMEOUT(conn_timeout) == 0)
    DebugSerial.println("2.Configure AWS_IOT parameter:Timeout");

  /* 3 : Enable AWS_IOT events */
  if (TYPE1SC.setAWSIOT_EV(1) == 0)
    DebugSerial.println("3.Enable AWS_IOT events");

  /* 4 : Establish connection */
  if (TYPE1SC.AWSIOT_Connect() == 0) {
    DebugSerial.println("4.Establish connection");
  }

  /* 5 : Subscribe (register) to the topic on the endpoint */
  if (TYPE1SC.AWSIOT_SUBSCRIBE(_Topic) == 0)
    DebugSerial.println("5.Subscribe to the topic on the endpoint");

  /* 5 : Subscribe (register) to the topic on the endpoint */
  if (TYPE1SC.AWSIOT_SUBSCRIBE("sdkTest/sub") == 0)
    DebugSerial.println("5.Subscribe to the topic on the endpoint - sdkTest/sub");
}

void disconnectAws(){
  /* 7 : UnSubscribe to the topic on the endpoint */
  if (TYPE1SC.AWSIOT_UnSUBSCRIBE(_Topic) == 0) {
    DebugSerial.println("7.UnSubscribe to the topic on the endpoint");
  }

  /* 8 : Disconnect AWS_IOT Service */
  if (TYPE1SC.AWSIOT_DisConnect() == 0)
    DebugSerial.println("8.Disconnect AWS_IOT Service");

  /* 9 : Disable AWS_IOT events */
  if (TYPE1SC.setAWSIOT_EV(0) == 0)
    DebugSerial.println("9.Disable AWS_IOT events");
}
