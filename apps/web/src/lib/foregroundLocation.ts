import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "./api";

export type PhysicalState = "HOME" | "OUTSIDE" | "UNKNOWN";
type Point = { latitude: number; longitude: number };
type Fix = Point & { accuracy: number; time: number };
type Candidate = { place: Exclude<PhysicalState, "UNKNOWN">; since: number };
export type HomeAnchor = Point & { house: string; configId: string; deviceId: string };

interface LocationDevice {
  generation: number;
  currentDevice: boolean;
  activeHouseId: string | null;
}
interface LocationSharing {
  enabled: boolean;
  generation: number;
  version: number;
  generalSharing: boolean;
  homeConfigId: string | null;
}

const devicePath = "/api/me/location-device";
const sharingPath = (house: string) => `/api/households/${house}/me/location-sharing`;
const anchorKey = (user: string) => `dot-home:foreground-location:${user}`;
const browserKey = (user: string) => `dot-home:location-device:${user}`;
const deviceQueryKey = ["location-device"] as const;
const sharingQueryKey = (house?: string) => ["location-sharing", house] as const;
const POLL_MS = 65_000;

export function readAnchor(user?: string): HomeAnchor | null {
  if (!user) return null;
  try {
    const stored = JSON.parse(localStorage.getItem(anchorKey(user)) ?? "null") as HomeAnchor | null;
    if (stored && Number.isFinite(stored.latitude) && Math.abs(stored.latitude) <= 90 &&
      Number.isFinite(stored.longitude) && Math.abs(stored.longitude) <= 180 &&
      typeof stored.house === "string" && typeof stored.deviceId === "string" && typeof stored.configId === "string") return stored;
  } catch { /* Browser storage is optional until the user enables sharing. */ }
  return null;
}

function distanceMeters(a: Point, b: Point) {
  const radians = Math.PI / 180;
  const dLat = (b.latitude - a.latitude) * radians;
  const dLon = (b.longitude - a.longitude) * radians;
  const haversine = Math.sin(dLat / 2) ** 2
    + Math.cos(a.latitude * radians) * Math.cos(b.latitude * radians) * Math.sin(dLon / 2) ** 2;
  return 12_742_000 * Math.asin(Math.sqrt(Math.min(1, haversine)));
}

export function classifyFix(anchor: Point, fix: Fix, previous: Candidate | null, now = Date.now()): {
  state: PhysicalState;
  candidate: Candidate | null;
} {
  if (!Number.isFinite(fix.accuracy) || fix.accuracy < 0 || fix.accuracy > 100
    || fix.time < now - 120_000 || fix.time > now + 30_000) return { state: "UNKNOWN", candidate: null };
  const distance = distanceMeters(anchor, fix);
  const place = distance <= 150 ? "HOME" : distance >= 250 ? "OUTSIDE" : null;
  if (!place) return { state: "UNKNOWN", candidate: null };
  const candidate: Candidate = previous?.place === place && fix.time >= previous.since ? previous : { place, since: fix.time };
  return { state: fix.time - candidate.since >= 60_000 ? place : "UNKNOWN", candidate };
}

function currentFix(): Promise<Fix> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation || !window.isSecureContext) {
      reject(new Error("이 브라우저에서는 보안 연결로 위치를 확인할 수 없어요."));
      return;
    }
    navigator.geolocation.getCurrentPosition(({ coords, timestamp }) => resolve({
      latitude: coords.latitude, longitude: coords.longitude, accuracy: coords.accuracy, time: timestamp,
    }), (error) => reject(new Error(error.code === 1
      ? "브라우저에서 위치를 허용한 뒤 다시 시도해 주세요."
      : "지금 위치를 확인할 수 없어요. 잠시 뒤 다시 시도해 주세요.")), {
      enableHighAccuracy: true, maximumAge: 0, timeout: 12_000,
    });
  });
}

