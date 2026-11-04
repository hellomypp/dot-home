import { useId, useRef, useState, type FormEvent, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, ApiError, places } from "../lib/api";
import type { Place } from "../game/types";
import {
  expressionFields, expressionKeys, expressionLabel,
  type ExpressionField, type ExpressionState,
} from "../lib/expressions";

export function ExpressionForm({
  householdId, householdName, currentPlace, onSaved, onPlaceSaved, legacy,
}: {
  householdId: string;
  householdName: string;
  currentPlace: Place;
  onSaved: () => void;
  onPlaceSaved: () => void;
  legacy: ReactNode;
}) {
  const path = `/api/households/${householdId}/me/state`;
  const query = useQuery({
    queryKey: ["member-expressions", householdId],
    queryFn: () => api<ExpressionState>(path),
    retry: false,
    staleTime: 0,
    gcTime: 0,
  });
  if (query.error instanceof ApiError && query.error.status === 404) return legacy;
  if (query.isPending) return <p role="status">내 표현을 불러오고 있어요…</p>;
  if (query.error) return (
    <div>
      <p role="alert">{query.error.message}</p>
      <button type="button" className="button secondary" onClick={() => void query.refetch()}>다시 시도</button>
    </div>
  );
  return (
    <div className="expression-panel">
      <Editor initial={query.data} path={path} householdName={householdName} onSaved={onSaved} />
      <details>
        <summary>공유 장소 바꾸기</summary>
        <PlaceForm householdId={householdId} initial={currentPlace} onSaved={onPlaceSaved} />
      </details>
    </div>
  );
}

const initialValues = (state: ExpressionState) => Object.fromEntries(
  expressionKeys.map((key) => [key, state.expressions[key].value ?? ""]),
) as Record<ExpressionField, string>;

