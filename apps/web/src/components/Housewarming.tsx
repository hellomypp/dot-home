import { useRef, useState, type FormEvent } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { api, type Identity } from "../lib/api";
import { SessionBar } from "./SessionBar";
import { PixelScene } from "../onboarding/PixelScene";
interface Entry {
  id: string;
  visitorName: string;
  message: string;
  createdAt: string;
  hidden: boolean;
}
interface Visit {
  householdName: string;
  theme: string;
  guestbook: Entry[];
}
export function VisitPage() {
  const { token } = useParams(),
    root = `/api/visits/${encodeURIComponent(token || "")}`;
  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Identity>("/api/me"),
    retry: false,
  });
  const [visit, setVisit] = useState<Visit | null>(null),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const lock = useRef(false),
    request = useRef({ body: "", id: "" });
  async function enter() {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    try {
      setVisit(await api<Visit>(root + "/accept", "POST"));
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
      lock.current = false;
    }
  }
  async function write(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    if (request.current.body !== message)
      request.current = { body: message, id: crypto.randomUUID() };
    try {
      await api(root + "/guestbook", "POST", {
        message,
        requestId: request.current.id,
      });
      setVisit(await api<Visit>(root));
      setMessage("");
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
      lock.current = false;
    }
  }
  return (
    <div className="form-page visit-page">
      <header className="visit-header">
        <Link className="brand" to="/">
          도트홈
        </Link>
        {me.data && <SessionBar name={me.data.user.name} />}
      </header>
      <main className="onboard-card">
        <h1>
          {visit ? visit.householdName + " 집들이" : "작은 집에 초대받았어요"}
        </h1>
        <p>집들이는 가족 입주와 달라요. 방문해도 가족으로 가입되지 않아요.</p>
        {me.isPending ? (
          <p role="status">로그인을 확인하고 있어요…</p>
        ) : !me.data ? (
          <Link
            className="button primary"
            to={"/login?next=" + encodeURIComponent("/visit/" + token)}
          >
            Google 로그인하고 방문하기
          </Link>
        ) : !visit ? (
          <button
            className="button primary"
            disabled={busy}
            onClick={() => void enter()}
          >
            방문하기
          </button>
        ) : (
          <>
            <PixelScene kind="room" showAvatar={false} />
            <p className="muted">
              공개된 집 내부 풍경이에요. 가족의 비공개 활동은 표시하지 않아요.
            </p>
            <h2>방명록</h2>
            {visit.guestbook.length ? (
              visit.guestbook.map((e) => (
                <article className="calendar-entry" key={e.id}>
                  <strong>{e.visitorName}</strong>
                  <p>{e.message}</p>
                </article>
              ))
            ) : (
              <p>첫 방문 인사를 남겨보세요.</p>
            )}
            <form onSubmit={write}>
              <label>
                방문 인사
                <textarea
                  required
                  maxLength={1000}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                />
              </label>
              <button
                className="button primary"
                disabled={busy || !message.trim()}
              >
                방명록 남기기
              </button>
            </form>
          </>
        )}
        {error && (
          <p role="alert" className="error-message">
            {error}
          </p>
        )}
      </main>
    </div>
  );
}
export function HousewarmingPanel({ householdId }: { householdId: string }) {
  const root = `/api/households/${householdId}`,
    [url, setUrl] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const links = useQuery({
    queryKey: ["housewarming", householdId],
    queryFn: () =>
      api<{ id: string; expiresAt: string; revokedAt: string | null }[]>(
        root + "/housewarming-invitations",
      ),
  });
  const visitors = useQuery({
    queryKey: ["visitors", householdId],
    queryFn: () =>
      api<{ name: string; visitedAt: string }[]>(root + "/visitors"),
    refetchInterval: 30000,
  });
  const book = useQuery({
    queryKey: ["guestbook", householdId],
    queryFn: () => api<Entry[]>(root + "/guestbook"),
    refetchInterval: 30000,
  });
  async function action(path: string, method: string) {
    if (busy) return;
    setBusy(true);
    try {
      const result = await api<{ url: string } | undefined>(
        root + path,
        method,
      );
      if (result?.url) setUrl(new URL(result.url, location.origin).href);
      await Promise.all([links.refetch(), book.refetch()]);
      setError("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="housewarming-panel">
      {(links.isPending || book.isPending || visitors.isPending) && (
        <p role="status">집들이 정보를 가져오고 있어요…</p>
      )}
      <p>
        가족 초대와 별개인 7일 집들이 링크예요. 가족 개인정보는 손님에게
        공개되지 않아요.
      </p>
      <button
        disabled={busy}
        className="button primary"
        onClick={() => void action("/housewarming-invitations", "POST")}
      >
        집들이 링크 만들기
      </button>
      {url && (
        <label>
          새 집들이 링크
          <input readOnly value={url} onFocus={(e) => e.target.select()} />
        </label>
      )}
      {links.data?.map((i) => (
        <article className="calendar-entry" key={i.id}>
          <p>
            {new Date(i.expiresAt).toLocaleString("ko-KR")}까지{" "}
            {i.revokedAt ? "· 취소됨" : ""}
          </p>
          {!i.revokedAt && (
            <button
              disabled={busy}
              className="button secondary"
              onClick={() =>
                void action("/housewarming-invitations/" + i.id, "DELETE")
              }
            >
              방문 링크 취소
            </button>
          )}
        </article>
      ))}
      <h3>다녀간 친구들</h3>
      {visitors.data?.map((v, i) => (
        <p key={i}>
          {v.name} · {new Date(v.visitedAt).toLocaleString("ko-KR")}
        </p>
      ))}
      <h3>방문 방명록</h3>
      {book.data?.length ? (
        book.data.map((e) => (
          <article className="calendar-entry" key={e.id}>
            <strong>{e.visitorName}</strong>
            <p>{e.message}</p>
            {e.hidden ? (
              <span>숨긴 글</span>
            ) : (
              <button
                disabled={busy}
                className="button secondary"
                onClick={() => void action("/guestbook/" + e.id, "DELETE")}
              >
                방명록 숨기기
              </button>
            )}
          </article>
        ))
      ) : (
        <p className="empty-text">아직 방문 인사가 없어요.</p>
      )}
      {(error || links.error || book.error || visitors.error) && (
        <p role="alert">
          {error ||
            links.error?.message ||
            book.error?.message ||
            visitors.error?.message}
        </p>
      )}
    </section>
  );
}
