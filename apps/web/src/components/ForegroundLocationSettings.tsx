import { useRef, useState } from "react";
import { ApiError } from "../lib/api";
import type { ForegroundLocationControl } from "../lib/foregroundLocation";

export function ForegroundLocationSettings({ location, house, name }: {
  location: ForegroundLocationControl;
  house: string;
  name: string;
}) {
  const { settings, device, anchor, permissionIssue, refresh } = location;
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);

  if (settings.error instanceof ApiError && settings.error.status === 404) return null;
  const config = settings.data;
  const registered = device.data;
  const replace = Boolean(registered?.generation && !registered.currentDevice);
  const switching = Boolean(registered?.activeHouseId && registered.activeHouseId !== house);
  const here = anchor?.house === house && anchor?.configId === config?.homeConfigId && registered?.currentDevice;
  async function save(enable: boolean) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      if (enable) await location.enable(replace);
      else await location.disable();
      setConfirm(false);
    } catch (e) {
      setError((e as Error).message);
      await refresh();
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="foreground-location">
      <h3>접속 중 자동 위치 공유</h3>
      <p className="muted">동의하면 <strong>{name}</strong> 가족에게 집에 있는지 또는 밖에 있는지만 알려요. 집의 실제 위치는 이 브라우저에 저장하고, 화면을 열어 둔 동안에만 확인해요.</p>
      {settings.isPending && <p role="status">위치 공유 설정을 불러오는 중이에요…</p>}
      {(settings.isError || device.isError) && <div>
        <p role="alert">자동 위치 설정을 불러올 수 없어요.</p>
        <button type="button" className="button secondary" onClick={() => void refresh()}>다시 시도</button>
      </div>}
      {config && !config.generalSharing && <p>위에서 이 집의 위치 공유를 먼저 켜 주세요.</p>}
      {config?.generalSharing && registered && (
        <>
          <p role="status">{config.enabled ? (here ? "이 브라우저의 자동 공유가 켜져 있어요." : "등록된 기기의 자동 공유가 켜져 있어요.") : "자동 위치 공유가 꺼져 있어요."}</p>
          {config.enabled && registered.currentDevice && !here && <p>이 브라우저에 집 기준 위치가 없어요. 다시 등록해 주세요.</p>}
          {config.enabled && here && permissionIssue && <p className="error-message">{permissionIssue}</p>}
          {confirm ? (
            <div>
              <p>{replace ? "기존 기기의 자동 공유가 중단돼요. " : ""}{switching ? "이전 집의 자동 공유가 중단돼요. " : ""}지금 집에 있다면 현재 위치를 기준으로 등록해 주세요.</p>
              <button type="button" className="button primary" disabled={busy} onClick={() => void save(true)}>
                {busy ? "위치 확인 중…" : "현재 위치를 집으로 등록하고 공유"}
              </button>
              <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirm(false)}>취소</button>
            </div>
          ) : (
            <div className="location-actions">
              <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirm(true)}>
                {replace ? "이 기기로 교체" : config.enabled && registered.currentDevice ? "집 기준 위치 다시 등록" : "자동 위치 공유 켜기"}
              </button>
              {config.enabled && <button type="button" className="button secondary" disabled={busy}
                onClick={() => void save(false)}>자동 위치 공유 끄기</button>}
            </div>
          )}
        </>
      )}
      {error && <p className="error-message" role="alert">{error}</p>}
    </section>
  );
}
