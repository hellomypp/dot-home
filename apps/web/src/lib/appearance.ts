import type { WorldMember } from "../game/types";
import { colors } from "./api";
export const wardrobe = {
  hair: ["밤색 단정머리", "검정 단발", "갈색 곱슬", "금빛 긴머리"],
  top: ["세이지 셔츠", "장미빛 셔츠", "푸른 셔츠", "꿀빛 셔츠"],
  bottom: ["차콜 바지", "청바지", "크림 바지", "갈색 바지"],
  accessory: ["장식 없음", "동그란 안경", "꽃 머리핀", "초록 목도리"],
};
export type Outfit = {
  hair: number;
  top: number;
  bottom: number;
  accessory: number;
};
export const defaultOutfit: Outfit = {
  hair: 0,
  top: 0,
  bottom: 0,
  accessory: 0,
};
export function encodeOutfit(value: Outfit) {
  return `v1:${value.hair}${value.top}${value.bottom}${value.accessory}`;
}
export function decodeOutfit(value: string): Outfit {
  const match = /^v1:([0-3])([0-3])([0-3])([0-3])$/.exec(value);
  return match
    ? {
        hair: +match[1],
        top: +match[2],
        bottom: +match[3],
        accessory: +match[4],
      }
    : {
        ...defaultOutfit,
        top: Math.max(0, Object.keys(colors).indexOf(value)),
      };
}
export function gameAppearance(
  value: string,
): NonNullable<WorldMember["appearance"]> {
  const outfit = decodeOutfit(value);
  return {
    shirt: Object.values(colors)[outfit.top],
    hair: ["#655044", "#373b38", "#855b3d", "#c6a265"][outfit.hair],
    hairStyle: outfit.hair,
    pants: ["#626b66", "#526d87", "#d2c3a1", "#87634f"][outfit.bottom],
    accessory: outfit.accessory,
  };
}
