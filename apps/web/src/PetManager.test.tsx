import {useState} from 'react';
import {afterEach,it,expect,vi} from 'vitest';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {PetManager} from './components/PetManager';
import {api,type Snapshot} from './lib/api';
vi.mock('./lib/api',()=>({api:vi.fn()}));
const world={household:{id:'h'},meMemberId:'me',members:[{id:'me',role:'OWNER'}],pets:[]} as unknown as Snapshot;
function App({initial=world}:{initial?:Snapshot}){const [value,setValue]=useState(initial);return <PetManager snapshot={value} onSaved={setValue}/>;}
afterEach(()=>cleanup());
it('0마리에서 한 마리씩 등록하고 최신 목록을 표시한다',async()=>{
 const first={id:'p',name:'모카',kind:'cat',appearance:'cream'},second={id:'q',name:'보리',kind:'dog',appearance:'cream'};
 vi.mocked(api).mockResolvedValueOnce({...world,pets:[first]}).mockResolvedValueOnce({...world,pets:[first,second]});render(<App/>);
 expect(screen.getByText('아직 등록된 반려동물이 없어요.')).toBeTruthy();
 fireEvent.change(screen.getByLabelText('반려동물 이름'),{target:{value:'모카'}});fireEvent.click(screen.getByRole('button',{name:'반려동물 등록'}));
 await screen.findByRole('button',{name:'모카 수정'});
 fireEvent.change(screen.getByLabelText('반려동물 이름'),{target:{value:'보리'}});fireEvent.change(screen.getByLabelText('반려동물 종류'),{target:{value:'dog'}});fireEvent.click(screen.getByRole('button',{name:'반려동물 등록'}));
 await screen.findByRole('button',{name:'보리 수정'});expect(api).toHaveBeenLastCalledWith('/api/households/h/pets','POST',{name:'보리',kind:'dog',appearance:'cream'});
});
it('기존 동물의 이름과 종류만 수정해 외형을 보존한다',async()=>{
 const pet={id:'p',name:'모카',kind:'cat',appearance:'legacy-color'};
 vi.mocked(api).mockResolvedValueOnce({...world,pets:[{...pet,name:'새 이름',kind:'dog'}]});render(<App initial={{...world,pets:[pet]}}/>);
 fireEvent.click(screen.getByRole('button',{name:'모카 수정'}));fireEvent.change(screen.getByLabelText('모카 이름'),{target:{value:'새 이름'}});fireEvent.change(screen.getByLabelText('모카 종류'),{target:{value:'dog'}});fireEvent.click(screen.getByRole('button',{name:'반려동물 변경 저장'}));
 await screen.findByRole('button',{name:'새 이름 수정'});expect(api).toHaveBeenCalledWith('/api/households/h/pets/p','PATCH',{name:'새 이름',kind:'dog'});
});
it('일반 구성원은 목록만 볼 수 있다',()=>{
 render(<App initial={{...world,members:[{...world.members[0],role:'MEMBER'}],pets:[{id:'p',name:'모카',kind:'cat',appearance:'cream'}]}}/>);
 expect(screen.getByText('모카')).toBeTruthy();expect(screen.queryByRole('button',{name:'반려동물 등록'})).toBeNull();expect(screen.queryByRole('button',{name:'모카 수정'})).toBeNull();
});
it('중복 제출을 막고 실패하면 입력을 유지한다',async()=>{
 let reject!:(e:Error)=>void;vi.mocked(api).mockImplementationOnce(()=>new Promise((_resolve,r)=>{reject=r;}));const {container}=render(<App/>);
 fireEvent.change(screen.getByLabelText('반려동물 이름'),{target:{value:'모카'}});fireEvent.submit(container.querySelector('form')!);fireEvent.submit(container.querySelector('form')!);expect(api).toHaveBeenCalledTimes(1);
 reject(new Error('연결 오류'));await screen.findByRole('alert');expect((screen.getByLabelText('반려동물 이름') as HTMLInputElement).value).toBe('모카');expect(screen.getByRole('button',{name:'반려동물 등록'}).hasAttribute('disabled')).toBe(false);
});
