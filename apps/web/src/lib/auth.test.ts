import {describe,it,expect} from 'vitest';
import {safeInvitePath,googleLoginUrl,localDevLoginVisible} from './auth';
describe('Google 로그인 목적지 및 로컬 인증',()=>{
 it('초대 경로만 보존하고 외부/조작 경로를 차단한다',()=>{const invite='/invite/'+'a'.repeat(43);expect(safeInvitePath(invite)).toBe(invite);for(const value of ['https://evil.example','//evil.example','/invite/../evil',invite+'?next=evil',invite+'#evil',invite+'\n','/invite/%2f%2fevil']){expect(safeInvitePath(value)).toBeNull();expect(googleLoginUrl(value)).toBe('/oauth2/authorization/google');}expect(googleLoginUrl(invite)).toBe('/oauth2/authorization/google?next='+encodeURIComponent(invite));});
 it('Tailscale/공개 호스트와 운영 빌드에서는 개발 로그인 숨김',()=>{expect(localDevLoginVisible('127.0.0.1',true,true)).toBe(true);expect(localDevLoginVisible('localhost',true,false)).toBe(false);expect(localDevLoginVisible('localhost',false,true)).toBe(false);expect(localDevLoginVisible('hs-macbook-pro.tailcd16d0.ts.net',true,true)).toBe(false);});
});
