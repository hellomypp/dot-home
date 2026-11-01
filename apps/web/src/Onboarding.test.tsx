import {afterEach,it,expect,vi} from 'vitest';
import {render,screen,cleanup,fireEvent,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,Routes,Route} from 'react-router-dom';
vi.mock('./components/WorldCanvas',()=>({WorldCanvas:()=>null}));
import {Onboarding,LoginPage} from './App';
afterEach(()=>{cleanup();vi.unstubAllGlobals();});
it('집 생성 전에도 로그아웃해 Google 로그인 화면으로 돌아간다',async()=>{
 let authenticated=true;
 const fetcher=vi.fn(async(input:RequestInfo|URL,options?:RequestInit)=>{
  const path=String(input);
  if(path==='/api/auth/csrf')return new Response(JSON.stringify({token:'csrf-test',headerName:'X-CSRF-TOKEN'}),{status:200});
  if(path==='/api/logout'){expect(options?.method).toBe('POST');expect((options?.headers as Record<string,string>)['X-CSRF-TOKEN']).toBe('csrf-test');authenticated=false;return new Response(null,{status:204});}
  if(path==='/api/me')return new Response(JSON.stringify(authenticated?{user:{id:'google-user',name:'새 사용자'},memberships:[]}:{message:'로그인 필요'}),{status:authenticated?200:401});
  if(path==='/api/auth/config')return new Response(JSON.stringify({googleEnabled:true,devEnabled:false}),{status:200});
  throw new Error('Unexpected request');
 });
 vi.stubGlobal('fetch',fetcher);
 const client=new QueryClient({defaultOptions:{queries:{retry:false}}});
 render(<QueryClientProvider client={client}><MemoryRouter initialEntries={['/onboarding']}><Routes><Route path="/onboarding" element={<Onboarding/>}/><Route path="/login" element={<LoginPage/>}/></Routes></MemoryRouter></QueryClientProvider>);
 const button=await screen.findByRole('button',{name:'로그아웃'});expect(screen.getByText('새 사용자 계정으로 로그인됨')).toBeTruthy();fireEvent.click(button);
 expect(await screen.findByRole('link',{name:'Google로 시작하기'})).toBeTruthy();await waitFor(()=>expect(client.getQueryData(['me'])).toBeUndefined());expect(screen.queryByRole('heading',{name:'우리의 집을 만들어볼까요?'})).toBeNull();
});
