import {afterEach,it,expect,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {NicknameForm} from './components/NicknameForm';
import {api,type Snapshot} from './lib/api';
vi.mock('./lib/api',()=>({api:vi.fn()}));
afterEach(()=>cleanup());
it('본인 닉네임을 저장하고 최신 집 데이터를 반영한다',async()=>{
  const world={meMemberId:'me'} as Snapshot,onSaved=vi.fn();
  vi.mocked(api).mockResolvedValueOnce(world);
  render(<NicknameForm householdId="house" nickname="가온" onSaved={onSaved}/>);
  fireEvent.change(screen.getByLabelText('닉네임'),{target:{value:'  새이름  '}});
  fireEvent.click(screen.getByRole('button',{name:'닉네임 저장'}));
  expect(await screen.findByRole('status')).toHaveProperty('textContent','닉네임을 변경했어요.');
  expect(api).toHaveBeenCalledWith('/api/households/house/me/profile','PATCH',{memberName:'새이름'});
  expect(onSaved).toHaveBeenCalledWith(world);
});
it('저장 중 재제출을 막는다',async()=>{
  let done!:(world:Snapshot)=>void;
  vi.mocked(api).mockImplementationOnce(()=>new Promise(resolve=>{done=resolve as typeof done;}));
  const {container}=render(<NicknameForm householdId="house" nickname="가온" onSaved={()=>{}}/>);
  fireEvent.change(screen.getByLabelText('닉네임'),{target:{value:'새이름'}});
  fireEvent.submit(container.querySelector('form')!);fireEvent.submit(container.querySelector('form')!);
  expect(api).toHaveBeenCalledTimes(1);
  expect(screen.getByRole('button',{name:'저장 중…'}).hasAttribute('disabled')).toBe(true);
  done({} as Snapshot);await screen.findByRole('status');
});
it('오류 시 입력값을 유지하며 재시도할 수 있다',async()=>{
  vi.mocked(api).mockRejectedValueOnce(new Error('연결을 확인해 주세요.')).mockResolvedValueOnce({});
  render(<NicknameForm householdId="house" nickname="가온" onSaved={()=>{}}/>);
  fireEvent.change(screen.getByLabelText('닉네임'),{target:{value:'다정'}});
  fireEvent.click(screen.getByRole('button',{name:'닉네임 저장'}));
  expect(await screen.findByRole('alert')).toHaveProperty('textContent','연결을 확인해 주세요.');
  expect((screen.getByLabelText('닉네임') as HTMLInputElement).value).toBe('다정');
  fireEvent.click(screen.getByRole('button',{name:'닉네임 저장'}));
  await screen.findByRole('status');expect(screen.queryByRole('alert')).toBeNull();
});
it('공백만 있는 이름과 제어 문자를 서버에 보내지 않는다',async()=>{
  const {container}=render(<NicknameForm householdId="house" nickname="가온" onSaved={()=>{}}/>);
  fireEvent.change(screen.getByLabelText('닉네임'),{target:{value:'   '}});
  fireEvent.submit(container.querySelector('form')!);
  await screen.findByRole('alert');expect(api).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('닉네임'),{target:{value:'가\u0001온'}});
  fireEvent.submit(container.querySelector('form')!);
  await waitFor(()=>expect(screen.getByRole('alert')).toBeTruthy());expect(api).not.toHaveBeenCalled();
});