function Editor({
  initial, path, householdName, onSaved,
}: {
  initial: ExpressionState;
  path: string;
  householdName: string;
  onSaved: () => void;
}) {
  const id = useId();
  const [base, setBase] = useState(initial);
  const [values, setValues] = useState(() => initialValues(initial));
  const [duration, setDuration] = useState<Partial<Record<ExpressionField, number>>>({});
  const [latest, setLatest] = useState<ExpressionState>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const receipt = useRef<{ body: string; id: string } | null>(null);

  function setValue(field: ExpressionField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    if (!value.trim()) setDuration((current) => ({ ...current, [field]: undefined }));
  }
  const changes = expressionKeys.flatMap((field) => {
    const value = values[field].trim() || null;
    const ttl = duration[field];
    const previous = base.expressions[field];
    if (value === previous.value && !ttl) return [];
    return [[field, { value, version: previous.version, ...(value && ttl ? { ttlMinutes: ttl } : {}) }]] as const;
  });
  const exceedsLimit = changes.some(([key, { value }]) => {
    const config = expressionFields[key];
    return "maxLength" in config && [...(value ?? "")].length > config.maxLength;
  });
  async function save(event: FormEvent) {
    event.preventDefault();
    if (pending.current || !changes.length || exceedsLimit || latest) return;
    pending.current = true;
    setBusy(true);
    setError("");
    const fields = Object.fromEntries(changes);
    const body = JSON.stringify(fields);
    if (receipt.current?.body !== body) receipt.current = { body, id: crypto.randomUUID() };
    try {
      await api<ExpressionState>(path, "PATCH", { requestId: receipt.current.id, changes: fields });
      onSaved();
    } catch (err) {
      setError((err as Error).message);
      if (err instanceof ApiError && err.status === 409) {
        try { setLatest(await api<ExpressionState>(path)); } catch {}
      }
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <form className="expression-form" onSubmit={save}>
      <p className="form-intro"><strong>{householdName}</strong>에 공유해요. 수정한 항목만 갱신돼요.</p>
      {expressionKeys.map((field) => {
        const config = expressionFields[field];
        const value = values[field];
        const options = "options" in config ? config.options : null;
        const expiresAt = base.expressions[field].expiresAt;
        const current = base.expressions[field].value;
        return (
          <fieldset key={field} disabled={busy}>
            <legend>{config.label}</legend>
            <label htmlFor={id + field}>
              <span className="sr-only">{config.label}</span>
              {options ? (
                <select id={id + field} value={value} onChange={(e) => setValue(field, e.target.value)}>
                  <option value="">공유하지 않아요</option>
                  {value && !Object.hasOwn(options, value) && <option value={value}>{value} (기존 표현)</option>}
                  {Object.entries(options).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
                </select>
              ) : (
                <input id={id + field} value={value} onChange={(e) => setValue(field, e.target.value)}
                  placeholder={"maxLength" in config ? `${config.maxLength}자 이내` : undefined} />
              )}
            </label>
            {current && expiresAt && <small>현재 공개 기한 {new Date(expiresAt).toLocaleString("ko-KR")}</small>}
            {value.trim() && (!options || Object.hasOwn(options, value)) && (
              <label>
                <span className="sr-only">{config.label} 유지 시간</span>
                <select value={duration[field] ?? ""} onChange={(e) => {
                  const ttl = Number(e.target.value) || undefined;
                  setDuration((current) => ({ ...current, [field]: ttl }));
                }}>
                  <option value="">{current === value.trim() ? "현재 기한 유지" : `기본 유지 시간 · ${config.defaultHours}시간`}</option>
                  <option value="30">30분</option><option value="60">1시간</option>
                  <option value="120">2시간</option><option value="240">4시간</option>
                  <option value="360">6시간</option><option value="1440">24시간</option>
                </select>
              </label>
            )}
          </fieldset>
        );
      })}
      {exceedsLimit && <p className="error-message" role="alert">한마디는 60자, 음식은 30자 이내로 입력해 주세요.</p>}
      {error && <p role="alert" className="error-message">{error}</p>}
      {latest && (
        <div className="expression-conflict">
          <p>가족에게 보이는 최신값과 내 입력을 확인해 주세요.</p>
          <ul>
            {changes.map(([field]) => {
              const value = latest.expressions[field].value;
              return <li key={field}>{expressionFields[field].label} · 최신 {value ? expressionLabel(field, value) : "비어 있음"} / 내 입력 {values[field] ? expressionLabel(field, values[field]) : "비우기"}</li>;
            })}
          </ul>
          <button type="button" className="button secondary" onClick={() => {
            // Adopt fresh versions only for a deliberate retry. Keep untouched fields fresh.
            const edited = new Set(changes.map(([field]) => field));
            setValues((current) => Object.fromEntries(expressionKeys.map((field) =>
              [field, edited.has(field) ? current[field] : latest.expressions[field].value ?? ""],
            )) as Record<ExpressionField, string>);
            setBase(latest);
            setLatest(undefined);
            setError("");
          }}>최신값 확인 · 내 입력 유지</button>
        </div>
      )}
      <button type="submit" className="button primary" disabled={busy || exceedsLimit || !changes.length || Boolean(latest)}>
        {busy ? "공유 중…" : "표현 공유하기"}
      </button>
    </form>
  );
}


function PlaceForm({ householdId, initial, onSaved }: {
  householdId: string;
  initial: Place;
  onSaved: () => void;
}) {
  const [place, setPlace] = useState(initial);
  const [savedPlace, setSavedPlace] = useState(initial);
  const [saved, setSaved] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy || place === savedPlace) return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/households/${householdId}/me/status`, "PATCH", { place });
      setSavedPlace(place);
      setSaved(true);
      onSaved();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={save}>
      <label>내가 공유할 장소
        <select value={place} onChange={(event) => {
          setPlace(event.target.value as Place);
          setSaved(false);
        }}>
          {Object.entries(places).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select>
      </label>
      {error && <p role="alert" className="error-message">{error}</p>}
      {saved && <p role="status">공유 장소를 저장했어요.</p>}
      <button type="submit" className="button secondary" disabled={busy || place === savedPlace}>장소 저장</button>
    </form>
  );
}
