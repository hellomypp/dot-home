import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useQueryClient} from '@tanstack/react-query';
import {LogOut} from 'lucide-react';
import {logout} from '../lib/api';

/** Available before household membership exists, including onboarding and invitations. */
export function SessionBar({name}:{name:string}) {
 const navigate=useNavigate(),client=useQueryClient();
 const [busy,setBusy]=useState(false),[error,setError]=useState('');
 async function leave(){
  setBusy(true);setError('');
  try{await logout();await client.cancelQueries();client.clear();navigate('/login',{replace:true});}
  catch(e){setError((e as Error).message);setBusy(false);}
 }
 return <div className="session-bar"><span>{name} 계정으로 로그인됨</span><button type="button" className="button secondary" disabled={busy} onClick={()=>void leave()}><LogOut size={16}/>{busy?'로그아웃 중…':'로그아웃'}</button>{error&&<p className="error-message" role="alert">{error}</p>}</div>;
}
