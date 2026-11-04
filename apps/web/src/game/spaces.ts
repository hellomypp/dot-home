import raw from "../../../../assets/space-catalog.json";
export interface SpaceDesign {
  presetId: string;
  version: number;
  variants: Record<string, string>;
}
export interface MapObject {
  id: string;
  sprite: string;
  x: number;
  y: number;
  width: number;
  height: number;
  depth: number;
  layer: string;
  interactive: boolean;
  collision: number[] | null;
  interactionAnchor?: number[];
  occlusion?: {
    alpha: number;
    fadeMs: number;
    restoreMs: number;
    rearInset: number;
  };
}
export interface RuntimeMap {
  id: string;
  objects: MapObject[];
  spawns: number[][];
  visitorSpawn: number[];
  backdrop: string;
  previewCrop: [number, number, number, number];
  walkBounds: [number, number, number, number];
  petSpawn: number[];
}
export const catalogue = raw as unknown as {
  maps: Record<string, RuntimeMap>;
  presets: { id: string; name: string }[];
  variants: Record<string, string[]>;
  aliases: Record<string, string>;
};
export const warmWoodPreset = "warm-wood-home";
export const defaultDesign: SpaceDesign = {
  presetId: warmWoodPreset,
  version: 0,
  variants: {},
};
export const assetBase = "/art/v62/";
export function mapFor(id = warmWoodPreset) {
  const key = Object.hasOwn(catalogue.aliases, id) ? catalogue.aliases[id] : id;
  return Object.hasOwn(catalogue.maps, key)
    ? catalogue.maps[key]
    : catalogue.maps[warmWoodPreset];
}
export function fridgeMagnet(design: SpaceDesign) {
  return design.variants.fridge === "leaf" ? 0x47832b
    : design.variants.fridge === "star" ? 0xffcf76
    : undefined;
}
export type NativeCharacter = "adult_female" | "adult_male" | "child";
export const characterStyles: { id: NativeCharacter; name: string }[] = [
  { id: "adult_female", name: "스카프와 초록 드레스" },
  { id: "adult_male", name: "파란 겉옷" },
  { id: "child", name: "노란 멜빵 캐릭터" },
];
export function nativeCharacter(appearance?: string): NativeCharacter {
  if (
    appearance?.startsWith("v2:") &&
    characterStyles.some((c) => c.id === appearance.slice(3))
  )
    return appearance.slice(3) as NativeCharacter;
  return appearance === "blue" || /^v1:.2/.test(appearance || "")
    ? "adult_male"
    : "adult_female";
}
export interface Point {
  x: number;
  y: number;
}
export function canStand(p: Point, m: RuntimeMap, pet = false) {
  if (!Number.isFinite(p.x) || !Number.isFinite(p.y)) return false;
  const left = p.x - (pet ? 5 : 6), right = p.x + (pet ? 5 : 6);
  const top = p.y - (pet ? 5 : 8), bottom = p.y;
  const [x, y, width, height] = m.walkBounds;
  return left >= x && right <= x + width && top >= y && bottom <= y + height &&
    !m.objects.some(({ collision }) => {
      if (!collision) return false;
      const [cx, cy, w, h] = collision;
      return left < cx + w && right > cx && top < cy + h && bottom > cy;
    });
}
export function petClearsPeople(pet: Point, people: readonly Point[]) {
  // Actors are anchored at their feet. A pet behind someone can touch the
  // upper part of their sprite even when their feet are far apart.
  return people.every((person) => {
    const dy = pet.y - person.y;
    return Math.abs(pet.x - person.x) >= 20 || dy <= -40 || dy >= 18;
  });
}

