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
    return datetime.fromtimestamp(num+32400)

#  항목 비율 값으로 변경
def changeDictRate(_dict, _total):
    for key in _dict.keys():
        _dict[key] = _dict[key]/_total * 100



client = boto3.client('dynamodb')
dynamodb = boto3.resource('dynamodb')

zone_table = dynamodb.Table('Zone')             # Zone 테이블
location_table = dynamodb.Table('Location')     # Loction 테이블
dis_table = dynamodb.Table('Disappearance')     # Disappearance 테이블

zone_response = zone_table.scan()   # 존 목록 전체 스캔
zone_list = zone_response['Items']


def lambda_handler(event, context):
    
    # 딕셔너리 초기화
    total_dict = {"NULL":0} 
    for i in range(len(zone_response['Items'])):
        z_name = zone_response['Items'][i]['name']
        total_dict[z_name] = 0
    

    # 전달받은 시작, 종료 시간이 있으면 해당 구간만 검색(년-월-일 시:분:초 형식으로 보낼 것)
    _from = event['from']
    _to = event['to']
    _device = event['device']

    
    if(_from !='' and _to!=''):         # from~to 시간 입력이 있으면 필요한 기간의 데이터만 db에서 불러옴
        # 검색 시작일, 종료일 int 타입으로 계산
        start_date = str_to_timenumber(_from)
        end_date = str_to_timenumber(_to)
        
        # DB에서 해당 시간대 데이터 가져옴
        dis_response = dis_table.scan(
                FilterExpression = Attr('start_time').between(start_date, end_date) & Attr('end_time').between(start_date, end_date)
            )
    else:
        # DB에서 해당 시간대 데이터 가져옴
        dis_response = dis_table.scan()
            
    dis_list = dis_response['Items']
    
    
    # 실종기록 없으면 0으로만 된 것 보냄
    if len(dis_list)==0:
        return {"result":total_dict}
        
    # 등록된 실종 기록만큼 반복하여 위치 목록을 뽑아옴
    loc_list = []
    for item in dis_list:
        print(item)
        response = location_table.scan(
            FilterExpression = Attr('time').between(item['start_time'], item['end_time'])
        )
        loc_list += response['Items']
    
    
    
        
    
    
    # 초기 저장할 변수 설정
    remember_time = loc_list[0]['time']                                                         # 시작 시간
    remember_zone = is_in_zone(loc_list[0]['latitude'], loc_list[0]['longitude'], zone_list)    # 시작 위치
    total_time = 0
    
    
    i = 1
    while(i<len(loc_list)):   # 위치 배열 크기만큼 반복
        zone_name = is_in_zone(loc_list[i]['latitude'], loc_list[i]['longitude'], zone_list)    # 해당 위치 항목이 어떤 존에 있는지 확인
        stay_time = loc_list[i]['time'] - remember_time     # 현 존에 머무른 시간 계산
        
        total_time += stay_time     # 전체 시간 저장
        total_dict[remember_zone] +=stay_time

        remember_time = loc_list[i]['time']     # 현 아이템 시간을 기억
        remember_zone = zone_name               # 현 존 이름을 기억
        
        i = i+1
    
    # 결과값 비율로 변경
    changeDictRate(total_dict, total_time)
    
    return {"result":total_dict}
