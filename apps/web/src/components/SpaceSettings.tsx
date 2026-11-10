import { useEffect, useRef, useState } from "react";
import { api, type Snapshot } from "../lib/api";
import { catalogue, defaultDesign, mapFor, type SpaceDesign } from "../game/spaces";
import { SpacePreview } from "./SpacePreview";
import "./spaces.css";
export function PresetPicker({
  value,
  onChange,
  disabled = false,
}: {
  value: string;
  disabled?: boolean;
  onChange: (id: string) => void;
}) {
  return (
    <fieldset className="preset-picker">
      <legend>완성된 집 선택</legend>
      {catalogue.presets.map((p) => (
        <button
          type="button"
          disabled={disabled}
          key={p.id}
          aria-label={p.name}
          aria-pressed={mapFor(value).id === p.id}
          onClick={() => onChange(p.id)}
        >
          <SpacePreview design={{ presetId: p.id, version: 0, variants: {} }} />
          <strong>{p.name}</strong>
        </button>
      ))}
    </fieldset>
  );
}
const labels: Record<string, string> = {
  original: "기본 냉장고",
  leaf: "초록 자석",
  star: "노란 자석",
};
interface EditSession {
  id: string;
  status: "ACTIVE" | "RECOVERABLE" | "ENDED" | "COMMITTED";
  draft: SpaceDesign;
  draftVersion: number;
  committedVersion: number | null;
}

export function SpaceSettings({
  snapshot,
  onSaved,
}: {
  snapshot: Snapshot;
  onSaved: (world: Snapshot) => void;
}) {
  const source = snapshot.design || defaultDesign;
  const canEdit = !snapshot.household.pendingDeleteUntil &&
    snapshot.members.some((member) => member.id === snapshot.meMemberId && member.role === "OWNER");

  if (!canEdit) {
    return (
      <section className="space-settings">
        <SpacePreview design={source} />
        <p>가족과 함께 보는 집 분위기예요.</p>
      </section>
    );
  }
  return <SpaceEditor key={snapshot.household.id} house={snapshot.household.id} source={source} onSaved={onSaved} />;
}

