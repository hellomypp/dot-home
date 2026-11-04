import { expect, it } from "vitest";
import { classifyWeather, windowLight } from "./windowWeather";

it("maps the forecast's weather codes to window effects", () => {
  expect([0, 3, 45, 66, 86, 99].map(classifyWeather))
    .toEqual(["clear", "cloud", "fog", "rain", "snow", "storm"]);
  expect(classifyWeather(999)).toBeNull();
  expect(classifyWeather(null)).toBeNull();
});

it("uses reported daylight when available and device hours otherwise", () => {
  expect([7, 12, 18, 23].map((hour) => windowLight(hour)))
    .toEqual(["dawn", "day", "dusk", "night"]);
  expect(windowLight(12, false)).toBe("night");
  expect(windowLight(21, true)).toBe("dusk");
});
