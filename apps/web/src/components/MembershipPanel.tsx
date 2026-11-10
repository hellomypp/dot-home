import { useRef, useState } from "react";
import { api, type Member, type Snapshot } from "../lib/api";
import { Portrait } from "./Portrait";
import { OwnershipTransferPanel } from "./OwnershipTransferPanel";

type Confirmation = { kind: "leave"; requestId: string } | { kind: "remove"; member: Member };

export function MembershipPanel({ snapshot, onLeave, refresh }: {
  snapshot: Snapshot;
  onLeave: () => void;
  refresh: () => Promise<unknown>;
}) {
  const own = snapshot.members.find((member) => member.id === snapshot.meMemberId);
  const owner = own?.role === "OWNER";
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const pending = useRef(false);

  async function submit() {
    if (!confirmation || pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    const root = `/api/households/${snapshot.household.id}`;
    try {
      if (confirmation.kind === "leave") {
        await api(root + "/me/leave", "POST", { requestId: confirmation.requestId });
        onLeave();
      } else {
        const { member } = confirmation;
        await api(`${root}/members/${member.id}?version=${member.membershipVersion ?? 0}`, "DELETE");
        await refresh();
        setMessage(`${member.name} 님의 참여가 종료됐어요.`);
        setConfirmation(null);
      }
    } catch (error) {
      setError((error as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function review(next: Confirmation) {
    setError("");
    setMessage("");
    setConfirmation(next);
  }

  return (
    <section className="pet-manager" aria-label="집 참여 관리">
      <p><strong>{snapshot.household.name}</strong>의 참여를 관리해요.</p>
      {confirmation ? (
        <div className="settings-section">
          <h3>{confirmation.kind === "leave" ? "이 집에서 나갈까요?" : `${confirmation.member.name} 님의 참여를 종료할까요?`}</h3>
          <p>참여가 끝나면 이 집의 캐릭터와 접근 권한이 사라져요. 다른 집과 계정은 계속 이용할 수 있어요.</p>
          <p>공동 기록은 ‘퇴실한 구성원’으로 남고 다시 참여하려면 새 초대가 필요해요.</p>
          {error && <p role="alert" className="error-message">{error}</p>}
          <div className="form-actions">
            <button className="button secondary" type="button" disabled={busy} onClick={() => setConfirmation(null)}>돌아가기</button>
            <button className="button secondary logout-button" type="button" disabled={busy} onClick={() => void submit()}>
              {busy ? "처리 중…" : confirmation.kind === "leave" ? "이 집에서 나가기 확인" : "참여 종료 확인"}
            </button>
          </div>
        </div>
      ) : owner ? (
        <>
          {snapshot.members.filter((member) => member.role !== "OWNER").map((member) => (
            <article className="pet-manager-card" key={member.id}>
              <div className="care-profile-summary"><Portrait small appearance={member.appearance} /><strong>{member.name}</strong></div>
              <button className="button secondary" type="button" aria-label={`${member.name} 참여 종료`} onClick={() => review({ kind: "remove", member })}>참여 종료</button>
            </article>
          ))}
          <p className="muted">집 대표는 소유권을 넘긴 뒤 나갈 수 있어요.</p>
        </>
      ) : (
        <button className="button secondary logout-button" type="button" onClick={() => review({ kind: "leave", requestId: crypto.randomUUID() })}>이 집에서 나가기</button>
      )}
      {!confirmation && <OwnershipTransferPanel snapshot={snapshot} refresh={refresh} />}
      {message && <p role="status">{message}</p>}
    </section>
  );
}
