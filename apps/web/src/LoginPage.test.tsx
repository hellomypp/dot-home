import {afterEach,describe,it,expect,vi} from 'vitest';
import {render,screen,cleanup} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,Routes,Route} from 'react-router-dom';
vi.mock('./components/WorldCanvas',()=>({WorldCanvas:()=>null}));
import {LoginPage} from './App';
import {rememberHome} from './lib/lastHome';
function setup(route='/login',loggedIn=false,googleEnabled=true){
 vi.stubGlobal('fetch',vi.fn(async(input:RequestInfo|URL)=>{
  const path=String(input);
  if(path==='/api/auth/config')return new Response(JSON.stringify({googleEnabled,devEnabled:false}),{status:200});
  if(path==='/api/me')return new Response(JSON.stringify(loggedIn?{user:{id:'user',name:'가온'},memberships:[{householdId:'house',memberId:'member',role:'OWNER'}]}:{message:'로그인 필요'}),{status:loggedIn?200:401});
  throw new Error('Unexpected request');
 }));
 const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
 render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[route]}><Routes><Route path="/login" element={<LoginPage/>}/><Route path="/home/:id" element={<h1>기존 우리 집</h1>}/><Route path="/invite/:token" element={<h1>초대 복귀</h1>}/></Routes></MemoryRouter></QueryClientProvider>);
}
afterEach(()=>{cleanup();localStorage.clear();vi.unstubAllGlobals();});
describe('공식 로그인 화면',()=>{
 it('Google 하나를 표시하고 초대를 OAuth 시작 주소에 안전하게 전달한다',async()=>{const next='/invite/'+'a'.repeat(43);setup('/login?next='+encodeURIComponent(next));const link=await screen.findByRole('link',{name:'Google로 시작하기'});expect(link.getAttribute('href')).toBe('/oauth2/authorization/google?next='+encodeURIComponent(next));expect(screen.queryByLabelText('계정 키')).toBeNull();expect(screen.queryByText(/카카오/)).toBeNull();});
 it('실패 이유의 원문 대신 재시도 안내를 표시한다',async()=>{setup('/login?error=google');expect(await screen.findByRole('alert')).toHaveProperty('textContent',expect.stringContaining('Google 로그인을 완료하지 못했어요'));});
 it('설정 전에는 잘못된 OAuth 주소로 이동하지 않는다',async()=>{setup('/login',false,false);const button=await screen.findByRole('button',{name:'Google로 시작하기'});expect(button.hasAttribute('disabled')).toBe(true);expect(screen.queryByRole('link',{name:'Google로 시작하기'})).toBeNull();});
 it('기존 로그인 사용자는 기억된 집으로 바로 간다',async()=>{rememberHome('user','house');setup('/login',true);expect(await screen.findByRole('heading',{name:'기존 우리 집'})).toBeTruthy();});
 it('로그인된 사용자라도 보존된 초대 경로가 있으면 그곳으로 복귀한다',async()=>{setup('/login?next='+encodeURIComponent('/invite/'+'a'.repeat(43)),true);expect(await screen.findByRole('heading',{name:'초대 복귀'})).toBeTruthy();});
});
