import { useRef, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { StickyNote, Trash2 } from "lucide-react";
import { api, ApiError } from "../lib/api";

interface FormerNote {
  id: string;
  householdName: string;
  body: string;
  createdAt: string;
  version: number;
}
interface FormerNotePage {
  items: FormerNote[];
  nextCursor: string | null;
}

const path = "/api/me/former-records/notes";

export function FormerNotesPanel({ user }: { user: string }) {
  const query = useInfiniteQuery({
    queryKey: ["former-notes", user],
    queryFn: ({ pageParam }) => api<FormerNotePage>(path + (pageParam ? "?cursor=" + encodeURIComponent(pageParam) : "")),
    initialPageParam: null as string | null,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: false,
  });
  const [confirming, setConfirming] = useState<string>();
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");
  const pending = useRef(false);
  const notes = query.data?.pages.flatMap(page => page.items) ?? [];

  async function remove(note: FormerNote) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setStatus("");
    setError("");
    try {
      await api(path + "/" + encodeURIComponent(note.id) + "?version=" + note.version, "DELETE");
      setConfirming(undefined);
      setStatus("쪽지를 삭제했어요.");
      await query.refetch();
    } catch (failure) {
      setError((failure as Error).message);
      if (failure instanceof ApiError && (failure.status === 404 || failure.status === 409)) {
        setConfirming(undefined);
        await query.refetch();
      }
    } finally {
      setBusy(false);
      pending.current = false;
    }
  }

  return (
    <section className="former-notes" aria-labelledby="former-notes-heading">
      <h2 id="former-notes-heading"><StickyNote size={20} aria-hidden="true" /> 내가 붙인 쪽지</h2>
      <p>퇴실한 집에 남아 있는 내 쪽지를 확인하고 삭제할 수 있어요.</p>
      {query.isPending && <p role="status">쪽지를 불러오고 있어요…</p>}
      {query.isError && <div>
        <p role="alert" className="error-message">{query.error.message}</p>
        <button type="button" className="button secondary" onClick={() => void query.refetch()}>다시 불러오기</button>
      </div>}
      {!query.isPending && !query.isError && !notes.length && <p className="muted">이전 집에 남긴 쪽지가 없어요.</p>}
      {status && <p role="status">{status}</p>}
      {error && <p role="alert" className="error-message">{error}</p>}
      <ul>
        {notes.map(note => (
          <li key={note.id} className="former-note">
            <header>
              <strong>{note.householdName}</strong>
              <time dateTime={note.createdAt}>{new Date(note.createdAt).toLocaleDateString("ko-KR")}</time>
            </header>
            <p>{note.body}</p>
            {confirming === note.id ? (
              <div className="former-note-confirmation">
                <p>삭제하면 이 집의 쪽지에서도 사라져요.</p>
                <button type="button" className="button primary" disabled={busy} onClick={() => void remove(note)}>
                  {busy ? "삭제 중…" : "쪽지 삭제 확인"}
                </button>
                <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirming(undefined)}>취소</button>
              </div>
            ) : (
              <button type="button" className="button secondary" disabled={busy} onClick={() => {
                setConfirming(note.id);
                setStatus("");
                setError("");
              }}><Trash2 size={16} aria-hidden="true" /> 쪽지 삭제</button>
            )}
          </li>
        ))}
      </ul>
      {query.hasNextPage && <button type="button" className="button secondary" disabled={query.isFetchingNextPage || busy}
        onClick={() => void query.fetchNextPage()}>{query.isFetchingNextPage ? "불러오는 중…" : "이전 쪽지 더 보기"}</button>}
    </section>
  );
}
