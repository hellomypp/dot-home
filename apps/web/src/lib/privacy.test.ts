import {it,expect} from 'vitest';
import {redactTokenPaths} from './privacy';
it('가족·집들이 경로와 OAuth next의 토큰을 개발 로그에서 제거한다',()=>{
 const token='a'.repeat(43);
 for(const path of ['/visit/','/invite/','/api/visits/','/api/invitations/','%2Fvisit%2F','%2Finvite%2F']){
  const result=redactTokenPaths('proxy failed '+path+token+'?next=home');expect(result).not.toContain(token);expect(result).toContain('[redacted]');
 }
 expect(redactTokenPaths('/api/households/abc/world')).toBe('/api/households/abc/world');
});
