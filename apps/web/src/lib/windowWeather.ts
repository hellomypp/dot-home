import { useEffect, useRef, useState } from "react";
import { skipToken, useQuery } from "@tanstack/react-query";

export type WindowWeather = "clear" | "cloud" | "fog" | "rain" | "snow" | "storm";
export type WindowLight = "day" | "dawn" | "dusk" | "night";
export interface WindowSky {
  weather: WindowWeather | null;
  light: WindowLight;
}
type ForecastObservation = { weather: WindowWeather; isDay: boolean; observedAt: number; timezone: string };
export interface WindowWeatherView extends WindowSky {
  source: "live" | "recent" | "fallback";
  observation?: ForecastObservation;
}

const weatherCodes: Record<WindowWeather, number[]> = {
  clear: [0, 1],
  cloud: [2, 3],
  fog: [45, 48],
  rain: [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82],
  snow: [71, 73, 75, 77, 85, 86],
  storm: [95, 96, 99],
};
export function classifyWeather(code: unknown): WindowWeather | null {
  return (Object.keys(weatherCodes) as WindowWeather[]).find((kind) =>
    typeof code === "number" && weatherCodes[kind].includes(code)
  ) ?? null;
}

export function windowLight(hour: number, isDay = hour >= 6 && hour < 19): WindowLight {
  if (!isDay) return "night";
  if (hour < 8) return "dawn";
  if (hour >= 17) return "dusk";
  return "day";
}

type Region = { latitude: string; longitude: string };
export const weatherRefreshInterval = 30 * 60 * 1000;
export const maxWeatherAge = 2 * 60 * 60 * 1000;
const clockOptions = { hour: "2-digit", hourCycle: "h23" } as const;
const deviceClock = new Intl.DateTimeFormat("ko-KR", clockOptions);

export function parseForecast(result: {
  timezone?: unknown;
  current?: { weather_code?: unknown; is_day?: unknown; time?: unknown };
}, now = Date.now()): ForecastObservation {
  const weather = classifyWeather(result.current?.weather_code);
  const daylight = result.current?.is_day;
  const stamp = result.current?.time;
  if (!weather || (daylight !== 0 && daylight !== 1) || typeof result.timezone !== "string" ||
      typeof stamp !== "number" || !Number.isSafeInteger(stamp) || stamp <= 0 || stamp * 1000 > now + 900_000) {
    throw new Error("Weather data unavailable");
  }
  new Intl.DateTimeFormat("ko-KR", { ...clockOptions, timeZone: result.timezone });
  return { weather, isDay: daylight === 1, timezone: result.timezone, observedAt: stamp * 1000 };
}

export function selectWindowWeather(now: Date, data?: ForecastObservation, providerFailed = false): WindowWeatherView {
  const age = data ? now.getTime() - data.observedAt : Infinity;
  const observation = data && age <= maxWeatherAge ? data : undefined;
  const clock = observation
    ? new Intl.DateTimeFormat("ko-KR", { ...clockOptions, timeZone: observation.timezone })
    : deviceClock;
  const hour = Number(clock.formatToParts(now).find(part => part.type === "hour")?.value);
  const live = !!observation && !providerFailed && age <= weatherRefreshInterval;
  return {
    light: windowLight(hour, live ? observation?.isDay : undefined),
    weather: observation?.weather ?? null,
    source: observation ? live ? "live" : "recent" : "fallback",
    observation,
  };
}

async function readWeather(region: Region, signal: AbortSignal) {
  const params = new URLSearchParams({
    ...region,
    current: "weather_code,is_day",
    timezone: "auto",
    timeformat: "unixtime",
    forecast_days: "1",
  });
  const request = new AbortController();
  const cancel = () => request.abort();
  if (signal.aborted) cancel();
  signal.addEventListener("abort", cancel, { once: true });
  const timeout = window.setTimeout(cancel, 8000);
  try {
    const response = await fetch("https://api.open-meteo.com/v1/forecast?" + params, {
      signal: request.signal, credentials: "omit", referrerPolicy: "no-referrer",
    });
    if (!response.ok) throw new Error("Weather request failed");
    return parseForecast(await response.json());
  } finally {
    window.clearTimeout(timeout);
    signal.removeEventListener("abort", cancel);
  }
}

export function useWindowSky(inHome: boolean): WindowWeatherView {
  const [now, setNow] = useState(() => new Date());
  const [region, setRegion] = useState<Region | null>(null);
  const requestedLocation = useRef(false);
  useEffect(() => {
    if (!inHome || requestedLocation.current || !navigator.geolocation) return;
    requestedLocation.current = true;
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      // Use an approximate area for the weather request.
      setRegion({ latitude: coords.latitude.toFixed(1), longitude: coords.longitude.toFixed(1) });
    }, undefined, { enableHighAccuracy: false, maximumAge: 300_000, timeout: 10_000 });
  }, [inHome]);
  useEffect(() => {
    const updateTime = () => setNow(new Date());
    const timer = window.setInterval(updateTime, 30_000);
    document.addEventListener("visibilitychange", updateTime);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", updateTime);
    };
  }, []);
  const forecast = useQuery({
    queryKey: ["window-weather", region],
    queryFn: inHome && region ? ({ signal }) => readWeather(region, signal) : skipToken,
    staleTime: weatherRefreshInterval,
    refetchInterval: weatherRefreshInterval,
    gcTime: maxWeatherAge,
    retry: false,
  });
  return selectWindowWeather(now, forecast.data, forecast.isError);
}
