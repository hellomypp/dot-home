"""Local dev-only REST smoke test. Creates isolated QA accounts; never real user data.
Usage: python3 scripts/smoke.py [http://127.0.0.1:5173]
Re-run with --verify to check persistence of its last run after restarting Spring.
"""
import http.cookiejar, json, sys, urllib.request, urllib.error, uuid
from pathlib import Path

base = next((arg for arg in sys.argv[1:] if arg.startswith('http')), 'http://127.0.0.1:5173')
state = Path(__file__).resolve().parent.parent / 'apps/server/data/smoke-state.json'

class Client:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf = None
    def call(self, path, method='GET', body=None, expected=200):
        headers = {'Content-Type':'application/json'}
        if method != 'GET':
            if not self.csrf: self.csrf=self.call('/api/auth/csrf')['token']
            headers['X-CSRF-TOKEN']=self.csrf
        request=urllib.request.Request(base+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
        try: response=self.http.open(request);status=response.status;raw=response.read()
        except urllib.error.HTTPError as e: status=e.code;raw=e.read()
        assert status==expected, f'{method} {path.split("/invitations/")[0]}: {status} != {expected}'
        return json.loads(raw) if raw else None
    def login(self,key,name):
        data=self.call('/api/auth/dev','POST',{'accountKey':key,'name':name});self.csrf=None;return data

if '--verify' in sys.argv:
    saved=json.loads(state.read_text());a=Client();a.login(saved['owner'],'검증 A');world=a.call('/api/households/'+saved['household']+'/world')
    assert len(world['members'])==2 and len(world['pets'])==0
    assert world['pantryItems'][0]['quantity']==1 and len(world['events'])==1
    print('PASS: server restart persisted memberships, zero pets, pantry and event history')
else:
    a,b,c=Client(),Client(),Client();key='smoke-'+uuid.uuid4().hex
    a.login(key,'검증 A');b.login(key+'b','검증 B');c.login(key+'c','검증 C')
    world=a.call('/api/households','POST',{'name':'격리된 QA 집','memberName':'검증 A','appearance':'sage'});hid=world['household']['id'];root='/api/households/'+hid
    assert len(world['members'])==1 and world['pets']==[]
    invite=a.call(root+'/invitations','POST');b.call('/api/invitations/'+invite['token']);assert len(a.call(root+'/world')['members'])==1
    joined=b.call('/api/invitations/'+invite['token']+'/accept','POST',{'memberName':'검증 B','appearance':'blue'});assert len(joined['members'])==2
    c.call(root+'/world',expected=403)
    item=a.call(root+'/pantry-items','POST',{'name':'우유','location':'냉장','quantity':2,'unit':'개','expiresOn':None})
    b.call(root+'/pantry-items/'+item['id'],'PATCH',{'quantity':1,'version':item['version']})
    a.call(root+'/pantry-items/'+item['id'],'PATCH',{'quantity':0,'version':item['version']},expected=409)
    payload={'requestId':uuid.uuid4().hex,'type':'NOTE','message':'QA 공유 기록'}
    event=b.call(root+'/events','POST',payload);again=b.call(root+'/events','POST',payload);assert event['id']==again['id'] and event['authorName']=='검증 B'
    state.parent.mkdir(parents=True,exist_ok=True);state.write_text(json.dumps({'owner':key,'household':hid}))
    print('PASS: isolated authenticated accounts, zero pets, pending hidden, invite acceptance, household denial, shared stock conflict, server author and event deduplication')
