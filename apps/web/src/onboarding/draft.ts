import { decodeOutfit, encodeOutfit } from "../lib/appearance";
export interface Draft {
  step: 0 | 1 | 2;
  name: string;
  memberName: string;
  appearance: string;
}
export const draftPrefix = "dot-home:onboarding:";
export function emptyDraft(name: string): Draft {
  return {
    step: 0,
    name: "우리의 작은 집",
    memberName: name,
    appearance: "v1:0000",
  };
}
export function readDraft(id: string, name: string): Draft {
  const fresh = emptyDraft(name);
  try {
    const saved = JSON.parse(
      sessionStorage.getItem(draftPrefix + id) || "null",
    );
    if (
      !saved ||
      !Number.isFinite(saved.updatedAt) ||
      saved.updatedAt > Date.now() ||
      Date.now() - saved.updatedAt > 24 * 60 * 60 * 1000
    )
      return fresh;
    const d = saved.draft;
    if (!d || typeof d !== "object") return fresh;
    if (
      ![0, 1, 2].includes(d.step) ||
      typeof d.name !== "string" ||
      typeof d.memberName !== "string"
    )
      return fresh;
    return {
      ...fresh,
      step: d.step,
      name: d.name.slice(0, 60),
      memberName: d.memberName.slice(0, 30),
      appearance: encodeOutfit(decodeOutfit(String(d.appearance))),
    };
  } catch {
    return fresh;
  }
}
export function saveDraft(id: string, draft: Draft) {
  try {
    sessionStorage.setItem(
      draftPrefix + id,
      JSON.stringify({ updatedAt: Date.now(), draft }),
    );
  } catch {
    /* Private browsing/storage quota must not block onboarding. */
  }
}
export function clearDrafts() {
  try {
    for (const key of Object.keys(sessionStorage))
      if (key.startsWith(draftPrefix)) sessionStorage.removeItem(key);
  } catch {
    /* Storage can be unavailable. */
  }
}
