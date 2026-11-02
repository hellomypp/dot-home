import {useRef,useState,type FormEvent} from 'react';
import {api,type Snapshot} from '../lib/api';
import './pets.css';
type Pet=Snapshot['pets'][number];
function PetForm({householdId,pet,onSaved,onCancel}:{householdId:string;pet?:Pet;onSaved:(world:Snapshot)=>void;onCancel?:()=>void}){
  const [name,setName]=useState(pet?.name||''),[kind,setKind]=useState(pet?.kind==='dog'?'dog':'cat'),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const pending=useRef(false);
  async function submit(e:FormEvent){
    e.preventDefault();if(pending.current)return;setError('');
    if(!name.trim()||name.length>40||/[\u0000-\u001f\u007f-\u009f]/.test(name)){setError('반려동물 이름은 줄바꿈 없이 1~40자로 입력해 주세요.');return;}
    pending.current=true;setBusy(true);
    try{
      const world=await api<Snapshot>(`/api/households/${householdId}/pets${pet?'/'+pet.id:''}`,pet?'PATCH':'POST',{name:name.trim(),kind,...(!pet?{appearance:'cream'}:{})});
      onSaved(world);if(!pet)setName('');
    }catch(e){setError((e as Error).message);}
    finally{pending.current=false;setBusy(false);}
  }
  return <form onSubmit={submit} className="pet-form"><label>{pet?`${pet.name} 이름`:'반려동물 이름'}<input required maxLength={40} value={name} disabled={busy} onChange={e=>setName(e.target.value)}/></label><label>{pet?`${pet.name} 종류`:'반려동물 종류'}<select value={kind} disabled={busy} onChange={e=>setKind(e.target.value)}><option value="cat">고양이</option><option value="dog">강아지</option></select></label>{error&&<p role="alert" className="error-message">{error}</p>}<div className="form-actions">{onCancel&&<button type="button" className="button secondary" disabled={busy} onClick={onCancel}>취소</button>}<button className="button primary" disabled={busy}>{busy?'저장 중…':pet?'반려동물 변경 저장':'반려동물 등록'}</button></div></form>;
}
export function PetManager({snapshot,onSaved}:{snapshot:Snapshot;onSaved:(world:Snapshot)=>void}){
  const [editing,setEditing]=useState<string|null>(null),[message,setMessage]=useState('');
  const owner=snapshot.members.find(m=>m.id===snapshot.meMemberId)?.role==='OWNER';
  return <section className="pet-manager"><p>함께 지내는 반려동물을 한 마리씩 등록해요. 게임 속 움직임은 가상 연출이에요.</p>{!snapshot.pets.length&&<p className="empty-text">아직 등록된 반려동물이 없어요.</p>}{snapshot.pets.map(p=><article className="pet-manager-card" key={p.id}>{editing===p.id?<PetForm householdId={snapshot.household.id} pet={p} onCancel={()=>setEditing(null)} onSaved={w=>{onSaved(w);setEditing(null);setMessage('반려동물 정보를 변경했어요.');}}/>:<><div><strong>{p.name}</strong><p>{p.kind==='dog'?'강아지':p.kind==='cat'?'고양이':'반려동물'}</p></div>{owner&&<button className="button secondary" aria-label={`${p.name} 수정`} onClick={()=>{setEditing(p.id);setMessage('');}}>수정</button>}</>}</article>)}{owner?<><h3>새 반려동물</h3><PetForm householdId={snapshot.household.id} onSaved={w=>{onSaved(w);setMessage('반려동물을 등록했어요.');}}/></>:<p>반려동물 등록과 수정은 집 대표가 할 수 있어요.</p>}{message&&<p role="status">{message}</p>}</section>;
}
