import raw from "../../../../assets/space-catalog.json";

export interface SurfaceChoices {
  floorId: string;
  wallId: string;
}

type SurfaceOption = { id: string; name: string; color?: string };
export const surfaceCatalogue = raw.surfaces as unknown as Record<"floor" | "wall", {
  bounds: [number, number, number, number];
  options: SurfaceOption[];
}>;
export const defaultSurfaces: SurfaceChoices = { floorId: "original", wallId: "original" };

function shade(color: string) {
  return [1, 3, 5].map(offset => parseInt(color.slice(offset, offset + 2), 16));
}

function luma(pixels: Uint8ClampedArray, i: number) {
  return 0.299 * pixels[i] + 0.587 * pixels[i + 1] + 0.114 * pixels[i + 2];
}

// Preserve the detail and alpha of the source pixel art while changing the finish.
function tintRegion(context: CanvasRenderingContext2D, bounds: [number, number, number, number], color: string) {
  const region = context.getImageData(...bounds);
  const pixels = region.data;
  let total = 0, count = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] === 0) continue;
    total += luma(pixels, i);
    count++;
  }
  if (count === 0) return;
  const average = total / count;
  const target = shade(color);
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i + 3] === 0) continue;
    const source = luma(pixels, i);
    const detail = (source - average) * 0.92;
    for (let channel = 0; channel < 3; channel++)
      pixels[i + channel] = Math.round(target[channel] + detail + (pixels[i + channel] - source) * 0.12);
  }
  context.putImageData(region, bounds[0], bounds[1]);
}

export function hasCustomSurfaces(surfaces?: SurfaceChoices) {
  return !!surfaces && (surfaces.floorId !== "original" || surfaces.wallId !== "original");
}

export function drawHomeArchitecture(
  context: CanvasRenderingContext2D, backdrop: CanvasImageSource, surfaces: SurfaceChoices = defaultSurfaces,
) {
  context.drawImage(backdrop, 0, 0);
  for (const kind of ["floor", "wall"] as const) {
    const { options, bounds } = surfaceCatalogue[kind];
    const finish = options.find(option => option.id === surfaces[kind === "floor" ? "floorId" : "wallId"]);
    if (finish?.color) tintRegion(context, bounds, finish.color);
  }
}
