#!/usr/bin/env python3
"""Disposable loopback QA only. Never prints session cookies or invitation tokens."""
import json,urllib.request,urllib.error,http.cookiejar,uuid,os
BASE=os.environ.get('DOT_HOME_SMOKE_ORIGIN','http://127.0.0.1:8081')
if BASE not in ('http://127.0.0.1:8081',):raise SystemExit('Use the isolated QA server on loopback 8081')
class Client:
 def __init__(self,key):
  self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
  self.csrf=None
  self.call('/api/auth/csrf');self.call('/api/auth/dev','POST',{'accountKey':key,'name':'검증 '+key});self.call('/api/auth/csrf')
 def call(self,path,method='GET',body=None,status=200):
  headers={'Content-Type':'application/json'}
  if self.csrf:headers['X-CSRF-TOKEN']=self.csrf
  request=urllib.request.Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
  try:
   with self.http.open(request) as response:code=response.status;data=response.read()
  except urllib.error.HTTPError as response:code=response.code;data=response.read()
  assert code==status,(method,path.split('/')[1:3],code,status)
  value=json.loads(data) if data else None
  if path=='/api/auth/csrf':self.csrf=value['token']
  return value
suffix=uuid.uuid4().hex[:12]
a,b,c=Client('v06qa-owner'),Client('v06qa-family-'+suffix),Client('v06qa-visitor-'+suffix)
h=a.call('/api/households','POST',{'name':'v0.6 검증의 집','memberName':'하늘','appearance':'sage'})['household']['id'];root='/api/households/'+h
invite=a.call(root+'/invitations','POST');b.call('/api/invitations/'+invite['url'].split('/')[-1]+'/accept','POST',{'memberName':'보리','appearance':'blue'})
n=a.call(root+'/objects/fridge/notes','POST',{'body':'우유 사왔어요. 함께 마셔요!'});assert len(b.call(root+'/objects/fridge/notes'))==1
b.call(root+'/notes/'+n['id']+'/take','POST',{'version':n['version']});assert a.call(root+'/objects/fridge/notes')==[];assert len(a.call(root+'/notes/archive'))==1
a.call(root+'/objects/fridge/notes','POST',{'body':'주말에 같이 산책할까요?'})
e=a.call(root+'/calendar-events','POST',{'title':'가족 저녁 식사','description':'함께 따뜻한 밥을 먹어요','category':'GENERAL','allDay':True,'startDate':'2026-11-03','endDate':'2026-11-03','startAt':None,'endAt':None,'timeZone':'Asia/Seoul','version':0});assert len(b.call(root+'/calendar-events?from=2026-11-01&to=2026-11-30'))==1
link=a.call(root+'/housewarming-invitations','POST');token=link['url'].split('/')[-1]
visit=c.call('/api/visits/'+token+'/accept','POST',{});assert set(visit)=={'householdName','theme','guestbook','design'}
c.call('/api/visits/'+token+'/guestbook','POST',{'requestId':'smoke-'+suffix,'message':'따뜻한 집이네요. 초대해줘서 고마워요!'});assert len(a.call(root+'/guestbook'))==1
assert len(a.call(root+'/visitors'))==1
for path in ['/world','/objects/fridge/notes','/notes/archive','/calendar-events?from=2026-11-01&to=2026-11-30','/guestbook']:
 c.call(root+path,status=403)
assert c.call('/api/me')['memberships']==[]
assert len(a.call(root+'/world')['members'])==2
a.call(root+'/housewarming-invitations/'+link['id'],'DELETE',status=204);c.call('/api/visits/'+token,status=410)
print(json.dumps({'result':'PASS','householdId':h,'checks':'two-family REST notes/archive/calendar; guestbook; visitors; revocation; 5 private API denials; membership unchanged'},ensure_ascii=False))
