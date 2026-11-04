import { useRef, useState } from "react";
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
export function SpaceSettings({
  snapshot,
  onSaved,
}: {
  snapshot: Snapshot;
  onSaved: (world: Snapshot) => void;
}) {
  const source = snapshot.design || defaultDesign,
    [draft, setDraft] = useState<SpaceDesign>(source),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [saved, setSaved] = useState(false),
    pending = useRef(false);
  const owner =
    snapshot.members.find((m) => m.id === snapshot.meMemberId)?.role ===
    "OWNER";
  async function save() {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const data = await api<Snapshot>(
        `/api/households/${snapshot.household.id}/design`,
        "PATCH",
        draft,
      );
      onSaved(data);
      setDraft(data.design!);
      setSaved(true);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return (
    <section className="space-settings">
      <p>가족과 함께 보는 집 분위기예요. 저장 전 미리보기는 나에게만 보여요.</p>
      <SpacePreview design={draft} />
      {owner ? (
        <>
          <PresetPicker
            disabled={busy}
            value={draft.presetId}
            onChange={(presetId) => {
              setDraft({ ...draft, presetId });
              setSaved(false);
            }}
          />
          <label>
            냉장고 외형
            <select
              disabled={busy}
              value={draft.variants.fridge || ""}
              onChange={(e) => {
                const variants = { ...draft.variants };
                if (e.target.value) variants.fridge = e.target.value;
                else delete variants.fridge;
                setDraft({ ...draft, variants });
                setSaved(false);
              }}
            >
              <option value="">선택한 집의 기본 외형</option>
              {catalogue.variants.fridge.map((v) => (
                <option value={v} key={v}>{labels[v]}</option>
              ))}
            </select>
          </label>
          <div className="form-actions">
            <button
              className="button secondary"
              disabled={busy}
              onClick={() => {
                setDraft(source);
                setError("");
                setSaved(false);
              }}
            >
              미리보기 취소
            </button>
            <button
              className="button primary"
              disabled={busy}
              onClick={() => void save()}
            >
              {busy ? "저장 중…" : "집 분위기 저장"}
            </button>
          </div>
          {error && (
            <>
              <p role="alert" className="error-message">
                {error}
              </p>
              <button
                className="button secondary"
                onClick={async () => {
                  try {
                    const d = await api<SpaceDesign>(
                      `/api/households/${snapshot.household.id}/design`,
                    );
                    setDraft(d);
                    setError("");
                  } catch (e) {
                    setError((e as Error).message);
                  }
                }}
              >
                최신 설정 다시 가져오기
              </button>
            </>
          )}
          {saved && <p role="status">집 분위기를 저장했어요.</p>}
        </>
      ) : (
        <p>집 분위기는 대표가 바꿀 수 있어요.</p>
      )}
    </section>
  );
}
