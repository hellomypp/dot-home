#!/usr/bin/env python3
"""Disposable v6.2/v0.7 API QA, pinned to the isolated loopback server; no secrets printed."""
import http.cookiejar,json,urllib.request,urllib.error,uuid
BASE='http://127.0.0.1:8081'
class Client:
 def __init__(self,key=None):
  self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.csrf=None
  if key:
   self.csrf=self.call('/api/auth/csrf')['token'];self.call('/api/auth/dev','POST',{'accountKey':key,'name':'공간 검증'});self.csrf=self.call('/api/auth/csrf')['token']
 def call(self,path,method='GET',body=None,status=200,csrf=True):
  headers={'Content-Type':'application/json'}
  if csrf and self.csrf:headers['X-CSRF-TOKEN']=self.csrf
  req=urllib.request.Request(BASE+path,method=method,headers=headers,data=None if body is None else json.dumps(body).encode())
  try:
   with self.http.open(req,timeout=10) as r:code,data=r.status,r.read()
  except urllib.error.HTTPError as r:code,data=r.code,r.read()
  assert code==status,(method,path.split('/')[1:3],code,status)
  return json.loads(data) if data else None
suffix=uuid.uuid4().hex[:12];a,b,c=Client('space-owner-'+suffix),Client('space-family-'+suffix),Client('space-visitor-'+suffix)
w=a.call('/api/households','POST',{'name':'공간 검증 집','memberName':'대표','appearance':'v2:child','presetId':'sunny_apartment'});h=w['household']['id'];root='/api/households/'+h
assert w['design']=={'presetId':'sunny_apartment','version':0,'variants':{}}
i=a.call(root+'/invitations','POST');b.call('/api/invitations/'+i['token']+'/accept','POST',{'memberName':'가족','appearance':'v1:3212'})
draft={'presetId':'brick_loft','version':0,'variants':{'sofa':'blue','table':'round','fridge':'star'}}
a.call(root+'/design','PATCH',draft,status=403,csrf=False)
b.call(root+'/design','PATCH',draft,status=403);c.call(root+'/design',status=403);Client().call(root+'/design',status=401)
for invalid in ({**draft,'presetId':'bad'},{**draft,'variants':{'sofa':'bad'}},{**draft,'version':-1},{**draft,'ownerId':'forged'}):a.call(root+'/design','PATCH',invalid,status=400)
saved=a.call(root+'/design','PATCH',draft);assert b.call(root+'/world')['design']==saved['design'];a.call(root+'/design','PATCH',draft,status=409)
next=a.call(root+'/design','PATCH',{**draft,'presetId':'garden_home','version':1});assert next['design']['variants']==draft['variants']
a.call(root+'/me/profile','PATCH',{'memberName':'대표','appearance':'v2:adult_male'})
relogin=Client('space-owner-'+suffix);assert relogin.call(root+'/world')['design']==next['design']
link=a.call(root+'/housewarming-invitations','POST');token=link['url'].split('/')[-1];public=c.call('/api/visits/'+token+'/accept','POST',{})
assert set(public)=={'householdName','theme','guestbook','design'};assert public['design']==next['design'];assert c.call('/api/me')['memberships']==[]
c.call(root+'/design','PATCH',{**draft,'version':2},status=403)
assert len(a.call(root+'/world')['members'])==2
print('PASS: create/relogin/shared design/override retention; 401/403/400/409/CSRF; public-only visitor DTO; memberships unchanged')
