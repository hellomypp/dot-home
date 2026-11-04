export const petSpecies = {
  CAT: { name: "고양이", habitat: "바닥", icon: "🐈" },
  DOG: { name: "강아지", habitat: "바닥", icon: "🐕" },
  BIRD: { name: "조류", habitat: "횃대", icon: "🐦" },
  SMALL_MAMMAL: { name: "소형 포유류", habitat: "소형 서식처", icon: "🐾" },
  REPTILE: { name: "파충류", habitat: "테라리움", icon: "🦎" },
  AMPHIBIAN: { name: "양서류", habitat: "테라리움", icon: "🐸" },
  FISH: { name: "어류", habitat: "수조", icon: "🐟" },
  INVERTEBRATE: { name: "무척추동물", habitat: "테라리움", icon: "🐾" },
  OTHER: { name: "기타", habitat: "소형 서식처", icon: "🐾" },
};
export type PetSpecies = keyof typeof petSpecies;
export type PetHabitat = "FLOOR" | "PERCH" | "TANK" | "TERRARIUM" | "SMALL_HABITAT";
export interface Pet {
  id: string;
  name: string;
  kind: string;
  appearance: string;
  speciesGroup?: PetSpecies;
  speciesName?: string;
  habitat?: PetHabitat;
  guardianMemberId?: string | null;
  appearanceId?: string;
  version?: number;
}

export function petGroup(pet?: Pet): PetSpecies {
  return pet?.speciesGroup ?? (pet?.kind === "dog" ? "DOG" : pet?.kind === "other" ? "OTHER" : "CAT");
}
