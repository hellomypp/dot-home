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
 fireEvent.change(screen.getByLabelText('반려동물 이름'),{target:{value:'보리'}});fireEvent.change(screen.getByLabelText('반려동물 종류'),{target:{value:'DOG'}});fireEvent.click(screen.getByRole('button',{name:'반려동물 등록'}));
 await screen.findByRole('button',{name:'보리 수정'});expect(api).toHaveBeenLastCalledWith('/api/households/h/pets','POST',{name:'보리',kind:'dog',speciesGroup:'DOG',speciesName:'',appearance:'cream'});
});
it('기존 동물의 이름과 종류만 수정해 외형을 보존한다',async()=>{
 const pet={id:'p',name:'모카',kind:'cat',appearance:'legacy-color'};
 vi.mocked(api).mockResolvedValueOnce({...world,pets:[{...pet,name:'새 이름',kind:'dog'}]});render(<App initial={{...world,pets:[pet]}}/>);
 fireEvent.click(screen.getByRole('button',{name:'모카 수정'}));fireEvent.change(screen.getByLabelText('모카 이름'),{target:{value:'새 이름'}});fireEvent.change(screen.getByLabelText('모카 종류'),{target:{value:'DOG'}});fireEvent.click(screen.getByRole('button',{name:'반려동물 변경 저장'}));
 await screen.findByRole('button',{name:'새 이름 수정'});expect(api).toHaveBeenCalledWith('/api/households/h/pets/p','PATCH',{name:'새 이름',kind:'dog',speciesGroup:'DOG',speciesName:'',version:0});
});
it('일반 구성원은 등록할 수 있지만 다른 보호자의 동물을 수정할 수 없다',()=>{
 render(<App initial={{...world,members:[{...world.members[0],role:'MEMBER'}],pets:[{id:'p',name:'모카',kind:'cat',appearance:'cream'}]}}/>);
 expect(screen.getByText('모카')).toBeTruthy();expect(screen.getByRole('button',{name:'반려동물 등록'})).toBeTruthy();expect(screen.queryByRole('button',{name:'모카 수정'})).toBeNull();
});
it('중복 제출을 막고 실패하면 입력을 유지한다',async()=>{
 let reject!:(e:Error)=>void;vi.mocked(api).mockImplementationOnce(()=>new Promise((_resolve,r)=>{reject=r;}));const {container}=render(<App/>);
 fireEvent.change(screen.getByLabelText('반려동물 이름'),{target:{value:'모카'}});fireEvent.submit(container.querySelector('form')!);fireEvent.submit(container.querySelector('form')!);expect(api).toHaveBeenCalledTimes(1);
 reject(new Error('연결 오류'));await screen.findByRole('alert');expect((screen.getByLabelText('반려동물 이름') as HTMLInputElement).value).toBe('모카');expect(screen.getByRole('button',{name:'반려동물 등록'}).hasAttribute('disabled')).toBe(false);
});

it("가족이 어류 종명과 보호자를 입력하고 수조 범용 표현을 확인한다", async () => {
  const members = [{ id: "me", role: "MEMBER", name: "보호자" }] as Snapshot["members"];
  const initial = { ...world, members };
  vi.mocked(api).mockResolvedValueOnce({
    ...initial, pets: [{ id: "fish", name: "별이", kind: "other", appearance: "cream",
      speciesGroup: "FISH", speciesName: "베타", habitat: "TANK", guardianMemberId: "me", version: 0 }],
  });
  render(<App initial={initial} />);
  fireEvent.change(screen.getByLabelText("반려동물 이름"), { target: { value: "별이" } });
  fireEvent.change(screen.getByLabelText("반려동물 종류"), { target: { value: "FISH" } });
  fireEvent.change(screen.getByLabelText("반려동물 종 이름 · 선택"), { target: { value: "베타" } });
  expect(screen.getByText(/수조 범용 그림/)).toBeTruthy();
  expect(screen.getByText("놓을 수 없음")).toBeTruthy();
  const submit = screen.getByRole("button", { name: "반려동물 등록" }) as HTMLButtonElement;
  expect(submit.disabled).toBe(true);
  expect(screen.queryByRole("button", { name: "수조 위치 1행 1열" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "수조 위치 3행 2열" }));
  expect(submit.disabled).toBe(false);
  fireEvent.click(submit);
  await screen.findByRole("button", { name: "별이 수정" });
  expect(screen.getByText("보호자 님이 보호자로 관리 중")).toBeTruthy();
  expect(api).toHaveBeenLastCalledWith("/api/households/h/pets", "POST", {
    name: "별이", speciesGroup: "FISH", speciesName: "베타", appearance: "cream", tankSlot: 17,
  });
});

it("프로필 보관은 확인 후에만 요청하고 장면용 목록에서 뺀다", async () => {
  const pet = { id: "p", name: "모카", kind: "cat", appearance: "cream", version: 4 };
  vi.mocked(api).mockResolvedValueOnce(world);
  render(<App initial={{ ...world, pets: [pet] }} />);
  fireEvent.click(screen.getByRole("button", { name: "모카 수정" }));
  fireEvent.click(screen.getByRole("button", { name: "프로필 보관" }));
  expect(api).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "보관 확인" }));
  await screen.findByText("반려동물 프로필을 보관했어요.");
  expect(api).toHaveBeenCalledWith("/api/households/h/pets/p?version=4", "DELETE");
  expect(screen.queryByRole("button", { name: "모카 수정" })).toBeNull();
});
