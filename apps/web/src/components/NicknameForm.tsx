import {useRef,useState,type FormEvent} from 'react';
import {api,type Snapshot} from '../lib/api';

export function NicknameForm({householdId,nickname,onSaved}:{householdId:string;nickname:string;onSaved:(world:Snapshot)=>void}) {
  const [name,setName]=useState(nickname),[busy,setBusy]=useState(false),[error,setError]=useState(''),[saved,setSaved]=useState(false);
  const pending=useRef(false);
  async function submit(event:FormEvent){
    event.preventDefault();
    if(pending.current)return;
    const trimmed=name.trim();
    setError('');setSaved(false);
    if(!trimmed||trimmed.length>40||/[\u0000-\u001f\u007f-\u009f]/.test(trimmed)){setError('닉네임은 줄바꿈 없이 1~40자로 입력해 주세요.');return;}
    pending.current=true;setBusy(true);
    try{
      const world=await api<Snapshot>(`/api/households/${householdId}/me/profile`,'PATCH',{memberName:trimmed});
      onSaved(world);setName(trimmed);setSaved(true);
    }catch(e){setError((e as Error).message);}
    finally{pending.current=false;setBusy(false);}
  }
  return <form onSubmit={submit} className="nickname-form">
    <label>닉네임<input required maxLength={40} value={name} disabled={busy} autoComplete="nickname" onChange={e=>{setName(e.target.value);setSaved(false);setError('');}} aria-describedby="nickname-help"/></label>
    <p className="muted" id="nickname-help">이 집에서 가족에게 보이는 이름이에요. 1~40자로 입력해 주세요.</p>
    <button className="button primary" disabled={busy||!name.trim()||name.trim()===nickname}>{busy?'저장 중…':'닉네임 저장'}</button>
    {saved&&<p role="status">닉네임을 변경했어요.</p>}
    {error&&<p className="error-message" role="alert">{error}</p>}
  </form>;
}
