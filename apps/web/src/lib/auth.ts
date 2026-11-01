/** Only a family invitation or housewarming path can survive login; no origins, queries or fragments. */
export function safeInvitePath(value:string|null):string|null {
  return value && value.length===(value.startsWith('/invite/')?51:50) && /^\/(?:invite|visit)\/[A-Za-z0-9_-]{43}$/.test(value)?value:null;
}
export function googleLoginUrl(next:string|null) {
  const destination=safeInvitePath(next);
  return '/oauth2/authorization/google'+(destination?'?next='+encodeURIComponent(destination):'');
}
export function localDevLoginVisible(hostname:string,developmentBuild:boolean,serverAllowed:boolean) {
  return developmentBuild && serverAllowed && ['localhost','127.0.0.1','[::1]','::1'].includes(hostname);
}
export interface AuthConfig { googleEnabled:boolean;devEnabled:boolean }
