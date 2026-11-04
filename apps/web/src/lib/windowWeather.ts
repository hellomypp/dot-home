import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

export type WindowWeather = "clear" | "cloud" | "fog" | "rain" | "snow" | "storm";
export type WindowLight = "day" | "dawn" | "dusk" | "night";
export interface WindowSky {
  weather: WindowWeather | null;
  light: WindowLight;
}

const weatherCodes: Record<WindowWeather, number[]> = {
  clear: [0, 1],
  cloud: [2, 3],
  fog: [45, 48],
  rain: [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82],
  snow: [71, 73, 75, 77, 85, 86],
  storm: [95, 96, 99],
};
export const weatherNames: Record<WindowWeather, string> = {
  clear: "맑음", cloud: "흐림", fog: "안개", rain: "비", snow: "눈", storm: "뇌우",
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
async function readWeather(region: Region, signal: AbortSignal) {
  const params = new URLSearchParams({
    ...region,
    current: "weather_code,is_day",
    timezone: "auto",
    forecast_days: "1",
  });
  const response = await fetch("https://api.open-meteo.com/v1/forecast?" + params, {
    signal, credentials: "omit", referrerPolicy: "no-referrer",
  });
  if (!response.ok) throw new Error("Weather request failed");
  const result = await response.json();
  const weather = classifyWeather(result.current?.weather_code);
  const daylight = result.current?.is_day;
  if (!weather || (daylight !== 0 && daylight !== 1) || typeof result.timezone !== "string") {
    throw new Error("Weather data unavailable");
  }
  const clock = new Intl.DateTimeFormat("ko-KR", {
    timeZone: result.timezone, hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  });
  return { weather, isDay: daylight === 1, clock };
}

const deviceClock = new Intl.DateTimeFormat("ko-KR", {
  hour: "2-digit", minute: "2-digit", hourCycle: "h23",
});
const refreshInterval = 15 * 60 * 1000;

export function useWindowWeather() {
  const [now, setNow] = useState(() => new Date());
  const [region, setRegion] = useState<Region | null>(null);
  const [locating, setLocating] = useState(false);
  const [locationError, setLocationError] = useState("");
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
    queryFn: ({ signal }) => readWeather(region!, signal),
    enabled: region !== null,
    staleTime: refreshInterval,
    refetchInterval: refreshInterval,
    gcTime: 0,
    retry: false,
  });
  const data = Date.now() - forecast.dataUpdatedAt <= 2 * refreshInterval ? forecast.data : undefined;
  const clock = data?.clock ?? deviceClock;
  const hour = Number(clock.formatToParts(now).find((part) => part.type === "hour")?.value);
  const sky: WindowSky = { light: windowLight(hour, data?.isDay), weather: data?.weather ?? null };

  function connect() {
    setLocationError("");
    if (!navigator.geolocation) {
      setLocationError("이 브라우저에서 위치를 확인할 수 없어요.");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(({ coords }) => {
      setLocating(false);
      // The weather service only needs an approximate area, not the device's exact position.
      setRegion({ latitude: coords.latitude.toFixed(1), longitude: coords.longitude.toFixed(1) });
    }, (error) => {
      setLocating(false);
      setLocationError(error.code === 1
        ? "위치 권한을 허용하면 지역 날씨를 볼 수 있어요."
        : "위치를 확인하지 못했어요. 다시 시도해 주세요.");
    }, { enableHighAccuracy: false, maximumAge: 300_000, timeout: 10_000 });
  }

  return {
    sky, time: clock.format(now), connected: region !== null,
    busy: locating || (region !== null && forecast.isFetching),
    error: locationError || (forecast.isError ? "날씨를 불러오지 못했어요." : ""),
    connect,
    retry: () => void forecast.refetch(),
    disconnect: () => setRegion(null),
  };
}
