import { useEffect, useRef } from 'react';
import { mountWorld } from '../game';
import type { WorldView, WorldEvent } from '../game/types';

export function WorldCanvas({ view, onEvent }: {view:WorldView;onEvent:(event:WorldEvent)=>void}) {
  const parent = useRef<HTMLDivElement>(null);
  const instance = useRef<ReturnType<typeof mountWorld> | null>(null);
  const event = useRef(onEvent);
  event.current = onEvent;
  useEffect(() => {instance.current = mountWorld(parent.current!, view, e => event.current(e)); return () => {instance.current?.destroy(); instance.current=null;};}, []);
  useEffect(() => {instance.current?.update(view);}, [view]);
  return <div ref={parent} className="world-canvas" role="region" aria-label={view.place === 'office' ? '달빛 광산에서 안전모를 쓴 캐릭터가 곡괭이로 광석을 캐는 장면' : view.place === 'school' ? '햇살 교실에서 캐릭터가 친구들과 수업을 듣는 장면' : '도트홈 가상 공간. 바닥을 클릭하거나 방향키로 내 캐릭터를 움직일 수 있습니다.'} tabIndex={view.place === 'office' || view.place === 'school' ? undefined : 0}/>;
}
