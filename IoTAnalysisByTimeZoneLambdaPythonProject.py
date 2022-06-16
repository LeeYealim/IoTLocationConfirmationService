import json
import boto3
from boto3.dynamodb.conditions import Key, Attr

from datetime import datetime
import time

from math import sin, cos, sqrt, atan2, radians



# 두 위경도 간 거리 계산 함수
def calculate_distance(x1, y1, x2, y2):
    R = 6373.0

    lat1 = radians(x1)
    lon1 = radians(y1)
    lat2 = radians(x2)
    lon2 = radians(y2)

    dlon = lon2 - lon1
    dlat = lat2 - lat1

    a = (sin(dlat/2))**2 + cos(lat1) * cos(lat2) * (sin(dlon/2))**2
    c = 2 * atan2(sqrt(a), sqrt(1-a))

    distance = R*c
    return distance*1000


# 존 내부에 속한 것인지 계산하여 bool 값 리턴
def is_in(x1, y1, x2, y2, radius):
    if(calculate_distance(x1, y1, x2, y2) < radius):
        return True
    return False

# 존 리스트 넘겨 받아서 어떤 존 이름에 속하는지 리턴해줌
def is_in_zone(x, y, zlist):
    for z in zlist:
        if is_in(x, y, z['latitude'], z['longitude'], z['radius']):
            return z['name']
    return "NULL"       # 어느 존에도 해당하지 않으면 "NULL" 문자열 리턴함
            



# 문자열 시간 > int형 시간
def str_to_timenumber(str):
    return int(time.mktime(datetime.strptime(str,'%Y-%m-%d %H:%M:%S').timetuple()))-32400

# int형 시간 > 문자열 시간
def timenumber_to_str(num):
    return datetime.fromtimestamp(num)    #num+32400 어떻게 해야하지...


def init_dict(zone_list):
    total_dict = {"NULL":0}                       # !!!!!!!!!!!!!!!!! (중요) 시간을 저장할 딕셔너리  {'존이름':시간}
    for i in range(len(zone_list)):
        z_name = zone_list[i]['name']
        total_dict[z_name] = 0
    return total_dict


#  항목 비율 값으로 변경
def changeDictRate(_dict, _total):
    for key in _dict.keys():
        _dict[key] = _dict[key]/_total * 100



client = boto3.client('dynamodb')
dynamodb = boto3.resource('dynamodb')

zone_table = dynamodb.Table('Zone')             # Zone 테이블
location_table = dynamodb.Table('Location')     # Loction 테이블

zone_response = zone_table.scan()   # 존 목록 전체 스캔
zone_list = zone_response['Items']



def lambda_handler(event, context):
    # 전달받은 시작, 종료 시간이 있으면 해당 구간만 검색(년-월-일 시:분:초 형식으로 보낼 것)
    _from = event['from']
    _to = event['to']
    _device = event['device']
    
    
    # 0시 ~ 23시 시간대 리스트
    timezone_list = ['00', '01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11',
                 '12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23']
    
    result = {}     # 결과 딕셔너리
    
    # 0 ~ 23만큼 반복
    for timezone in timezone_list:  
        total_dict = init_dict(zone_response['Items'])  # 존 딕셔너리 초기화
                           # 결과 딕셔너리 0으로 된 것으로 초기화
    
        if(_from !='' and _to!=''):         # from~to 시간 입력이 있으면 필요한 기간의 데이터만 db에서 불러옴
            # 검색 시작일, 종료일 int 타입으로 계산
            start_date = str_to_timenumber(_from)
            end_date = str_to_timenumber(_to)
            # 해당 시간대의 위치기록을 뽑아냄
            # 바로 다음 기록이 한 시간(3600) 이상 차이나면 다른 날인 것
            response = location_table.scan(
                    FilterExpression = Attr('time').between(start_date, end_date) & Attr('device').eq('MyNode02') & Attr('timestamp').contains(' {}:'.format(timezone))
                )
        else:
            response = location_table.scan(
                    FilterExpression = Attr('device').eq('MyNode02') & Attr('timestamp').contains(' {}:'.format(timezone))
                )
          
        loc_list = response['Items']
    
        # 해당 시간대에 위치 기록이 있으면
        if(len(loc_list) != 0):       
            
            # 배열 0 번째 인덱스 먼저 기억
            remember_time = loc_list[0]['time']                                                         # 시작 시간 기억
            remember_zone = is_in_zone(loc_list[0]['latitude'], loc_list[0]['longitude'], zone_list)    # 시작 위치 기억
            total_time = 0
            
    
            i = 1
            while(i < len(loc_list)):
                stay_time = loc_list[i]['time'] - remember_time     # 현 존에 머무른 시간 계산
    
                if(stay_time < 3600):                               # 같은 날짜의 시간대일 때에만 시간 계산하여 저장
                    total_dict[remember_zone] +=stay_time
                    total_time +=stay_time
    
                remember_time = loc_list[i]['time']     # 현 아이템 시간을 기억
                zone_name = is_in_zone(loc_list[i]['latitude'], loc_list[i]['longitude'], zone_list)
                remember_zone = zone_name               # 현 존 이름을 기억
                        
                i+=1
            changeDictRate(total_dict, total_time)
        result[timezone] = total_dict
    
    
    # ---- 리턴 형식 수정
    _dict = {"NULL":[]}                       
    for i in range(len(zone_list)):
        z_name = zone_list[i]['name']
        _dict[z_name] = []
    
    
    for time in timezone_list:
        for zone in _dict.keys():
            _dict[zone].append(result[time][zone])
    
    
    return {"result":result, "result2": _dict}
    
