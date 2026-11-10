import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";

const categories = {
  EDIT: { label: "집 편집", detail: "편집 요청과 승인, 권한 종료" },
  TASK: { label: "가족 부탁", detail: "내가 받는 새 부탁" },
  NOTICE: { label: "가족 공지", detail: "내가 받는 새 공지" },
  ARRIVAL: { label: "가족 귀가", detail: "위치를 공유한 가족의 귀가 소식" },
} as const;
type NotificationGroup = keyof typeof categories;
interface Settings {
  enabled: Record<NotificationGroup, boolean>;
  version: number;
}

export function NotificationSettings({ house, readOnly, back }: {
  house: string;
  readOnly: boolean;
  back: () => void;
}) {
  const path = `/api/households/${house}/me/notification-settings`;
  const key = ["notification-settings", house] as const;
  const client = useQueryClient();
  const query = useQuery({ queryKey: key, queryFn: () => api<Settings>(path) });
  const [draft, setDraft] = useState<Settings>();
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const current = draft ?? query.data;

  async function save() {
    if (!draft || pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const result = await api<Settings>(path, "PATCH", draft);
      client.setQueryData(key, result);
      setDraft(undefined);
      setSaved(true);
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="notification-settings" aria-busy={busy || query.isPending}>
      <p>이 집에서 내가 받을 알림을 선택해요. 이전에 받은 알림은 알림함에 남아요.</p>
      {readOnly && <p className="muted">삭제 대기 중인 집의 설정은 볼 수 있어요.</p>}
      {query.isPending && <p role="status">알림 설정을 불러오는 중이에요.</p>}
      {query.isError && <p role="alert" className="error-message">알림 설정을 불러오지 못했어요.</p>}
      {current && (
        <fieldset disabled={busy || readOnly}>
          <legend>받을 알림</legend>
          {(Object.keys(categories) as NotificationGroup[]).filter(category => Object.hasOwn(current.enabled, category)).map(category => (
            <label className="checkbox-label" key={category}>
              <input type="checkbox" checked={current.enabled[category] ?? false}
                onChange={event => {
                  setSaved(false);
                  setDraft({ ...current, enabled: { ...current.enabled, [category]: event.target.checked } });
                }} />
              <span><strong>{categories[category].label}</strong>
                <small>{categories[category].detail}</small></span>
            </label>
          ))}
        </fieldset>
      )}
      {saved && <p role="status">알림 설정을 저장했어요.</p>}
      {error && <p role="alert" className="error-message">{error}</p>}
      {(query.isError || error) && (
        <button className="button secondary" disabled={busy} onClick={async () => {
          const latest = await query.refetch();
          if (latest.isSuccess) {
            setDraft(undefined);
            setError("");
          }
        }}>최신 설정 다시 불러오기</button>
      )}
      <div className="form-actions">
        <button className="button secondary" disabled={busy} onClick={back}>알림함으로</button>
        {!readOnly && <button className="button primary" disabled={busy || !draft} onClick={() => void save()}>
          알림 설정 저장
        </button>}
      </div>
    </section>
  );
}
