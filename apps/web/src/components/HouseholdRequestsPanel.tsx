import { useRef, useState, type FormEvent } from "react";
import { useInfiniteQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, type Snapshot } from "../lib/api";

type Kind = "NOTICE" | "TASK" | "PROMISE";
type State = "ACTIVE" | "OPEN" | "CLAIMED" | "DONE" | "CANCELLED" | "EXPIRED";
type Action = "claim" | "release" | "complete" | "cancel" | "reopen";
interface HouseholdRequest {
  id: string;
  kind: Kind;
  title: string;
  body: string;
  authorMemberId: string;
  targetMemberIds: string[];
  assigneeMemberId: string | null;
  state: State;
  dueAt: string;
  completedByMemberId: string | null;
  version: number;
}
interface RequestPage { items: HouseholdRequest[]; nextCursor: string | null }
const kinds: Record<Kind, string> = { NOTICE: "공지", TASK: "부탁", PROMISE: "내 약속" };
const stateNames: Record<State, string> = {
  ACTIVE: "게시 중", OPEN: "담당을 기다리는 중", CLAIMED: "맡았어요",
  DONE: "완료", CANCELLED: "취소됨", EXPIRED: "기한 지남",
};
const actionNames: Record<Action, string> = {
  claim: "제가 할게요", release: "맡기 취소", complete: "완료하기", cancel: "요청 취소", reopen: "완료 취소",
};
const filters = {
  all: { label: "전체", states: "" },
  open: { label: "진행 중", states: "ACTIVE,OPEN,CLAIMED" },
  closed: { label: "지난 요청", states: "DONE,CANCELLED,EXPIRED" },
};
const dateTime = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul", dateStyle: "medium", timeStyle: "short",
});

export function HouseholdRequestsPanel({ snapshot }: { snapshot: Snapshot }) {
  const [filter, setFilter] = useState<keyof typeof filters>("all");
  const [compose, setCompose] = useState(false);
  const client = useQueryClient();
  const root = `/api/households/${snapshot.household.id}/requests`;
  const key = ["household-requests", snapshot.household.id];
  const query = useInfiniteQuery({
    queryKey: [...key, filter],
    initialPageParam: "",
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams();
      if (filters[filter].states) params.set("state", filters[filter].states);
      if (pageParam) params.set("cursor", pageParam);
      return api<RequestPage>(root + "?" + params);
    },
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    retry: false,
    refetchInterval: 30_000,
  });
  const rows = query.data?.pages.flatMap((page) => page.items) ?? [];
  const owner = snapshot.members.find((member) => member.id === snapshot.meMemberId)?.role === "OWNER";
  const refresh = () => client.invalidateQueries({ queryKey: key });

  return (
    <section className="requests-panel">
      <p className="form-intro"><strong>{snapshot.household.name}</strong> 가족과 부탁, 공지, 약속을 나눠요.</p>
      <div className="request-toolbar">
        <nav aria-label="생활 요청 필터">
          {(Object.entries(filters) as [keyof typeof filters, {label: string}][]).map(([id, item]) => (
            <button key={id} className="button secondary" aria-pressed={id === filter} onClick={() => setFilter(id)}>
              {item.label}
            </button>
          ))}
        </nav>
        <button className="button primary" aria-expanded={compose} onClick={() => setCompose(!compose)}>
          {compose ? "작성 접기" : "새 요청"}
        </button>
      </div>
      {compose && <CreateForm root={root} snapshot={snapshot} onSaved={() => {
        setCompose(false);
        setFilter("all");
        void refresh();
      }} />}
      {query.isPending && <p role="status">생활 요청을 가져오고 있어요…</p>}
      {query.isError && !rows.length && (
        <div>
          <p role="alert">{query.error.message}</p>
          <button className="button secondary" onClick={() => void query.refetch()}>다시 시도</button>
        </div>
      )}
      {query.isSuccess && !rows.length && <p className="empty-text">아직 이 목록에 생활 요청이 없어요.</p>}
      {rows.map((row) => (
        <RequestCard key={row.id} row={row} root={root} snapshot={snapshot} owner={owner} onChanged={refresh} />
      ))}
      {query.hasNextPage && (
        <button className="button secondary" disabled={query.isFetching} onClick={() => void query.fetchNextPage()}>
          {query.isFetchingNextPage ? "불러오는 중…" : query.isFetchNextPageError ? "다음 요청 다시 불러오기" : "요청 더 보기"}
        </button>
      )}
    </section>
  );
}

