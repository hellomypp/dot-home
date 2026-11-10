import type { WindowSky } from "../lib/windowWeather";

// Pixel outlines for the four panes, measured from each 52×37 window sprite.
const panes = {
  "warm-wood-home": [
    [[17, 6], [24, 6], [24, 15], [15, 15], [17, 11]],
    [[28, 6], [35, 6], [37, 15], [28, 15]],
    [[15, 18], [24, 18], [24, 29], [16, 28], [14, 24]],
    [[28, 18], [37, 18], [39, 23], [37, 28], [28, 29]],
  ],
  sunny_apartment: [
    [[17, 5], [24, 5], [24, 15], [14, 15], [17, 10]],
    [[28, 5], [35, 5], [35, 11], [37, 15], [28, 15]],
    [[14, 18], [24, 18], [24, 28], [17, 28], [13, 22]],
    [[28, 18], [38, 18], [39, 21], [35, 28], [28, 28]],
  ],
  brick_loft: [
    [[19, 7], [24, 7], [24, 16], [15, 16], [19, 11]],
    [[28, 7], [33, 7], [34, 12], [37, 16], [28, 16]],
    [[13, 19], [24, 19], [24, 28], [15, 28], [13, 24]],
    [[28, 19], [39, 19], [38, 25], [36, 28], [28, 28]],
  ],
};
const colors = {
  day: ["#7fb4cf", "#bad9e5", "#577d51", "#f6db8e"],
  dawn: ["#8e9ac7", "#ebbfaf", "#666f69", "#ffe0aa"],
  dusk: ["#9d82ad", "#efad88", "#665865", "#ffdb9d"],
  night: ["#243852", "#485570", "#314854", "#efe6b5"],
};

export function paintWindowSky(ctx: CanvasRenderingContext2D, presetId: string, sky: WindowSky) {
  ctx.clearRect(0, 0, 52, 37);
  const glass = panes[presetId as keyof typeof panes];
  if (!glass) return;
  ctx.save();
  ctx.beginPath();
  for (const pane of glass) {
    const [[x, y], ...rest] = pane;
    ctx.moveTo(x, y);
    for (const [px, py] of rest) ctx.lineTo(px, py);
    ctx.closePath();
  }
  ctx.clip();
  const [top, horizon, hills, light] = colors[sky.light];
  const fill = (color: string, x: number, y: number, width: number, height: number) => {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, width, height);
  };
  fill(top, 0, 0, 52, 37);
  fill(horizon, 0, 18, 52, 19);
  fill(hills, 11, 25, 32, 12);
  fill(hills, 11, 23, 9, 2);
  fill(hills, 33, 22, 10, 3);

  if (sky.weather === "clear" || sky.weather === null) {
    if (sky.light === "night") {
      fill(light, 19, 8, 3, 5);
      fill(light, 21, 11, 2, 2);
      fill(light, 32, 9, 1, 1);
      fill(light, 36, 13, 1, 1);
    } else {
      const y = sky.light === "day" ? 8 : 18;
      fill(light, 30, y, 4, 4);
      fill(light, 29, y + 1, 6, 2);
    }
  } else {
    const cloud = sky.light === "night" ? "#76849b" : "#d9dce0";
    fill(cloud, 13, 12, 28, 3);
    fill(cloud, 16, 10, 14, 2);
    if (sky.weather === "fog") {
      fill(cloud, 12, 19, 29, 3);
      fill(cloud, 12, 25, 29, 2);
    }
    if (sky.weather === "rain" || sky.weather === "storm" || sky.weather === "snow") {
      for (let x = 15; x < 41; x += 4) {
        const y = 19 + (x % 3);
        if (sky.weather === "snow") {
          fill("#fff6e8", x, y, 1, 1);
          fill("#fff6e8", x + 1, y + 5, 1, 1);
        } else {
          const drop = sky.light === "night" ? "#98b3c8" : "#4e718d";
          fill(drop, x, y, 1, 2);
          fill(drop, x - 1, y + 2, 1, 2);
        }
      }
    }
    if (sky.weather === "storm") {
      fill("#ffe59d", 31, 15, 2, 4);
      fill("#ffe59d", 30, 18, 3, 1);
      fill("#ffe59d", 30, 19, 1, 3);
    }
  }
  ctx.restore();
}
