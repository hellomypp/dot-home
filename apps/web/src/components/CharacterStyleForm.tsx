import { useRef, useState } from "react";
import { api, type Snapshot } from "../lib/api";
import { characterStyles, nativeCharacter } from "../game/spaces";
import { Portrait } from "./Portrait";
export function CharacterChoices({
  value,
  onChange,
  disabled = false,
}: {
  value: string;
  disabled?: boolean;
  onChange: (v: string) => void;
}) {
  return (
    <fieldset className="character-styles">
      <legend>캐릭터 스타일</legend>
      {characterStyles.map((c) => (
        <button
          type="button"
          disabled={disabled}
          key={c.id}
          aria-pressed={nativeCharacter(value) === c.id}
          onClick={() => onChange("v2:" + c.id)}
        >
          <Portrait appearance={"v2:" + c.id} />
          {c.name}
        </button>
      ))}
    </fieldset>
  );
}
export function CharacterStyleForm({
  snapshot,
  onSaved,
}: {
  snapshot: Snapshot;
  onSaved: (w: Snapshot) => void;
}) {
  const own = snapshot.members.find((m) => m.id === snapshot.meMemberId)!,
    [appearance, setAppearance] = useState(own.appearance),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    lock = useRef(false);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        if (lock.current) return;
        lock.current = true;
        setBusy(true);
        setError("");
        try {
          onSaved(
            await api<Snapshot>(
              `/api/households/${snapshot.household.id}/me/profile`,
              "PATCH",
              { memberName: own.name, appearance },
            ),
          );
        } catch (e) {
          setError((e as Error).message);
        } finally {
          lock.current = false;
          setBusy(false);
        }
      }}
    >
      <CharacterChoices
        disabled={busy}
        value={appearance}
        onChange={setAppearance}
      />
      <p className="muted">
        기존 외형 값은 저장 전까지 유지돼요. 새 스타일은 제공된 v6.2 캐릭터예요.
      </p>
      <button
        className="button secondary"
        disabled={busy || appearance === own.appearance}
      >
        {busy ? "저장 중…" : "캐릭터 스타일 저장"}
      </button>
      {error && <p role="alert">{error}</p>}
    </form>
  );
}