export function nearestFreePoint(p: Point, canOccupy: (point: Point) => boolean): Point | null {
  const center = {
    x: Math.max(8, Math.min(440, Math.round(p.x / 4) * 4)),
    y: Math.max(12, Math.min(312, Math.round(p.y / 4) * 4)),
  };
  if (canOccupy(center)) return center;
  for (let r = 1; r < 112; r++)
    for (let i = -r; i <= r; i++)
      for (const q of [
        { x: center.x + i * 4, y: center.y - r * 4 },
        { x: center.x + i * 4, y: center.y + r * 4 },
        { x: center.x - r * 4, y: center.y + i * 4 },
        { x: center.x + r * 4, y: center.y + i * 4 },
      ])
        if (canOccupy(q)) return q;
  return null;
}
export function nearest(p: Point, m: RuntimeMap, pet = false): Point {
  return nearestFreePoint(p, (point) => canStand(point, m, pet))
    ?? { x: m.spawns[0][0], y: m.spawns[0][1] };
}
export function path(
  from: Point,
  to: Point,
  m: RuntimeMap,
  pet = false,
  avoids?: (point: Point) => boolean,
): Point[] {
  const canOccupy = (point: Point) => canStand(point, m, pet) && (!avoids || avoids(point));
  const a = nearestFreePoint(from, canOccupy);
  const b = nearestFreePoint(to, canOccupy);
  if (!a || !b) return [];
  const key = (p: Point) => `${p.x},${p.y}`,
    open = [a],
    cost = new Map([[key(a), 0]]),
    came = new Map<string, Point>(),
    closed = new Set<string>();
  let count = 0;
  while (open.length && count++ < 10000) {
    open.sort(
      (x, y) =>
        cost.get(key(x))! +
        Math.abs(x.x - b.x) +
        Math.abs(x.y - b.y) -
        (cost.get(key(y))! + Math.abs(y.x - b.x) + Math.abs(y.y - b.y)),
    );
    const p = open.shift()!,
      k = key(p);
    if (k === key(b)) {
      const out = [b];
      let q = b;
      while (came.has(key(q))) {
        q = came.get(key(q))!;
        out.unshift(q);
      }
      return out;
    }
    if (closed.has(k)) continue;
    closed.add(k);
    for (const [dx, dy] of [
      [4, 0],
      [-4, 0],
      [0, 4],
      [0, -4],
    ]) {
      const q = { x: p.x + dx, y: p.y + dy },
        n = key(q),
        c = cost.get(k)! + 4;
      if (
        !closed.has(n) &&
        canOccupy(q) &&
        c < (cost.get(n) ?? Infinity)
      ) {
        cost.set(n, c);
        came.set(n, p);
        open.push(q);
      }
    }
  }
  return [];
}
export interface OcclusionActor extends Point {
  pet?: boolean;
  kind?: string;
}
// Ported without geometry changes from the supplied shared/occlusion.js.
export function occludes(o: MapObject, a: OcclusionActor) {
  if (!o.occlusion) return false;
  const half = a.pet ? 9 : 10,
    height = a.pet ? 16 : a.kind === "child" ? 32 : 38,
    bottom = o.y + o.height - (o.occlusion.rearInset || 16);
  return (
    a.y > o.y + 2 &&
    a.y <= bottom &&
    a.x + half > o.x + 2 &&
    a.x - half < o.x + o.width - 2 &&
    a.y - height < bottom
  );
}
export function fade(current: number, target: number, dt: number, ms = 160) {
  const step = (0.45 * Math.max(0, dt) * 1000) / Math.max(1, ms);
  return current < target
    ? Math.min(target, current + step)
    : Math.max(target, current - step);
}
export function renderPreview(
  c: CanvasRenderingContext2D,
  m: RuntimeMap,
  images: Map<string, HTMLImageElement>,
  d: SpaceDesign,
) {
  c.imageSmoothingEnabled = false;
  c.clearRect(0, 0, 448, 320);
  c.drawImage(images.get(m.backdrop)!, 0, 0);
  const magnet = fridgeMagnet(d);
  const drawObject = (o: MapObject) => {
    c.drawImage(images.get(o.sprite)!, o.x, o.y);
    if (o.id === "fridge" && magnet !== undefined) {
      c.fillStyle = "#" + magnet.toString(16);
      c.fillRect(o.x + 18, o.y + 18, 4, 4);
      c.fillRect(o.x + 19, o.y + 16, 2, 8);
    }
  };
  m.objects.filter((o) => o.layer === "ground").forEach(drawObject);
  m.objects.filter((o) => o.layer === "wall").forEach(drawObject);
  m.objects.filter((o) => o.layer === "depth")
    .sort((a, b) => a.depth - b.depth)
    .forEach(drawObject);
}
