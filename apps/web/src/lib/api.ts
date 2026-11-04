import type { Pet } from "./pets";
import type {SpaceDesign} from '../game/spaces';
import { expressionLabel, type ExpressionField, type ExpressionValue, type SharedPlace } from './expressions';
import type { Place } from '../game/types';

export interface Member { id:string;userId:string;name:string;appearance:string;role:'OWNER'|'MEMBER';place:Place;mood:string;food:string;thought:string;statusExpiresAt:string|null;statusUpdatedAt:string;x:number;y:number;action:string;currentState?:Partial<Record<ExpressionField,ExpressionValue>>;sharedPlace?:SharedPlace;membershipVersion?:number }
export interface PantryItem { id:string;name:string;location:string;quantity:number;unit:string;expiresOn:string|null;version:number;updatedAt:string }
export interface LifeEvent { id:string;type:string;message:string;authorMemberId:string;authorName:string;createdAt:string }
export interface Dependent { id:string;type:"BABY";name:string;guardianMemberId:string;appearanceProfile:string;version:number }
export interface Snapshot { design?:SpaceDesign;household:{id:string;name:string;ownerId:string;pendingDeleteUntil?:string|null;deletionVersion?:number;deletionAvailable?:boolean};meMemberId:string;revision:number;serverTime:string;members:Member[];pets:Pet[];dependents?:Dependent[];pantryItems:PantryItem[];events:LifeEvent[] }
export interface Identity { user:{id:string;name:string};memberships:{householdId:string;householdName:string;memberId:string;role:string}[] }
export class ApiError extends Error { constructor(public status:number,message:string) {super(message);} }
let csrf: {token:string;headerName:string}|null = null;
export async function getCsrf(refresh=false) { if (!csrf || refresh) { const response=await fetch('/api/auth/csrf',{credentials:'same-origin'}); if (!response.ok) throw new ApiError(response.status,'서버에 연결할 수 없어요.'); csrf=await response.json(); } return csrf!; }
export async function api<T>(path:string,method='GET',body?:unknown):Promise<T> {
  const headers:Record<string,string>={'Accept':'application/json'};
  if (method!=='GET') { const token=await getCsrf();headers[token.headerName]=token.token;headers['Content-Type']='application/json'; }
  let response:Response;
  try { response=await fetch(path,{method,headers,credentials:'same-origin',body:body===undefined?undefined:JSON.stringify(body)}); }
  catch {throw new ApiError(0,'서버 연결이 끊겼어요. 연결 후 다시 시도해 주세요.');}
  if (!response.ok) {const error=await response.json().catch(()=>({})); if(response.status===401)csrf=null;throw new ApiError(response.status,error.message||'요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.');}
  if(response.status===204) return undefined as T;
  return response.json();
}
export async function login(accountKey:string,name:string) { const result=await api<Identity>('/api/auth/dev','POST',{accountKey,name});await getCsrf(true);return result; }
export async function logout() { await api('/api/logout','POST');csrf=null; }
export const colors:Record<string,string>={sage:'#7c9170',rose:'#bb7975',blue:'#6e88a1',amber:'#bd9556'};
export const places:Record<Place,string>={home:'우리 집',office:'직장',school:'학교',outside:'밖'};

export function activeExpression(member: Member) {
  const now = Date.now();
  for (const field of ["thought", "food", "mood", "physical", "availability"] as const) {
    const item = member.currentState?.[field];
    if (item?.value && item.expiresAt && Date.parse(item.expiresAt) > now)
      return { field, value: item.value, version: item.version };
  }
  return undefined;
}
export function statusText(member: Member) {
  if (member.currentState) {
    const item = activeExpression(member);
    if (!item) return "";
    return item.field === "food" ? `${item.value} 먹고 싶어` : expressionLabel(item.field, item.value);
  }
  if (member.statusExpiresAt && Date.parse(member.statusExpiresAt) <= Date.now()) return "";
  return member.thought || (member.food ? `${member.food} 먹고 싶어` : member.mood);
}
