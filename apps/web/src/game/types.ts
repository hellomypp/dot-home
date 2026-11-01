export type Place = 'home' | 'office' | 'school' | 'outside';
export interface WorldMember {
  id: string;
  name: string;
  appearance?: { hair?: string; shirt?: string; skin?: string };
  place: Place;
  status?: string;
  online?: boolean;
  x?: number;
  y?: number;
  action?: string;
}
export interface WorldPet { id: string; name: string; kind?: string; color?: string }
export interface WorldView {
  place: Place;
  ownMemberId: string;
  observedMemberId?: string;
  members: WorldMember[];
  pets: WorldPet[];
  reducedMotion?: boolean;
  palette?: 'warm' | 'dusk';
}
export type WorldEvent =
  | { type: 'interact'; target: 'fridge' | 'sofa' | 'table' | 'pet' | 'window'; label: string }
  | { type: 'activity'; text: string }
  | { type: 'ready' }
  | { type: 'visibility-resume' }
  | { type: 'move-intent'; x: number; y: number };
export interface WorldHandle { update(view: WorldView): void; destroy(): void }
