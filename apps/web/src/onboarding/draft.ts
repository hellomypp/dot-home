import { decodeOutfit, encodeOutfit } from "../lib/appearance";
export interface Draft {
  step: 0 | 1 | 2;
  name: string;
  people: number;
  petCount: number;
  memberName: string;
  appearance: string;
  pets: { name: string; kind: "cat" | "dog" }[];
}
export const draftPrefix = "dot-home:onboarding:";
export function emptyDraft(name: string): Draft {
  return {
    step: 0,
    name: "우리의 작은 집",
    people: 2,
    petCount: 0,
    memberName: name,
    appearance: "v1:0000",
    pets: [],
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
    if (
      ![0, 1, 2].includes(d.step) ||
      typeof d.name !== "string" ||
      typeof d.memberName !== "string" ||
      !Number.isSafeInteger(d.people) ||
      d.people < 1 ||
      !Number.isSafeInteger(d.petCount) ||
      d.petCount < 0 ||
      !Array.isArray(d.pets)
    )
      return fresh;
    return {
      ...fresh,
      ...d,
      name: d.name.slice(0, 60),
      memberName: d.memberName.slice(0, 30),
      appearance: encodeOutfit(decodeOutfit(String(d.appearance))),
      pets: d.pets
        .filter(
          (p: Draft["pets"][number]) =>
            typeof p?.name === "string" && ["cat", "dog"].includes(p.kind),
        )
        .map((p: Draft["pets"][number]) => ({
          ...p,
          name: p.name.slice(0, 30),
        })),
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
