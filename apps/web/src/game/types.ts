import type { PetHabitat } from "../lib/pets";
import type {SpaceDesign} from './spaces';
import type { WindowSky } from '../lib/windowWeather';
export type Place = 'home' | 'office' | 'school' | 'outside';
export interface WorldMember {
  id: string;
  name: string;
  characterStyle?: string;
  appearance?: { hair?: string; shirt?: string; skin?: string; pants?: string; hairStyle?: number; accessory?: number };
  place: Place;
  status?: string;
  online?: boolean;
  x?: number;
  y?: number;
  action?: string;
}
export interface WorldPet { id: string; name: string; kind?: string; color?: string; habitat?: PetHabitat; tankSlot?: number | null }
export interface WorldView {
  readOnly?: boolean;
  closeView?:boolean;
  windowSky?: WindowSky;
  design?:SpaceDesign;
  objectNotes?: {id:string;objectId:string}[];
  place: Place;
  ownMemberId: string;
  observedMemberId?: string;
  members: WorldMember[];
  pets: WorldPet[];
  reducedMotion?: boolean;
  palette?: 'warm' | 'dusk';
}
export type WorldEvent =
  | { type: 'interact'; target: 'fridge' | 'sofa' | 'table' | 'pet' | 'window' | 'calendar' | 'entrance' | 'pet_rest' | 'bookshelf'; label: string }
  | { type: 'activity'; text: string }
  | { type: 'reveal-name'; name: string }
  | { type: 'reveal-speech'; text: string }
  | { type: 'ready' }
  | { type: 'visibility-resume' }
  | { type: 'move-intent'; x: number; y: number };
export interface WorldHandle { update(view: WorldView): void; destroy(): void }
