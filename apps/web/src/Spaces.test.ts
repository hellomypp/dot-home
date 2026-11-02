import { describe, it, expect } from "vitest";
import {
  catalogue,
  mapFor,
  canStand,
  path,
  occludes,
  fade,
  nativeCharacter,
  spriteFor,
  defaultDesign,
} from "./game/spaces";
describe("v6.2 canonical spaces", () => {
  for (const { id } of catalogue.presets) {
    it(`${id}: spawns, every interaction and bookshelf passage are reachable`, () => {
      const m = mapFor(id),
        spawn = { x: m.spawns[0][0], y: m.spawns[0][1] };
      expect(m.collision).toHaveLength(560);
      expect(canStand(spawn, m)).toBe(true);
      for (const o of m.objects.filter((o) => o.interactive)) {
        const goal = { x: o.interactionAnchor![0], y: o.interactionAnchor![1] };
        expect(canStand(goal, m), o.id).toBe(true);
        const route = path(spawn, goal, m);
        expect(route.length, o.id).toBeGreaterThan(0);
        expect(route.every((p) => canStand(p, m))).toBe(true);
      }
      const passage: Record<string, number[]> = {
        garden_home: [56, 152, 128, 152],
        sunny_apartment: [344, 224, 416, 224],
        brick_loft: [352, 136, 424, 136],
      };
      const [x, y, xx, yy] = passage[id];
      expect(path({ x, y }, { x: xx, y: yy }, m).length).toBeGreaterThan(0);
      expect(canStand({ x: -1, y: 100 }, m)).toBe(false);
      expect(canStand({ x: NaN, y: 100 }, m)).toBe(false);
    });
  }
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
  it("retains classic alias, maps legacy values and uses provided variant assets", () => {
    expect(mapFor("classic_home")).toBe(mapFor("garden_home"));
    expect(nativeCharacter("v1:3212")).toBe("adult_male");
    expect(nativeCharacter("sage")).toBe("adult_female");
    expect(nativeCharacter("v2:child")).toBe("child");
    expect(
      spriteFor(mapFor().objects.find((o) => o.id === "sofa")!, {
        ...defaultDesign,
        variants: { sofa: "blue" },
      }),
    ).toBe("assets/objects/blue_sofa.png");
  });
});