export function useForegroundLocation(house: string | undefined, user: string | undefined,
  available: boolean, loadDevice: boolean, generalSharing: boolean) {
  const queryClient = useQueryClient();
  const [anchor, setAnchor] = useState(() => readAnchor(user));
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [permissionIssue, setPermissionIssue] = useState("");
  useEffect(() => {
    const sync = () => {
      setAnchor(readAnchor(user));
      try { setDeviceId(user ? localStorage.getItem(browserKey(user)) : null); }
      catch { setDeviceId(null); }
    };
    sync();
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, [user]);
  const browser = anchor?.deviceId ?? deviceId;
  const settings = useQuery({
    queryKey: sharingQueryKey(house),
    queryFn: () => api<LocationSharing>(sharingPath(house!)),
    enabled: Boolean(house && user && available && (loadDevice || anchor)),
    retry: false,
  });
  const device = useQuery({
    queryKey: [...deviceQueryKey, user, browser],
    queryFn: () => api<LocationDevice>(browser ? `${devicePath}?deviceId=${browser}` : devicePath),
    enabled: Boolean(house && user && available && loadDevice && settings.data),
    retry: false,
  });
  const config = settings.data;
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: sharingQueryKey(house) }),
    queryClient.invalidateQueries({ queryKey: deviceQueryKey }),
  ]);

  useEffect(() => {
    if (!available || !generalSharing || !house || !config?.enabled || anchor?.house !== house || anchor.configId !== config.homeConfigId) return;
    let candidate: Candidate | null = null;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let controller: AbortController | undefined;
    let lastSent = 0;
    let inactive = false;
    const isVisible = () => !document.hidden && navigator.onLine;
    function stop() {
      clearTimeout(timer);
      controller?.abort();
      controller = undefined;
      candidate = null;
    }
    async function sample() {
      if (!isVisible() || inactive || controller) return;
      const attempt = new AbortController();
      controller = attempt;
      const active = () => !attempt.signal.aborted && isVisible();
      try {
        const fix = await currentFix();
        if (!active()) return;
        const now = Date.now();
        if (fix.time <= lastSent || fix.time < now - 120_000 || fix.time > now + 30_000) return;
        const result = classifyFix(anchor!, fix, candidate, now);
        candidate = result.candidate;
        await api(`/api/households/${house}/me/location-observations`, "POST", {
          requestId: crypto.randomUUID(), deviceId: anchor!.deviceId, generation: config!.generation,
          version: config!.version, homeConfigId: anchor!.configId, observedAt: new Date(fix.time).toISOString(),
          sequence: Math.floor(fix.time), physicalState: result.state,
          fixAgeSeconds: Math.max(0, Math.floor((now - fix.time) / 1000)),
          accuracyMeters: Number.isFinite(fix.accuracy) ? Math.max(0, Math.min(fix.accuracy, 100_000)) : 100_000,
        }, attempt.signal);
        if (active()) {
          lastSent = fix.time;
          setPermissionIssue("");
        }
      } catch (error) {
        if (!active()) return;
        candidate = null;
        if (error instanceof ApiError) {
          if (error.code === "LOCATION_SHARING_INACTIVE" || error.status === 401 || error.status === 403
            || error.status === 404 || error.status === 409 && error.code !== "LOCATION_OBSERVATION_ORDER") {
            inactive = true;
            void queryClient.invalidateQueries({ queryKey: sharingQueryKey(house) });
            void queryClient.invalidateQueries({ queryKey: deviceQueryKey });
          }
        } else setPermissionIssue((error as Error).message);
      } finally {
        if (controller === attempt) {
          controller = undefined;
          if (active() && !inactive) timer = setTimeout(() => void sample(), POLL_MS);
        }
      }
    }
    function visibility() {
      stop();
      if (isVisible()) void sample();
    }
    document.addEventListener("visibilitychange", visibility);
    window.addEventListener("online", visibility);
    window.addEventListener("offline", visibility);
    window.addEventListener("pagehide", stop);
    window.addEventListener("pageshow", visibility);
    void sample();
    return () => {
      document.removeEventListener("visibilitychange", visibility);
      window.removeEventListener("online", visibility);
      window.removeEventListener("offline", visibility);
      window.removeEventListener("pagehide", stop);
      window.removeEventListener("pageshow", visibility);
      stop();
    };
  }, [house, available, generalSharing, anchor, config?.enabled, config?.generation, config?.version, config?.homeConfigId, queryClient]);

  async function enable(replace: boolean) {
    if (!house || !user || !device.data || !config) return;
    const fix = await currentFix();
    if (document.hidden || !Number.isFinite(fix.accuracy) || fix.accuracy > 100
      || Date.now() - fix.time > 120_000) throw new Error("집 근처에서 더 정확한 위치를 확인한 뒤 다시 시도해 주세요.");
    const browserId = browser ?? crypto.randomUUID();
    const next: HomeAnchor = {
      house, deviceId: browserId, configId: crypto.randomUUID(), latitude: fix.latitude, longitude: fix.longitude,
    };
    try { localStorage.setItem(browserKey(user), browserId); }
    catch { throw new Error("이 브라우저에서 집 위치를 저장할 수 없어요."); }
    setDeviceId(browserId);
    const registration = device.data.currentDevice ? device.data : await api<LocationDevice>(devicePath, "PUT", {
      requestId: crypto.randomUUID(), deviceId: browserId, replaceExisting: replace, generation: device.data.generation,
    });
    const latest = await api<LocationSharing>(sharingPath(house));
    if (registration.generation !== latest.generation || !latest.generalSharing
      || latest.version !== config.version + (device.data.currentDevice ? 0 : 1))
      throw new Error("위치 공유 설정이 바뀌었어요. 다시 확인해 주세요.");
    try { localStorage.setItem(anchorKey(user), JSON.stringify(next)); }
    catch { throw new Error("이 브라우저에서 집 위치를 저장할 수 없어요."); }
    try {
      const updated = await api<LocationSharing>(sharingPath(house), "PUT", {
        enabled: true, deviceId: browserId, homeConfigId: next.configId,
        generation: latest.generation, version: latest.version,
      });
      queryClient.setQueryData(sharingQueryKey(house), updated);
      setAnchor(next);
    } catch (error) {
      // A lost response may have committed. Keep the matching anchor until the server answers.
      if (error instanceof ApiError && error.status > 0 && error.status < 500) {
        if (anchor) localStorage.setItem(anchorKey(user), JSON.stringify(anchor));
        else localStorage.removeItem(anchorKey(user));
      } else setAnchor(next);
      throw error;
    }
    setPermissionIssue("");
    await refresh();
  }

  function forgetAnchor() {
    if (!user || anchor?.house !== house) return;
    try { localStorage.removeItem(anchorKey(user)); } catch { /* Server consent is already off. */ }
    setAnchor(null);
  }

  async function disable() {
    if (!house || !user) return;
    const latest = await api<LocationSharing>(sharingPath(house));
    if (latest.enabled) {
      const updated = await api<LocationSharing>(sharingPath(house), "PUT", {
        enabled: false, generation: latest.generation, version: latest.version,
      });
      queryClient.setQueryData(sharingQueryKey(house), updated);
    }
    forgetAnchor();
    setPermissionIssue("");
    await refresh();
  }

  return { settings, device, anchor, permissionIssue, refresh, enable, disable, forgetAnchor };
}

export type ForegroundLocationControl = ReturnType<typeof useForegroundLocation>;
