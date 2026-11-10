import { useRef, useState } from "react";
import { api, type Snapshot } from "../lib/api";

export const editScopeLabels = { PRESET: "집 프리셋", VARIANTS: "가구 외형", LAYOUT: "가구 배치" } as const;
export type EditScope = keyof typeof editScopeLabels;
export interface SpaceEditRequest {
  id: string;
  memberId: string;
  scope: EditScope[];
  state: "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED" | "USED" | "EXPIRED";
  expiresAt: string;
  startBefore: string | null;
  version: number;
}
export interface SpaceEditInbox {
  requests: SpaceEditRequest[];
  activeEditor: { sessionId: string; memberId: string; expiresAt: string } | null;
}

export function SpaceEditRequests({ snapshot, owner, inbox, reload, editingNow }: {
  snapshot: Snapshot;
  owner: boolean;
  inbox?: SpaceEditInbox;
  reload: () => Promise<unknown>;
  editingNow: boolean;
}) {
  const [scope, setScope] = useState<EditScope[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const inFlight = useRef(false);
  const path = `/api/households/${snapshot.household.id}/edit-requests`;
  const requests = inbox?.requests ?? [];
  const own = owner ? undefined : requests[0];
  const editing = inbox?.activeEditor;
  const anotherEditor = editing && editing.memberId !== snapshot.meMemberId ? editing : null;
  const name = (memberId: string) => snapshot.members.find(member => member.id === memberId)?.name ?? "가족";

  async function mutate(endpoint: string, body?: unknown) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    try {
      await api(endpoint, "POST", body);
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      await reload().catch((failure: Error) => setError(failure.message));
      inFlight.current = false;
      setBusy(false);
    }
  }

  if (!inbox || owner && !requests.length && !anotherEditor && !error) return null;
  return (
    <section className="space-access" aria-label="집 편집 권한" aria-busy={busy}>
      {owner ? (
        <>
          <h3>가족의 편집 요청</h3>
          {anotherEditor && (
            <div className="space-access-card">
              <p><strong>{name(anotherEditor.memberId)}</strong> 님이 편집 중이에요.</p>
              <button type="button" className="button secondary" disabled={busy} onClick={() => void mutate(
                `/api/households/${snapshot.household.id}/edit-sessions/${anotherEditor.sessionId}/revoke`,
              )}>편집 권한 회수</button>
            </div>
          )}
          {requests.map(request => (
            <div className="space-access-card" key={request.id}>
              <strong>{name(request.memberId)}</strong>
              <p>{request.scope.map(item => editScopeLabels[item]).join(" · ")}</p>
              {request.state === "PENDING" ? (
                <div className="form-actions">
                  <button type="button" className="button secondary" disabled={busy} onClick={() => void mutate(
                    `${path}/${request.id}/reject`, { requestId: crypto.randomUUID(), version: request.version },
                  )}>거절</button>
                  <button type="button" className="button primary" disabled={busy} onClick={() => void mutate(
                    `${path}/${request.id}/approve`, { requestId: crypto.randomUUID(), version: request.version },
                  )}>한 번 승인</button>
                </div>
              ) : (
                <p>승인됨 {anotherEditor?.memberId !== request.memberId && (
                  <button type="button" className="button secondary" disabled={busy} onClick={() => void mutate(
                    `${path}/${request.id}/cancel`, { version: request.version },
                  )}>승인 취소</button>
                )}</p>
              )}
            </div>
          ))}
        </>
      ) : own?.state === "PENDING" ? (
        <div className="space-access-card">
          <p role="status">집 대표의 승인을 기다리고 있어요.</p>
          <button type="button" className="button secondary" disabled={busy} onClick={() => void mutate(
            `${path}/${own.id}/cancel`, { version: own.version },
          )}>요청 취소</button>
        </div>
      ) : own?.state === "APPROVED" ? (
        <div className="space-access-card">
          <p role="status">편집을 승인받았어요. {own.scope.map(item => editScopeLabels[item]).join(" · ")}을 한 번 저장할 수 있어요.</p>
          {!editingNow && <button type="button" className="button secondary" disabled={busy} onClick={() => void mutate(
            `${path}/${own.id}/cancel`, { version: own.version },
          )}>편집 권한 반납</button>}
        </div>
      ) : (
        <>
          <h3>집 편집 요청</h3>
          {own?.state === "REJECTED" && <p role="status">이전 요청은 거절됐어요.</p>}
          {own?.state === "CANCELLED" && <p role="status">이전 편집 권한이 종료됐어요.</p>}
          {own?.state === "EXPIRED" && <p role="status">이전 요청 또는 승인의 기한이 지났어요.</p>}
          <fieldset className="space-access-options">
            <legend>바꾸고 싶은 항목을 골라 주세요.</legend>
            {(Object.keys(editScopeLabels) as EditScope[]).map(item => (
              <label className="checkbox-label" key={item}>
                <input type="checkbox" checked={scope.includes(item)} disabled={busy}
                  onChange={(event) => setScope(event.target.checked ? [...scope, item] : scope.filter(selected => selected !== item))} />
                {editScopeLabels[item]}
              </label>
            ))}
          </fieldset>
          <button type="button" className="button secondary" disabled={busy || !scope.length}
            onClick={() => void mutate(path, { requestId: crypto.randomUUID(), scope })}>대표에게 요청하기</button>
        </>
      )}
      {error && <p role="alert" className="error-message">{error}</p>}
    </section>
  );
}
