import {describe,it,expect} from 'vitest';
import {RevisionTracker} from './realtime';
import {statusText,type Member} from './api';
describe('스냅샷 복구와 상태 만료',()=>{
  it('스냅샷 이전과 중복 사건을 무시한다',()=>{const tracker=new RevisionTracker();tracker.snapshot(5);expect(tracker.accept({eventId:'old',revision:4,type:'STATUS_CHANGED',payload:{}})).toBe(false);const fresh={eventId:'new',revision:7,type:'STATUS_CHANGED',payload:{}};expect(tracker.accept(fresh)).toBe(true);expect(tracker.accept(fresh)).toBe(false);tracker.snapshot(8);tracker.snapshot(6);expect(tracker.revision).toBe(8);});
  it('만료한 이야기를 현재 기분으로 보여주지 않는다',()=>{const member={thought:'반가워',mood:'좋아',food:'수프',statusExpiresAt:new Date(Date.now()-1).toISOString()} as Member;expect(statusText(member)).toBe('');member.statusExpiresAt=new Date(Date.now()+60000).toISOString();expect(statusText(member)).toBe('반가워');member.thought='';expect(statusText(member)).toBe('수프 먹고 싶어');});
});
