import { expect, it } from "vitest";
import {
  classifyWeather, maxWeatherAge, parseForecast, selectWindowWeather, weatherRefreshInterval, windowLight,
} from "./windowWeather";

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

const observedAt = Date.parse("2026-11-10T07:00:00Z");
const payload = { timezone: "Asia/Seoul", current: { time: observedAt / 1000, weather_code: 63, is_day: 1 } };

it("reads a Unix observation in the forecast region's timezone", () => {
  const data = parseForecast(payload, observedAt);
  expect(selectWindowWeather(new Date(observedAt), data)).toMatchObject({ weather: "rain", source: "live", light: "day" });
  expect(selectWindowWeather(new Date(observedAt + weatherRefreshInterval), data).source).toBe("live");
  expect(() => parseForecast({ ...payload, timezone: "invalid-zone" }, observedAt)).toThrow();
  expect(() => parseForecast({ ...payload, current: { ...payload.current, time: "2026-11-10T16:00" } }, observedAt)).toThrow();
});

it("keeps the last weather during an outage, updates light and falls back at two hours", () => {
  const data = parseForecast(payload, observedAt);
  expect(selectWindowWeather(new Date(observedAt + 1000), data, true)).toMatchObject({
    weather: "rain", source: "recent", observation: { observedAt },
  });
  expect(selectWindowWeather(new Date(observedAt + 90 * 60_000), data, true)).toMatchObject({
    weather: "rain", light: "dusk", source: "recent",
  });
  expect(selectWindowWeather(new Date(observedAt + maxWeatherAge), data).source).toBe("recent");
  expect(selectWindowWeather(new Date(observedAt + maxWeatherAge + 1), data, true)).toMatchObject({
    weather: null, source: "fallback", observation: undefined,
  });
});
