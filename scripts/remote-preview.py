"""Tailnet-only preview; launchd jobs survive Codex sessions. No tokens are stored."""
import json,os,pathlib,plistlib,shutil,subprocess,sys
root=pathlib.Path(__file__).resolve().parent.parent
cli=shutil.which('tailscale') or '/Applications/Tailscale.app/Contents/MacOS/Tailscale'
uid=str(os.getuid()); folder=pathlib.Path.home()/'Library/LaunchAgents'
labels=['kr.dothome.preview.server','kr.dothome.preview.web']
def run(*args):return subprocess.run(args,check=True)
command=sys.argv[1] if len(sys.argv)>1 else 'status'
if not pathlib.Path(cli).exists():sys.exit('Tailscale 앱 설치 및 Mac VPN 승인·로그인이 필요합니다.')
if command=='stop':
 for label in labels:
  subprocess.run(['launchctl','bootout',f'gui/{uid}/{label}'],check=False)
  (folder/f'{label}.plist').unlink(missing_ok=True)
 run(cli,'serve','--https=443','off')
 # Allow graceful JVM shutdown before a subsequent start; never kill unrelated ports.
 import socket,time
 for attempt in range(30):
  busy=False
  for port in (5173,8080):
   with socket.socket() as sock:
    busy=busy or sock.connect_ex(('127.0.0.1',port))==0
  if not busy:break
  time.sleep(.5)
 else:sys.exit('중지 후에도 5173/8080 포트가 사용 중입니다. 남은 프로세스를 확인하세요.')
 sys.exit()
status=json.loads(subprocess.check_output([cli,'status','--json']))
if status.get('BackendState')!='Running':sys.exit('Tailscale 앱에서 로그인하고 연결한 후 다시 실행하세요.')
host=status['Self']['DNSName'].rstrip('.')
if not host.endswith('.ts.net'):sys.exit('MagicDNS의 실제 ts.net 이름을 확인할 수 없습니다.')
origin='https://'+host
if command=='status':
 print(origin);run(cli,'serve','status');sys.exit()
if command!='start':sys.exit('사용법: python3 scripts/remote-preview.py start|stop|status')
# Do not silently share an existing service or replace a server owned by another task.
import socket
for port in (5173,8080):
 with socket.socket() as sock:
  if sock.connect_ex(('127.0.0.1',port))==0:sys.exit(f'{port} 포트의 기존 개발 서버를 먼저 정상 종료한 뒤 다시 실행하세요.')
serve=json.loads(subprocess.check_output([cli,'serve','status','--json']))
expected={'TCP':{'443':{'HTTPS':True}},'Web':{host+':443':{'Handlers':{'/':{'Proxy':'http://127.0.0.1:5173'}}}}}
if serve and serve != expected:sys.exit('다른 Serve 설정이 있습니다. 기존 서비스와 충돌을 먼저 해소하세요.')
# launchd runs the JVM directly: bootRun's Gradle daemon can orphan the server on bootout.
run('/bin/bash',str(root/'scripts/server.sh'),'bootJar')
folder.mkdir(parents=True,exist_ok=True)
logs=root/'.tools/remote-preview';logs.mkdir(parents=True,exist_ok=True)
runtime=pathlib.Path.home()/'.cache/codex-runtimes/codex-primary-runtime/dependencies'
node=shutil.which('node')
path=(str(pathlib.Path(node).parent) if node else str(runtime/'node/bin'))+':/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin'
env={'PATH':path,'HOME':str(pathlib.Path.home()),'APP_ORIGIN':origin,'DOT_HOME_PREVIEW_HOST':host,'SERVER_SERVLET_SESSION_COOKIE_SECURE':'true'}
for label,script in zip(labels,['server.sh','dev.sh']):
 args=['/bin/bash',str(root/'scripts'/script)]+(['preview'] if script=='server.sh' else [])
 config={'Label':label,'ProgramArguments':args,'WorkingDirectory':str(root),'EnvironmentVariables':env,'RunAtLoad':True,'KeepAlive':True,'ThrottleInterval':15,'StandardOutPath':str(logs/(label+'.log')),'StandardErrorPath':str(logs/(label+'.error.log'))}
 file=folder/(label+'.plist');file.write_bytes(plistlib.dumps(config));file.chmod(0o600)
 run('launchctl','bootstrap',f'gui/{uid}',str(file))
run(cli,'serve','--bg','--https=443','http://127.0.0.1:5173')
print('iPhone Safari:',origin,'/studio',sep='\n')
