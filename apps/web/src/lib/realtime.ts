import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import { getCsrf } from './api';

export type ConnectionState = 'connecting'|'online'|'offline';
export interface Envelope {eventId:string;revision:number;type:string;payload:unknown}

/** Bounded deduplication; snapshots are always authoritative after reconnects/gaps. */
export class RevisionTracker {
  revision = -1;
  private seen = new Set<string>();
  accept(event:Envelope) { if(this.seen.has(event.eventId)||event.revision<=this.revision)return false;this.seen.add(event.eventId);if(this.seen.size>512)this.seen.delete(this.seen.values().next().value!);return true; }
  snapshot(revision:number) {this.revision=Math.max(this.revision,revision);}
}

export function connectWorld(id:string,refresh:()=>Promise<number>,state:(value:ConnectionState)=>void,onError:(message:string)=>void) {
  let stopped=false,refreshing=false,pending=false;
  const tracker=new RevisionTracker();
  async function synchronize() {
    if(stopped)return;
    if(refreshing){pending=true;return;}
    refreshing=true;
    try {do {pending=false;tracker.snapshot(await refresh());} while(pending&&!stopped);}
    catch {state('offline');}
    finally {refreshing=false;}
  }
  const client=new Client({brokerURL:`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`,reconnectDelay:1500,maxReconnectDelay:30_000,reconnectTimeMode:ReconnectionTimeMode.EXPONENTIAL,heartbeatIncoming:10_000,heartbeatOutgoing:10_000,
    beforeConnect:async()=>{state('connecting');try{const csrf=await getCsrf(true);client.connectHeaders={[csrf.headerName]:csrf.token};}catch{state('offline');client.connectHeaders={};}},
    onConnect:()=>{
      client.subscribe(`/topic/households/${id}/events`,message=>{try {const event=JSON.parse(message.body) as Envelope;if(tracker.accept(event))void synchronize();}catch{void synchronize();}});
      client.subscribe('/user/queue/errors',message=>{try{onError(JSON.parse(message.body).message);}catch{onError('이동 요청이 거절됐어요.');}void synchronize();});
      client.subscribe('/user/queue/sync',()=>{state('online');void synchronize();});
      client.publish({destination:`/app/households/${id}/sync`,body:'{}'});
    },
    onWebSocketClose:()=>{if(!stopped)state('offline');},onStompError:frame=>{state('offline');onError(frame.headers.message||'실시간 연결 권한을 확인해 주세요.');}
  });
  client.activate();
  return {send:(body:unknown)=>{if(!client.connected){onError('실시간 연결 후 다시 움직여 주세요.');return;}client.publish({destination:`/app/households/${id}/actions`,body:JSON.stringify(body)});},destroy:()=>{stopped=true;void client.deactivate();},refresh:synchronize};
}
