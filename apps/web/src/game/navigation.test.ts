import {describe,it,expect} from 'vitest';
import {findPath,walkable,nearestWalkable,HOME_SOLIDS} from './navigation';
describe('집 이동과 충돌',()=>{
  it('가구 내부와 장면 밖은 막는다',()=>{expect(walkable({x:400,y:245})).toBe(false);expect(walkable({x:5,y:300})).toBe(false);expect(walkable({x:320,y:288})).toBe(true);});
  it('소파를 가로지르지 않고 돌아간다',()=>{const path=findPath({x:120,y:190},{x:120,y:285});expect(path.length).toBeGreaterThan(0);for(const point of path)expect(walkable(point)).toBe(true);for(let i=1;i<path.length;i++){expect(Math.abs(path[i].x-path[i-1].x)+Math.abs(path[i].y-path[i-1].y)).toBe(8);}expect(path.some(p=>p.x>207||p.x<63)).toBe(true);});
  it('가구 클릭은 접근 가능한 지점으로 투영한다',()=>{for(const r of HOME_SOLIDS)expect(walkable(nearestWalkable({x:r.x+r.width/2,y:r.y+r.height/2}))).toBe(true);});
  it('실제 가구의 충돌선은 보이는 높이와 독립적이다',()=>{expect(walkable({x:100,y:200})).toBe(true);expect(walkable({x:100,y:240})).toBe(false);});
});
