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
  width: number;
  height: number;
  tileWidth: number;
  tileHeight: number;
  ground: number[][];
  architecture: number[][];
  collision: number[];
  objects: MapObject[];
  spawns: number[][];
  visitorSpawn: number[];
  tileset?: string;
  backdrop?: string;
  previewCrop?: [number, number, number, number];
  walkBounds?: [number, number, number, number];
  petSpawn?: number[];
}
export const catalogue = raw as unknown as {
  maps: Record<string, RuntimeMap>;
  presets: { id: string; name: string }[];
  variants: Record<string, string[]>;
  defaults: Record<string, Record<string, string>>;
};
export const defaultDesign: SpaceDesign = {
  presetId: "garden_home",
  version: 0,
  variants: {},
};
export const assetBase = "/art/v62/";
export const warmWoodPreset = "warm-wood-home";
export const assetUrl = (file: string) => file.startsWith("/") ? file : assetBase + file;
export function mapFor(id = "garden_home") {
  return (
    catalogue.maps[id === "classic_home" ? "garden_home" : id] ||
    catalogue.maps.garden_home
  );
}
export function variantsFor(d: SpaceDesign) {
  return { ...catalogue.defaults[mapFor(d.presetId).id], ...d.variants };
}
export function spriteFor(o: MapObject, d: SpaceDesign) {
  if (d.presetId === warmWoodPreset) return o.sprite;
  const v = variantsFor(d);
  return o.id === "sofa"
    ? `assets/objects/${{ sage: "sage_sofa", blue: "blue_sofa", leather: "leather_sofa" }[v.sofa] || "sage_sofa"}.png`
    : o.id === "dining_table"
      ? `assets/objects/${v.table === "round" ? "round_dining" : "wood_dining"}.png`
      : o.sprite;
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
  if (m.walkBounds) {
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
  return (pet ? [-5, 4] : [-6, 5]).every((dx) =>
    (pet ? [-5, -1] : [-8, -1]).every((dy) => {
      const x = Math.floor((p.x + dx) / 16),
        y = Math.floor((p.y + dy) / 16);
      return x >= 0 && x < 28 && y >= 0 && y < 20 && !m.collision[y * 28 + x];
    }),
  );
}
export function nearest(p: Point, m: RuntimeMap, pet = false): Point {
  const center = {
    x: Math.max(8, Math.min(440, Math.round(p.x / 4) * 4)),
    y: Math.max(12, Math.min(312, Math.round(p.y / 4) * 4)),
  };
  if (canStand(center, m, pet)) return center;
  for (let r = 1; r < 112; r++)
    for (let i = -r; i <= r; i++)
      for (const q of [
        { x: center.x + i * 4, y: center.y - r * 4 },
        { x: center.x + i * 4, y: center.y + r * 4 },
        { x: center.x - r * 4, y: center.y + i * 4 },
        { x: center.x + r * 4, y: center.y + i * 4 },
      ])
        if (canStand(q, m, pet)) return q;
  return { x: m.spawns[0][0], y: m.spawns[0][1] };
}
export function path(
  from: Point,
  to: Point,
  m: RuntimeMap,
  pet = false,
): Point[] {
  const a = nearest(from, m, pet),
    b = nearest(to, m, pet),
    key = (p: Point) => `${p.x},${p.y}`,
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
        canStand(q, m, pet) &&
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
  if (m.backdrop) c.drawImage(images.get(m.backdrop)!, 0, 0);
  const tiles = (grid: number[][]) => {
    if (!m.tileset) return;
    const terrain = images.get(m.tileset)!;
    grid.forEach((row, y) =>
      row.forEach((gid, x) => {
        if (gid)
          c.drawImage(
            terrain,
            ((gid - 1) % 8) * 16,
            Math.floor((gid - 1) / 8) * 16,
            16,
            16,
            x * 16,
            y * 16,
            16,
            16,
          );
      }),
    );
  };
  const obj = (o: MapObject) => {
    const image = images.get(spriteFor(o, d))!;
    c.drawImage(image, o.x + Math.round((o.width - image.width) / 2), o.y);
    if (o.id === "fridge") {
      const v = variantsFor(d).fridge;
      if (v !== "original") {
        c.fillStyle = v === "leaf" ? "#47832b" : "#ffcf76";
        c.fillRect(o.x + 18, o.y + 18, 4, 4);
        c.fillRect(o.x + 19, o.y + 16, 2, 8);
      }
    }
  };
  tiles(m.ground);
  m.objects.filter((o) => o.layer === "ground").forEach(obj);
  tiles(m.architecture);
  m.objects.filter((o) => o.layer === "wall").forEach(obj);
  m.objects
    .filter((o) => o.layer === "depth")
    .sort((a, b) => a.depth - b.depth)
    .forEach(obj);
  m.objects.filter((o) => o.layer === "foreground").forEach(obj);
}
