import type { PetHabitat } from "../lib/pets";

export function makePetHabitat(habitat: Exclude<PetHabitat, "FLOOR">) {
  const canvas = document.createElement("canvas");
  canvas.width = 30;
  canvas.height = 24;
  const ctx = canvas.getContext("2d")!;
  const rect = (color: string, x: number, y: number, w: number, h: number) => {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, w, h);
  };
  rect("#59412e", 2, 21, 26, 3);
  rect("#ab7952", 3, 21, 24, 1);
  if (habitat === "PERCH") {
    rect("#745339", 14, 8, 3, 13);
    rect("#af8157", 6, 10, 19, 2);
    rect("#6f8154", 11, 4, 8, 6);
    rect("#a9ae74", 17, 2, 5, 6);
    rect("#413329", 20, 3, 1, 1);
    rect("#dfad57", 22, 5, 3, 2);
    rect("#566745", 8, 7, 4, 2);
    return canvas;
  }
  rect("#5a4b3c", 1, 3, 28, 18);
  rect("#dce6d9", 3, 5, 24, 14);
  if (habitat === "TANK") {
    rect("#80b6b3", 3, 7, 24, 12);
    rect("#a3c9bc", 4, 8, 10, 1);
    rect("#587950", 6, 13, 2, 6);
    rect("#f4c581", 12, 11, 8, 4);
    rect("#df9463", 20, 10, 2, 6);
    rect("#493629", 13, 12, 1, 1);
    rect("#dbe6d9", 23, 8, 2, 2);
  } else {
    rect("#c6a37c", 3, 15, 24, 4);
    rect("#74875b", 6, 9, 4, 7);
    rect("#9ba278", 4, 11, 4, 2);
    rect("#a37752", 14, 12, 9, 5);
    rect("#644c38", 16, 15, 4, 3);
    if (habitat === "SMALL_HABITAT") {
      rect("#928976", 4, 5, 1, 10);
      rect("#928976", 24, 5, 1, 10);
    }
  }
  rect("#826b51", 2, 3, 26, 2);
  return canvas;
}
