import type { Identity } from "./api";

const key = (userId: string) => "dot-home:last-home:" + userId;

export function rememberHome(userId: string, householdId: string) {
  try {
    localStorage.setItem(key(userId), householdId);
  } catch {
    // The home list remains available when storage is blocked.
  }
}

export function homeDestination(identity: Identity) {
  if (!identity.memberships.length) return "/onboarding";
  try {
    const last = localStorage.getItem(key(identity.user.id));
    const membership = identity.memberships.find(({ householdId }) => householdId === last);
    if (membership) return "/home/" + encodeURIComponent(membership.householdId);
  } catch {
    // The saved value is a preference. Memberships remain authoritative.
  }
  return "/homes";
}
