import { useRef, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { Bell, CalendarDays, StickyNote, Trash2 } from "lucide-react";
import { api, ApiError } from "../lib/api";

interface FormerRecord {
  id: string;
  householdName: string;
  title?: string | null;
  body: string;
  createdAt: string;
  version: number;
}
interface FormerRecordPage {
  items: FormerRecord[];
  nextCursor: string | null;
}

const groups = {
  notes: {
    label: "쪽지", empty: "쪽지가", removed: "쪽지를", heading: "내가 붙인 쪽지", icon: StickyNote,
    intro: "퇴실한 집에 남아 있는 내 쪽지를 확인하고 삭제할 수 있어요.",
  },
  calendar: {
    label: "일정", empty: "일정이", removed: "일정을", heading: "내가 만든 일정", icon: CalendarDays,
    intro: "퇴실한 집에서 내가 쓴 제목과 내용이 남아 있는 일정을 확인할 수 있어요.",
  },
  requests: {
    label: "부탁·공지", empty: "부탁·공지가", removed: "부탁·공지를", heading: "내가 남긴 부탁·공지", icon: Bell,
    intro: "퇴실한 집에서 내가 쓴 제목과 내용이 남아 있는 부탁, 공지와 약속을 확인할 수 있어요.",
  },
} as const;
type Kind = keyof typeof groups;

export function FormerRecordsPanel({ user }: { user: string }) {
  const [kind, setKind] = useState<Kind>("notes");
  return (
    <>
      <nav className="fridge-tabs former-records-tabs" aria-label="이전 기록 분류">
        {(Object.keys(groups) as Kind[]).map(value => <button type="button" className="button secondary"
          key={value} aria-pressed={kind === value} onClick={() => setKind(value)}>{groups[value].label}</button>)}
      </nav>
      <FormerRecords key={kind} kind={kind} user={user} />
    </>
  );
}

function FormerRecords({ user, kind }: { user: string; kind: Kind }) {
  const group = groups[kind];
  const Icon = group.icon;
  const path = "/api/me/former-records/" + kind;
  const query = useInfiniteQuery({
    queryKey: ["former-records", user, kind],
    queryFn: ({ pageParam }) => api<FormerRecordPage>(path + (pageParam ? "?cursor=" + encodeURIComponent(pageParam) : "")),
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

  async function remove(note: FormerRecord) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setStatus("");
    setError("");
    try {
      await api(path + "/" + encodeURIComponent(note.id) + "?version=" + note.version, "DELETE");
      setConfirming(undefined);
      setStatus(`${group.removed} 삭제했어요.`);
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
    <section className="former-notes" aria-labelledby="former-record-kind">
      <h2 id="former-record-kind"><Icon size={20} aria-hidden="true" /> {group.heading}</h2>
      <p>{group.intro}</p>
      {query.isPending && <p role="status">기록을 불러오고 있어요…</p>}
      {query.isError && <div>
        <p role="alert" className="error-message">{query.error.message}</p>
        <button type="button" className="button secondary" onClick={() => void query.refetch()}>다시 불러오기</button>
      </div>}
      {!query.isPending && !query.isError && !notes.length && <p className="muted">이전 집에 남긴 {group.empty} 없어요.</p>}
      {status && <p role="status">{status}</p>}
      {error && <p role="alert" className="error-message">{error}</p>}
      <ul>
        {notes.map(note => (
          <li key={note.id} className="former-note">
            <header>
              <strong>{note.householdName}</strong>
              <time dateTime={note.createdAt}>{new Date(note.createdAt).toLocaleDateString("ko-KR")}</time>
            </header>
            {note.title && <h3>{note.title}</h3>}
            {note.body && <p>{note.body}</p>}
            {confirming === note.id ? (
              <div className="former-note-confirmation">
                <p>삭제하면 해당 집에서도 이 기록이 사라져요.</p>
                <button type="button" className="button primary" disabled={busy} onClick={() => void remove(note)}>
                  {busy ? "삭제 중…" : group.label + " 삭제 확인"}
                </button>
                <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirming(undefined)}>취소</button>
              </div>
            ) : (
              <button type="button" className="button secondary" disabled={busy} onClick={() => {
                setConfirming(note.id);
                setStatus("");
                setError("");
              }}><Trash2 size={16} aria-hidden="true" /> {group.label} 삭제</button>
            )}
          </li>
        ))}
      </ul>
      {query.hasNextPage && <button type="button" className="button secondary" disabled={query.isFetchingNextPage || busy}
        onClick={() => void query.fetchNextPage()}>{query.isFetchingNextPage ? "불러오는 중…" : group.label + " 더 보기"}</button>}
    </section>
  );
}
