import { useRef, useState } from "react";
import { api, type Snapshot } from "../lib/api";

export function HouseholdDeletionPanel({ household, refresh }: {
  household: Snapshot["household"];
  refresh: () => Promise<unknown>;
}) {
  const [confirm, setConfirm] = useState(false);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const requestId = useRef<string>(undefined);
  const pending = useRef(false);
  const deadline = household.pendingDeleteUntil;

  async function submit(cancel: boolean) {
    if (pending.current || (!cancel && name.trim() !== household.name)) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const root = `/api/households/${household.id}/deletion`;
      const version = household.deletionVersion ?? 0;
      if (cancel) await api(root + "/cancel", "POST", { version });
      else {
        requestId.current ??= crypto.randomUUID();
        await api(root, "POST", { requestId: requestId.current, version });
        requestId.current = undefined;
      }
      await refresh();
      setConfirm(false);
      setName("");
    } catch (error) {
      setError((error as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="pet-manager" aria-label="집 삭제 관리">
      <p><strong>{household.name}</strong></p>
      {deadline ? (
        <>
          <p role="status">삭제 대기 중이에요. 가족은 취소 기한까지 공동 기록을 보고 내려받을 수 있어요.</p>
          <p>취소 기한 <time dateTime={deadline}>{new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(deadline))}</time></p>
          <button className="button primary" disabled={busy} onClick={() => void submit(true)}>집 삭제 요청 취소</button>
        </>
      ) : (
        <>
          <p>삭제를 요청하면 7일 동안 집이 읽기 전용으로 바뀌고 기존 집들이 링크가 취소돼요. 기간이 지나면 이 집과 기록을 삭제해요.</p>
          {confirm ? (
            <form className="pet-form" onSubmit={(event) => { event.preventDefault(); void submit(false); }}>
              <label>확인할 집 이름
                <input value={name} disabled={busy} onChange={(event) => setName(event.target.value)} autoComplete="off" />
              </label>
              <div className="form-actions">
                <button className="button secondary" type="button" disabled={busy} onClick={() => setConfirm(false)}>돌아가기</button>
                <button className="button secondary logout-button" disabled={busy || name.trim() !== household.name}>7일 유예 시작</button>
              </div>
            </form>
          ) : <button className="button secondary logout-button" onClick={() => setConfirm(true)}>집 삭제 요청 확인</button>}
        </>
      )}
      {error && <p role="alert" className="error-message">{error}</p>}
    </section>
  );
}
