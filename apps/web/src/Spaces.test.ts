import { describe, it, expect } from "vitest";
import {
  catalogue,
  mapFor,
  canStand,
  path,
  occludes,
  fade,
  nativeCharacter,
} from "./game/spaces";
describe("shared home layouts", () => {
  for (const { id } of catalogue.presets) {
    it(`${id}: spawns, every interaction and bookshelf passage are reachable`, () => {
      const m = mapFor(id),
        spawn = { x: m.spawns[0][0], y: m.spawns[0][1] };
      for (const [x, y] of m.spawns) expect(canStand({ x, y }, m)).toBe(true);
      const [petX, petY] = m.petSpawn;
      expect(canStand({ x: petX, y: petY }, m, true)).toBe(true);
      for (const o of m.objects.filter((o) => o.interactive)) {
        const goal = { x: o.interactionAnchor![0], y: o.interactionAnchor![1] };
        expect(canStand(goal, m), o.id).toBe(true);
        const route = path(spawn, goal, m);
        expect(route.length, o.id).toBeGreaterThan(0);
        expect(route.every((p) => canStand(p, m))).toBe(true);
      }
      const passage: Record<string, number[]> = {
        "warm-wood-home": [60, 204, 72, 204],
        sunny_apartment: [328, 188, 380, 188],
        brick_loft: [60, 204, 72, 204],
      };
      const [x, y, xx, yy] = passage[id];
      expect(canStand({ x, y }, m)).toBe(true);
      expect(canStand({ x: xx, y: yy }, m)).toBe(true);
      expect(path({ x, y }, { x: xx, y: yy }, m).length).toBeGreaterThan(0);
      expect(canStand({ x: -1, y: 100 }, m)).toBe(false);
      expect(canStand({ x: NaN, y: 100 }, m)).toBe(false);
    });
  }
  it("fits each fridge footprint and keeps the approach clear", () => {
    for (const { id } of catalogue.presets) {
      const m = mapFor(id);
      expect(canStand({ x: 338, y: 133 }, m)).toBe(false);
      expect(canStand({ x: 338, y: 148 }, m)).toBe(true);
    }
  });
  it("occludes only the rear overlap and restores opacity in 160ms", () => {
    const o = mapFor().objects.find((o) => !!o.occlusion)!;
    const rear = {
      x: o.x + o.width / 2,
      y: o.y + o.height - o.occlusion!.rearInset,
    };
    expect(occludes(o, rear)).toBe(true);
    expect(occludes(o, { ...rear, y: o.y + o.height + 10 })).toBe(false);
    expect(occludes(o, { ...rear, x: o.x + o.width + 30 })).toBe(false);
    expect(fade(1, 0.55, 0.16)).toBeCloseTo(0.55);
    expect(fade(0.55, 1, 0.16)).toBeCloseTo(1);
  });
  it("maps legacy home and character IDs", () => {
    expect(mapFor("classic_home")).toBe(mapFor("warm-wood-home"));
    expect(mapFor("garden_home")).toBe(mapFor("warm-wood-home"));
    expect(nativeCharacter("v1:3212")).toBe("adult_male");
    expect(nativeCharacter("sage")).toBe("adult_female");
    expect(nativeCharacter("v2:child")).toBe("child");
  });
});