function SpaceEditor({ house, source, onSaved }: {
  house: string;
  source: SpaceDesign;
  onSaved: (world: Snapshot) => void;
}) {
  const path = `/api/households/${house}/edit-sessions`;
  const [session, setSession] = useState<EditSession | null>(null);
  const [unsaved, setUnsaved] = useState<SpaceDesign | null>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const startAttempt = useRef<{ requestId: string; recoverId?: string }>(null);
  const activeId = session?.status === "ACTIVE" ? session.id : null;
  const draft = unsaved ?? (activeId ? session!.draft : source);

  useEffect(() => {
    let mounted = true;
    void api<{ session: EditSession | null }>(path).then((data) => {
      if (mounted) {
        setSession(data.session);
        setReady(true);
      }
    }).catch((failure: Error) => {
      if (mounted) setError(failure.message);
    });
    return () => { mounted = false; };
  }, [path]);

  useEffect(() => {
    if (!activeId) return;
    let mounted = true;
    const renew = () => {
      if (document.visibilityState === "hidden") return;
      void api(`${path}/${activeId}/heartbeat`, "POST").catch((failure: Error) => {
        if (mounted) setError(failure.message);
      });
    };
    const timer = window.setInterval(renew, 30_000);
    document.addEventListener("visibilitychange", renew);
    return () => {
      mounted = false;
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", renew);
    };
  }, [activeId, path]);

  async function run(action: () => Promise<void>) {
    if (pending.current) return;
    pending.current = true;
    setError("");
    setBusy(true);
    try {
      await action();
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function refresh() {
    return run(async () => {
      const data = await api<{ session: EditSession | null }>(path);
      setSession(data.session);
      setUnsaved(null);
      setReady(true);
      if (data.session?.status === "COMMITTED") {
        onSaved(await api<Snapshot>(`/api/households/${house}/world`));
      }
    });
  }

  function start(recoverId?: string) {
    return run(async () => {
      if (startAttempt.current?.recoverId !== recoverId || !startAttempt.current)
        startAttempt.current = { requestId: crypto.randomUUID(), recoverId };
      setSession(await api<EditSession>(path, "POST", startAttempt.current));
      startAttempt.current = null;
    });
  }

  function changeDraft(design: SpaceDesign) {
    if (!activeId || !session) return;
    return run(async () => {
      setUnsaved(design);
      setSession(await api<EditSession>(`${path}/${activeId}/draft`, "PATCH", {
        draftVersion: session.draftVersion,
        design,
      }));
      setUnsaved(null);
    });
  }

  function commit() {
    if (!activeId || !session) return;
    return run(async () => {
      const data = await api<{ world: Snapshot; committedVersion: number }>(`${path}/${activeId}/commit`, "POST", {
        requestId: activeId,
        baseDesignVersion: session.draft.version,
        draftVersion: session.draftVersion,
      });
      onSaved(data.world);
      setSession({ ...session, status: "COMMITTED", committedVersion: data.committedVersion });
    });
  }

  function cancel() {
    if (!activeId) return;
    return run(async () => {
      setSession(await api<EditSession>(`${path}/${activeId}/cancel`, "POST"));
      setUnsaved(null);
    });
  }

  return (
    <section className="space-settings" aria-busy={!ready && !error || busy}>
      <p>가족과 함께 보는 집 분위기예요. 저장 전까지 변경 내용은 나에게만 보여요.</p>
      <SpacePreview design={draft} />
      {!ready && !error && <p role="status">집 분위기를 불러오는 중이에요.</p>}
      {ready && !activeId && (
        <>
          {session?.status === "COMMITTED" && <p role="status">집 분위기를 저장했어요.</p>}
          {session?.status === "RECOVERABLE" && <p>이전 초안을 이어서 편집할 수 있어요.</p>}
          <div className="form-actions">
            {session?.status === "RECOVERABLE" && (
              <button className="button primary" disabled={busy} onClick={() => void start(session.id)}>이전 초안 이어서</button>
            )}
            <button className="button secondary" disabled={busy} onClick={() => void start()}>
              {session?.status === "RECOVERABLE" ? "새로 편집" : "집 분위기 편집"}
            </button>
          </div>
        </>
      )}
      {activeId && session && (
        <>
          <p className="muted">선택한 내용은 초안으로 보관돼요. 편집은 한 번에 최대 20분 동안 할 수 있어요.</p>
          <PresetPicker disabled={busy || !!unsaved} value={draft.presetId}
            onChange={(presetId) => void changeDraft({ ...draft, presetId })} />
          <label>
            냉장고 외형
            <select
              disabled={busy || !!unsaved}
              value={draft.variants.fridge || ""}
              onChange={(e) => {
                const variants = { ...draft.variants };
                if (e.target.value) variants.fridge = e.target.value;
                else delete variants.fridge;
                void changeDraft({ ...draft, variants });
              }}
            >
              <option value="">선택한 집의 기본 외형</option>
              {catalogue.variants.fridge.map((v) => (
                <option value={v} key={v}>{labels[v]}</option>
              ))}
            </select>
          </label>
          <div className="form-actions">
            <button className="button secondary" disabled={busy} onClick={() => void cancel()}>편집 종료</button>
            <button className="button primary" disabled={busy || !!unsaved || session.draftVersion === 0}
              onClick={() => void commit()}>집 분위기 저장</button>
          </div>
        </>
      )}
      {error && (
        <>
          <p role="alert" className="error-message">{error}</p>
          <div className="form-actions">
            {unsaved && <button className="button secondary" disabled={busy} onClick={() => void changeDraft(unsaved)}>초안 다시 저장</button>}
            <button className="button secondary" disabled={busy} onClick={() => void refresh()}>편집 상태 다시 확인</button>
          </div>
        </>
      )}
    </section>
  );
}