function CreateForm({ root, snapshot, onSaved }: {
  root: string; snapshot: Snapshot; onSaved: () => void;
}) {
  const [kind, setKind] = useState<Kind>("TASK");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [due, setDue] = useState("");
  const [targets, setTargets] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const receipt = useRef<{ fingerprint: string; id: string } | null>(null);
  const valid = title.trim().length > 0 && [...title.trim()].length <= 80 && [...body.trim()].length <= 300;
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid || pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      const payload = {
        kind, title: title.trim(), body: body.trim(),
        targetMemberIds: kind === "TASK" ? targets : [],
        ...(due ? { dueAt: new Date(due + ":00+09:00").toISOString() } : {}),
      };
      const fingerprint = JSON.stringify(payload);
      if (receipt.current?.fingerprint !== fingerprint) receipt.current = { fingerprint, id: crypto.randomUUID() };
      await api(root, "POST", { ...payload, requestId: receipt.current.id });
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return (
    <form className="request-compose" onSubmit={submit}>
      <label>종류
        <select value={kind} disabled={busy} onChange={(e) => setKind(e.target.value as Kind)}>
          {Object.entries(kinds).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label>제목<input required value={title} disabled={busy} onChange={(e) => setTitle(e.target.value)} /></label>
      <label>내용<textarea value={body} disabled={busy} onChange={(e) => setBody(e.target.value)} /></label>
      <p className="muted">제목 {[...title.trim()].length}/80자 · 내용 {[...body.trim()].length}/300자</p>
      {kind === "TASK" && (
        <fieldset>
          <legend>부탁할 가족 · 선택</legend>
          <p className="muted">선택하지 않으면 누구나 맡을 수 있어요.</p>
          {snapshot.members.map((member) => (
            <label key={member.id} className="checkbox-label">
              <input type="checkbox" checked={targets.includes(member.id)} disabled={busy} onChange={(e) => {
                const selected = e.target.checked;
                setTargets((current) => selected ? [...current, member.id] : current.filter((id) => id !== member.id));
              }} />
              {member.name}
            </label>
          ))}
        </fieldset>
      )}
      <label>기한 · 서울 시간
        <input type="datetime-local" value={due} disabled={busy} onChange={(e) => setDue(e.target.value)} />
      </label>
      {!due && <p className="muted">{kind === "NOTICE" ? "기본 기한은 다음 자정이에요. 밤 11시 이후에는 12시간 뒤로 설정돼요." : "기한을 정하지 않으면 7일 뒤 만료돼요."}</p>}
      {error && <p role="alert" className="error-message">{error}</p>}
      <button type="submit" className="button primary" disabled={busy || !valid}>
        {busy ? "저장 중…" : "가족에게 공유"}
      </button>
    </form>
  );
}

function availableActions(row: HouseholdRequest, member: string, owner: boolean): Action[] {
  const actions: Action[] = [];
  const targeted = !row.targetMemberIds.length || row.targetMemberIds.includes(member);
  if (row.state === "OPEN" && targeted) actions.push("claim", "complete");
  if (row.state === "CLAIMED" && row.assigneeMemberId === member) actions.push("complete", "release");
  if ((owner || row.authorMemberId === member) && ["ACTIVE", "OPEN", "CLAIMED"].includes(row.state)) actions.push("cancel");
  if (row.state === "DONE" && (owner || row.completedByMemberId === member)) actions.push("reopen");
  return actions;
}

function RequestCard({ row, root, snapshot, owner, onChanged }: {
  row: HouseholdRequest;
  root: string;
  snapshot: Snapshot;
  owner: boolean;
  onChanged: () => Promise<void>;
}) {
  const [confirm, setConfirm] = useState<Action>();
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const receipt = useRef<{ fingerprint: string; id: string } | null>(null);
  const name = (id: string) => snapshot.members.find((m) => m.id === id)?.name ?? "퇴실한 구성원";
  const actions = availableActions(row, snapshot.meMemberId, owner);
  async function run(action: Action, note?: string) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    const payload = { version: row.version, ...(note ? { reason: note } : {}) };
    const fingerprint = action + JSON.stringify(payload);
    if (receipt.current?.fingerprint !== fingerprint) receipt.current = { fingerprint, id: crypto.randomUUID() };
    try {
      await api(root + "/" + row.id + "/" + action, "POST", { ...payload, requestId: receipt.current.id });
      setConfirm(undefined);
      setReason("");
      await onChanged();
    } catch (e) {
      setError((e as Error).message);
      if (e instanceof ApiError && [409, 410].includes(e.status)) void onChanged();
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return (
    <article className="request-card">
      <header><span>{kinds[row.kind]}</span><span>{stateNames[row.state]}</span></header>
      <h3>{row.title}</h3>
      {row.body && <p>{row.body}</p>}
      <dl>
        <div><dt>작성자</dt><dd>{name(row.authorMemberId)}</dd></div>
        <div><dt>기한</dt><dd>{dateTime.format(new Date(row.dueAt))}</dd></div>
        {row.targetMemberIds.length > 0 && <div><dt>대상</dt><dd>{row.targetMemberIds.map(name).join(", ")}</dd></div>}
        {row.assigneeMemberId && <div><dt>담당</dt><dd>{name(row.assigneeMemberId)}</dd></div>}
      </dl>
      {actions.length > 0 && (
        <div className="request-actions">
          {actions.map((action) => (
            <button type="button" key={action} className="button secondary" disabled={busy} onClick={() => {
              if (action === "cancel" || action === "reopen") {
                setReason("");
                setConfirm(action);
              } else void run(action);
            }}>{actionNames[action]}</button>
          ))}
        </div>
      )}
      {confirm && actions.includes(confirm) && (
        <form onSubmit={(e) => { e.preventDefault(); void run(confirm, reason.trim()); }}>
          <label>{actionNames[confirm]} 사유<input required value={reason} onChange={(e) => setReason(e.target.value)} /></label>
          <div className="request-actions">
            <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirm(undefined)}>돌아가기</button>
            <button type="submit" className="button primary" disabled={busy || !reason.trim() || [...reason.trim()].length > 200}>
              {actionNames[confirm]} 확인
            </button>
          </div>
        </form>
      )}
      {error && <p role="alert" className="error-message">{error}</p>}
    </article>
  );
}
