export const mineSize = { width: 320, height: 224 };

export function makeMoonMine() {
  const canvas = document.createElement("canvas");
  canvas.width = mineSize.width;
  canvas.height = mineSize.height;
  const ctx = canvas.getContext("2d")!;
  const rect = (color: string, x: number, y: number, w: number, h: number) => {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, w, h);
  };
  const polygon = (color: string, points: [number, number][]) => {
    ctx.fillStyle = color;
    ctx.beginPath();
    points.forEach(([x, y], i) => i ? ctx.lineTo(x, y) : ctx.moveTo(x, y));
    ctx.fill();
  };
  const disk = (color: string, x: number, y: number, rx: number, ry: number) => {
    for (let row = -ry; row <= ry; row++) {
      const w = Math.floor(rx * Math.sqrt(1 - (row / ry) ** 2));
      rect(color, x - w, y + row, w * 2 + 1, 1);
    }
  };
  const random = (i: number) => ((Math.imul(i + 53, 0x9e3779b1) ^ (i << 19)) >>> 0) / 4294967296;

  rect("#171a35", 0, 0, 320, 224);
  for (let y = 0; y < 120; y += 4) {
    const t = y / 120;
    rect(`rgb(${23 + Math.round(t * 20)},${26 + Math.round(t * 17)},${53 + Math.round(t * 23)})`, 0, y, 320, 4);
  }
  for (let i = 0; i < 78; i++) {
    const x = Math.floor(random(i * 9) * 320);
    const y = Math.floor(random(i * 9 + 4) * 104);
    rect(i % 5 === 0 ? "#c4bfd7" : "#6d729c", x, y, 1, 1);
    if (i % 13 === 0) {
      rect("#a1b6cf", x - 1, y, 3, 1);
      rect("#a1b6cf", x, y - 1, 1, 3);
      rect("#eef2e0", x, y, 1, 1);
    }
  }

  // A distant ringed planet and its small companion.
  const ring = (front: boolean) => {
    for (let i = 0; i < 80; i++) {
      const a = i * Math.PI / 40;
      if ((Math.sin(a) > 0) !== front) continue;
      const x = Math.round(246 + Math.cos(a) * 55);
      const y = Math.round(45 + Math.sin(a) * 12 - Math.cos(a) * 12);
      rect(front ? "#c0afca" : "#7a789d", x, y, 4, 2);
    }
  };
  ring(false);
  disk("#769da6", 246, 45, 31, 29);
  disk("#93bab5", 240, 39, 24, 20);
  disk("#61858f", 257, 54, 19, 16);
  for (const [y, left, width] of [[31, 224, 43], [40, 218, 53], [48, 218, 57], [58, 227, 44]]) {
    rect("#acc8bc", left, y, width, 2);
  }
  ring(true);
  disk("#b6a0ad", 47, 46, 9, 8);
  disk("#dbc4bd", 44, 43, 5, 4);
  rect("#8d7b9a", 49, 47, 5, 3);

  polygon("#343653", [[0, 109], [29, 94], [48, 101], [75, 80], [88, 80], [117, 103], [147, 91], [164, 101], [188, 84], [215, 94], [249, 108], [282, 87], [303, 91], [320, 104], [320, 150], [0, 150]]);
  polygon("#494460", [[0, 115], [32, 105], [55, 110], [81, 92], [99, 109], [128, 112], [158, 102], [187, 112], [208, 102], [237, 116], [267, 107], [287, 101], [320, 111], [320, 154], [0, 154]]);
  polygon("#605473", [[0, 132], [40, 123], [68, 129], [97, 119], [132, 124], [171, 118], [204, 125], [238, 118], [270, 123], [320, 115], [320, 224], [0, 224]]);
  polygon("#776682", [[0, 151], [49, 139], [87, 146], [125, 135], [163, 139], [205, 133], [247, 143], [280, 135], [320, 143], [320, 224], [0, 224]]);
  for (let i = 0; i < 95; i++) {
    const x = Math.floor(random(i * 11) * 320);
    const y = 148 + Math.floor(random(i * 11 + 3) * 76);
    rect(i % 3 === 0 ? "#a18a9f" : "#635470", x, y, i % 4 + 1, 1);
  }
  for (const [x, y, w, h] of [[44, 157, 23, 6], [252, 159, 17, 5], [284, 208, 31, 8], [69, 202, 24, 6]]) {
    disk("#8f7890", x, y, w + 2, h + 1);
    disk("#60536e", x, y - 1, w, h);
    disk("#6d5a76", x - 2, y - 2, w - 4, h - 2);
  }

  // Tracks, lamp and striped posts mark the little excavation site.
  for (let x = 9; x < 310; x += 12) rect("#514155", x, 214, 4, 10);
  rect("#594a59", 0, 214, 320, 2);
  rect("#b19588", 0, 215, 320, 1);
  rect("#594a59", 0, 221, 320, 2);
  rect("#b19588", 0, 222, 320, 1);
  disk("#6d5c77", 159, 183, 55, 8);
  disk("#7d6980", 95, 181, 19, 4);
  rect("#463e54", 86, 130, 4, 47);
  rect("#a49aac", 86, 132, 1, 41);
  rect("#463e54", 87, 129, 13, 4);
  rect("#4b3f4e", 95, 131, 11, 13);
  rect("#dfbb72", 97, 134, 7, 8);
  rect("#fff0a7", 99, 135, 4, 5);
  for (const x of [112, 226]) {
    rect("#43394d", x - 2, 174, 11, 3);
    rect("#3e3649", x, 155, 6, 21);
    for (let y = 155; y < 174; y += 6) rect("#e1b467", x, y, 6, 3);
  }

  // Crystal seam and the cart, both behind the animated tool and fragments.
  polygon("#484058", [[171, 178], [174, 164], [182, 159], [186, 146], [199, 143], [209, 151], [214, 174], [206, 181], [178, 181]]);
  polygon("#675777", [[175, 175], [180, 164], [187, 162], [190, 149], [198, 146], [209, 153], [209, 170], [199, 175]]);
  polygon("#59b4b8", [[185, 163], [190, 151], [196, 149], [202, 153], [197, 162], [191, 166]]);
  polygon("#b1eee0", [[188, 161], [192, 152], [196, 150], [198, 152], [194, 159]]);
  rect("#d1a15b", 202, 166, 4, 3);
  rect("#f1cb82", 178, 171, 4, 2);
  disk("#514457", 244, 194, 24, 5);
  rect("#453b50", 228, 174, 33, 15);
  rect("#9b7b6d", 231, 176, 28, 9);
  rect("#c4a084", 226, 174, 36, 3);
  rect("#755864", 235, 179, 21, 5);
  for (const x of [234, 255]) {
    disk("#413649", x, 189, 4, 4);
    rect("#a1959e", x - 1, 188, 2, 2);
  }
  return canvas;
}
