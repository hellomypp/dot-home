export interface Point { x: number; y: number }
export interface Rect extends Point { width: number; height: number }
export const WORLD_WIDTH = 640;
export const WORLD_HEIGHT = 448;
export const FLOOR: Rect = { x: 44, y: 130, width: 552, height: 276 };
export const HOME_SOLIDS: Rect[] = [
  { x: 336, y: 120, width: 184, height: 46 },
  { x: 542, y: 100, width: 44, height: 65 },
  { x: 70, y: 218, width: 130, height: 45 },
  { x: 92, y: 304, width: 96, height: 32 },
  { x: 55, y: 335, width: 30, height: 30 },
  { x: 361, y: 226, width: 78, height: 51 },
  { x: 510, y: 245, width: 75, height: 110 },
  { x: 242, y: 130, width: 32, height: 60 },
  { x: 236, y: 326, width: 63, height: 36 },
];
export const OTHER_SOLIDS: Rect[] = [{ x: 112, y: 200, width: 120, height: 48 }, { x: 410, y: 200, width: 110, height: 48 }];
export function walkable(point: Point, solids: Rect[] = HOME_SOLIDS, radius = 7): boolean {
  if (point.x < FLOOR.x + radius || point.y < FLOOR.y + radius || point.x > FLOOR.x + FLOOR.width - radius || point.y > FLOOR.y + FLOOR.height - radius) return false;
  return !solids.some(r => point.x > r.x - radius && point.x < r.x + r.width + radius && point.y > r.y - radius && point.y < r.y + r.height + radius);
}

const STEP = 8;
const key = (p: Point) => `${p.x},${p.y}`;
function gridPoint(point: Point): Point { return { x: Math.round(point.x / STEP) * STEP, y: Math.round(point.y / STEP) * STEP }; }
export function nearestWalkable(point: Point, solids: Rect[] = HOME_SOLIDS): Point {
  const center = gridPoint({ x: Math.max(52, Math.min(588, point.x)), y: Math.max(138, Math.min(398, point.y)) });
  if (walkable(center, solids)) return center;
  for (let radius = 1; radius < 70; radius++) {
    for (let x = -radius; x <= radius; x++) for (const y of [-radius, radius]) {
      const p = { x: center.x + x * STEP, y: center.y + y * STEP };
      if (walkable(p, solids)) return p;
    }
    for (let y = -radius + 1; y < radius; y++) for (const x of [-radius, radius]) {
      const p = { x: center.x + x * STEP, y: center.y + y * STEP };
      if (walkable(p, solids)) return p;
    }
  }
  return { x: 320, y: 288 };
}

/** Four-way A* avoids diagonal corner clipping; feet radius is included in all checks. */
export function findPath(from: Point, to: Point, solids: Rect[] = HOME_SOLIDS): Point[] {
  const start = nearestWalkable(from, solids), goal = nearestWalkable(to, solids);
  const open = [start], came = new Map<string, Point>(), costs = new Map<string, number>([[key(start), 0]]), closed = new Set<string>();
  const distance = (a: Point) => Math.abs(a.x - goal.x) + Math.abs(a.y - goal.y);
  let iterations = 0;
  while (open.length && iterations++ < 5000) {
    open.sort((a, b) => (costs.get(key(a))! + distance(a)) - (costs.get(key(b))! + distance(b)));
    const current = open.shift()!, currentKey = key(current);
    if (currentKey === key(goal)) {
      const path: Point[] = [goal];
      let cursor = goal;
      while (came.has(key(cursor))) { cursor = came.get(key(cursor))!; path.unshift(cursor); }
      return path;
    }
    if (closed.has(currentKey)) continue;
    closed.add(currentKey);
    for (const [dx, dy] of [[STEP, 0], [-STEP, 0], [0, STEP], [0, -STEP]]) {
      const next = { x: current.x + dx, y: current.y + dy }, nextKey = key(next);
      if (!walkable(next, solids) || closed.has(nextKey)) continue;
      const score = costs.get(currentKey)! + STEP;
      if (score < (costs.get(nextKey) ?? Infinity)) { costs.set(nextKey, score); came.set(nextKey, current); open.push(next); }
    }
  }
  return [];
}
