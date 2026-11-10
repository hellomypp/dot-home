import { observationSize } from "./observationScene";

export function makeClassroom() {
  const canvas = document.createElement("canvas");
  canvas.width = observationSize.width;
  canvas.height = observationSize.height;
  const ctx = canvas.getContext("2d")!;
  const rect = (color: string, x: number, y: number, w: number, h: number) => {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, w, h);
  };
  rect("#eee1bf", 0, 0, 320, 108);
  rect("#faf0d5", 0, 6, 320, 64);
  rect("#d1c6a3", 0, 93, 320, 11);
  rect("#a27f5b", 0, 104, 320, 4);
  rect("#d2a06d", 0, 108, 320, 116);
  for (let y = 113; y < 224; y += 13) {
    rect("#ae7854", 0, y, 320, 1);
    rect("#e3b480", 0, y + 1, 320, 1);
    for (let x = (y * 9) % 45; x < 320; x += 53) {
      rect("#af7a54", x, y + 2, 1, 11);
      rect("#c38f61", x + 6, y + 7, 9 + (x % 8), 1);
    }
  }

  // Tall window, treetops and a stripe of sunlight over the wood.
  rect("#9b7958", 13, 22, 83, 60);
  rect("#e2c294", 16, 24, 77, 55);
  rect("#a7d1d1", 19, 27, 71, 49);
  for (const [x, y, w] of [[25, 37, 19], [61, 32, 21]]) {
    rect("#f0f3df", x, y, w, 4);
    rect("#f0f3df", x + 5, y - 3, w - 8, 3);
  }
  for (const [x, y, w] of [[20, 59, 20], [39, 64, 22], [58, 55, 23], [75, 60, 14]]) {
    rect("#75a793", x, y + 3, w, 75 - y - 3);
    rect("#91b99a", x + 3, y, w - 5, 5);
  }
  rect("#e2c294", 52, 26, 4, 52);
  rect("#e2c294", 18, 52, 73, 3);
  rect("#a58360", 11, 79, 87, 4);
  rect("#f5d8a4", 12, 78, 85, 2);
  for (let row = 0; row < 80; row += 2) rect("#f5db9d38", Math.floor(16 + row * 0.8), 108 + row, 53, 2);

  // Blackboard, chalk rail and a wall clock.
  rect("#805b47", 109, 22, 151, 64);
  rect("#c49c68", 110, 23, 148, 60);
  rect("#3b6357", 115, 28, 138, 50);
  rect("#34564d", 115, 74, 138, 4);
  rect("#4b7160", 117, 29, 132, 2);
  rect("#84624c", 112, 83, 145, 3);
  rect("#f1e5cf", 217, 80, 10, 2);
  rect("#dbaa8d", 231, 80, 6, 2);
  rect("#a78c63", 276, 26, 24, 23);
  rect("#f7ebc9", 278, 28, 20, 19);
  rect("#695842", 287, 31, 2, 8);
  rect("#695842", 287, 37, 6, 2);
  rect("#bb9c72", 269, 57, 34, 27);
  rect("#fcf1d4", 271, 59, 30, 23);
  rect("#789b83", 274, 63, 24, 4);
  for (let y = 70; y < 80; y += 4) rect("#cfbc99", 275, y, 20, 1);

  rect("#b68b64", 112, 94, 146, 10);
  rect("#d5af80", 112, 94, 146, 6);
  rect("#90694f", 108, 105, 154, 6);
  rect("#e1bc8a", 108, 104, 154, 3);

  // A low bookcase and a few classroom supplies.
  rect("#997454", 10, 89, 32, 27);
  rect("#c59b69", 12, 90, 28, 23);
  rect("#765b49", 12, 104, 28, 3);
  for (const [x, y, color] of [[14, 94, "#668d79"], [20, 92, "#c98b77"], [25, 95, "#edc17d"], [32, 92, "#788eb0"]] as const)
    rect(color, x, y, 4, 104 - y);
  rect("#b78361", 49, 95, 12, 9);
  rect("#6f966f", 53, 85, 4, 11);
  rect("#88ae7e", 47, 88, 9, 4);
  rect("#6f966f", 56, 85, 8, 5);
  rect("#92724f", 267, 106, 29, 11);
  rect("#c49b6a", 265, 102, 32, 5);
  rect("#e0c4a0", 269, 100, 13, 3);
  return canvas;
}
