import { useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Snapshot } from "../lib/api";

type Offer = {
  id: string;
  fromMemberId: string;
  targetMemberId: string;
  state: "PENDING" | "ACCEPTED" | "CANCELLED" | "EXPIRED";
  expiresAt: string;
  version: number;
};

export function OwnershipTransferPanel({ snapshot, refresh }: {
  snapshot: Snapshot;
  refresh: () => Promise<unknown>;
}) {
  const house = snapshot.household.id;
  const root = `/api/households/${house}/ownership-transfers`;
  const client = useQueryClient();
  const query = useQuery({ queryKey: ["ownership-transfers", house], queryFn: () => api<Offer[]>(root) });
  const [target, setTarget] = useState("");
  const [accepting, setAccepting] = useState<string | null>(null);
  const [adultConfirmed, setAdultConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const pending = useRef(false);
  const attempt = useRef<{ target: string; key: string } | null>(null);
  const own = snapshot.members.find((member) => member.id === snapshot.meMemberId);
  const eligible = snapshot.members.filter((member) => member.id !== snapshot.meMemberId && member.role === "MEMBER");
  const offer = query.data?.[0];
  const name = (id: string) => snapshot.members.find((member) => member.id === id)?.name ?? "가족";
  const incoming = offer?.targetMemberId === snapshot.meMemberId;

  async function update(path: string, data: unknown, success: string) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api(path, "POST", data);
      await Promise.all([client.invalidateQueries({ queryKey: ["ownership-transfers", house] }), refresh()]);
      setAccepting(null);
      setAdultConfirmed(false);
      attempt.current = null;
      setMessage(success);
    } catch (error) {
      setError((error as Error).message);
      await query.refetch();
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function propose(event: FormEvent) {
    event.preventDefault();
    if (!target) return;
    if (attempt.current?.target !== target) attempt.current = { target, key: crypto.randomUUID() };
    void update(root, { targetMemberId: target, requestId: attempt.current.key }, "대표 이전을 제안했어요.");
  }

  return (
    <section className="settings-section" aria-label="집 대표 이전">
      <h3>집 대표 이전</h3>
      {query.isPending ? <p role="status">이전 제안을 확인하고 있어요…</p> : query.isError ? (
        <div>
          <p role="alert">{query.error.message}</p>
          <button className="button secondary" onClick={() => void query.refetch()}>다시 확인</button>
        </div>
      ) : offer?.state === "PENDING" ? (
        <div className="pet-form">
          <p>{incoming ? `${name(offer.fromMemberId)} 님이 집 대표 이전을 제안했어요.` : `${name(offer.targetMemberId)} 님의 수락을 기다려요.`}</p>
          <small>수락 기한 · {new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium", timeStyle: "short" }).format(new Date(offer.expiresAt))}</small>
          {accepting === offer.id ? (
            <>
              <p>수락하면 <strong>{snapshot.household.name}</strong>의 집 대표가 돼요. 기존 대표는 일반 가족으로 남아요.</p>
              <label className="checkbox-label">
                <input type="checkbox" checked={adultConfirmed} disabled={busy} onChange={(e) => setAdultConfirmed(e.target.checked)} />
                저는 성인이고 이 집의 대표 권한을 받겠습니다.
              </label>
              <div className="form-actions">
                <button className="button secondary" disabled={busy} onClick={() => setAccepting(null)}>돌아가기</button>
                <button className="button primary" disabled={busy || !adultConfirmed}
                  onClick={() => void update(`${root}/${offer.id}/accept`, { version: offer.version, adultConfirmed }, "집 대표가 변경됐어요.")}>
                  이전 수락 확인
                </button>
              </div>
            </>
          ) : (
            <div className="form-actions">
              {incoming && <button className="button primary" disabled={busy} onClick={() => setAccepting(offer.id)}>이전 제안 확인</button>}
              <button className="button secondary" disabled={busy}
                onClick={() => void update(`${root}/${offer.id}/cancel`, { version: offer.version }, incoming ? "이전 제안을 거절했어요." : "이전 제안을 취소했어요.")}>
                {incoming ? "제안 거절" : "제안 취소"}
              </button>
            </div>
          )}
        </div>
      ) : (
        <>
          {offer?.state === "EXPIRED" && <p>이전 제안의 수락 기한이 지났어요.</p>}
          {own?.role === "OWNER" && eligible.length > 0 ? (
            <form className="pet-form" onSubmit={propose}>
              <p>상대가 24시간 안에 직접 수락하면 집 대표가 바뀌어요.</p>
              <label>이전할 가족
                <select aria-label="이전할 가족" required value={target} disabled={busy} onChange={(e) => setTarget(e.target.value)}>
                  <option value="">가족 선택</option>
                  {eligible.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
                </select>
              </label>
              <button className="button secondary" disabled={busy || !target}>이전 제안 보내기</button>
            </form>
          ) : <p className="muted">{own?.role === "OWNER" ? "대표를 넘길 다른 가족이 아직 없어요." : "현재 받은 이전 제안이 없어요."}</p>}
        </>
      )}
      {error && <p role="alert" className="error-message">{error}</p>}
      {message && <p role="status">{message}</p>}
    </section>
  );
}
