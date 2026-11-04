import { useRef, useState, type FormEvent } from "react";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import { CareReactions } from "./CareReactions";
import { api } from "../lib/api";
export interface ObjectNote {
  id: string;
  objectId: string;
  authorMemberId: string;
  body: string;
  state: "ATTACHED" | "TAKEN";
  createdAt: string;
  takenByMemberId: string | null;
  takenAt: string | null;
  version: number;
}
export function ObjectNotes({
  householdId,
  memberId,
  isOwner,
  members = [],
  readOnly = false,
}: {
  householdId: string;
  memberId: string;
  isOwner: boolean;
  members?: { id: string; name: string }[];
  readOnly?: boolean;
}) {
  const [archive, setArchive] = useState(false),
    [body, setBody] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    lock = useRef(false),
    client = useQueryClient();
  const root = `/api/households/${householdId}`;
  const attachedNotes = useQuery({
    queryKey: ["object-notes", householdId, false],
    queryFn: () => api<ObjectNote[]>(root + "/objects/fridge/notes"),
    enabled: !archive,
  });
  const archivedNotes = useInfiniteQuery({
    queryKey: ["object-notes", householdId, "archive"],
    initialPageParam: "",
    queryFn: ({ pageParam }) => api<{ items: ObjectNote[]; nextCursor: string | null }>(
      root + "/notes/archive/page" + (pageParam ? "?cursor=" + encodeURIComponent(pageParam) : ""),
    ),
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    enabled: archive,
  });
  const query = archive ? archivedNotes : attachedNotes;
  const notes = archive ? archivedNotes.data?.pages.flatMap((page) => page.items) : attachedNotes.data;
  const noteBody = body.trim();
  const length = [...noteBody].length;
  const full = !archive && (attachedNotes.data?.length ?? 0) >= 10;
  const canCreate = !full && length > 0 && length <= 300;
  async function mutate(path: string, method: string, input?: unknown) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      await api(root + path, method, input);
      if (method === "POST" && path.includes("/objects/")) setBody("");
      await client.invalidateQueries({
        queryKey: ["object-notes", householdId],
      });
    } catch (e) {
      setError((e as Error).message);
      void query.refetch();
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  function create(e: FormEvent) {
    e.preventDefault();
    if (canCreate)
      void mutate("/objects/fridge/notes", "POST", { body: noteBody });
  }
  return (
    <section className="object-notes">
      <nav aria-label="쪽지 분류">
        <button
          className="button secondary"
          aria-pressed={!archive}
          onClick={() => setArchive(false)}
        >
          냉장고 쪽지
        </button>
        <button
          className="button secondary"
          aria-pressed={archive}
          onClick={() => setArchive(true)}
        >
          가족 보관함
        </button>
      </nav>
      {query.isPending ? (
        <p role="status">쪽지를 가져오고 있어요…</p>
      ) : query.error && !notes?.length ? (
        <p role="alert">
          {query.error.message}
          <button
            className="button secondary"
            onClick={() => void query.refetch()}
          >
            다시 시도
          </button>
        </p>
      ) : notes?.length ? (
        notes.map((n) => (
          <article className="object-note" key={n.id}>
            <p>{n.body}</p>
            <small>
              {members.find((m) => m.id === n.authorMemberId)?.name || "퇴실한 구성원"} ·{" "}
              {new Date(n.createdAt).toLocaleString("ko-KR")}
              {n.takenAt
                ? ` · ${members.find((m) => m.id === n.takenByMemberId)?.name || "퇴실한 구성원"}님이 ${new Date(n.takenAt).toLocaleString("ko-KR")}에 보관`
                : ""}
            </small>
            <div>
              {!archive && !readOnly && (
                <button
                  className="button primary"
                  disabled={busy}
                  onClick={() =>
                    void mutate(`/notes/${n.id}/take`, "POST", {
                      version: n.version,
                    })
                  }
                >
                  떼어 읽기
                </button>
              )}
              {!readOnly && (isOwner || n.authorMemberId === memberId) && (
                <button
                  className="button secondary"
                  disabled={busy}
                  onClick={() => {
                    if (window.confirm("이 쪽지를 삭제할까요?"))
                      void mutate(
                        `/notes/${n.id}?version=${n.version}`,
                        "DELETE",
                      );
                  }}
                >
                  삭제
                </button>
              )}
            </div>
            {!readOnly && <CareReactions householdId={householdId} sourceType="NOTE" sourceId={n.id} />}
          </article>
        ))
      ) : (
        <p className="empty-text">
          {archive
            ? "아직 보관한 쪽지가 없어요."
            : readOnly ? "냉장고에 붙은 쪽지가 없어요." : "냉장고에 첫 쪽지를 붙여보세요."}
        </p>
      )}
      {archive && (archivedNotes.hasNextPage || archivedNotes.isFetchNextPageError) && (
        <button
          className="button secondary"
          type="button"
          disabled={archivedNotes.isFetching}
          onClick={() => void archivedNotes.fetchNextPage()}
        >
          {archivedNotes.isFetchingNextPage ? "불러오는 중…" : archivedNotes.isFetchNextPageError ? "다음 쪽지 다시 불러오기" : "보관한 쪽지 더 보기"}
        </button>
      )}
      {!archive && !readOnly && (
        <form onSubmit={create}>
          <label>
            가족에게 남길 쪽지
            <textarea
              required
              aria-describedby="note-body-limit"
              aria-invalid={length > 300}
              value={body}
              onChange={(e) => setBody(e.target.value)}
            />
          </label>
          <p id="note-body-limit" className={length > 300 ? "error-message" : "muted"}>
            {length}/300자{length > 300 && " · 300자 이내로 줄여 주세요."}
          </p>
          {full && <p role="status">냉장고에 쪽지가 10장 붙어 있어요. 한 장을 떼거나 삭제해 주세요.</p>}
          <button className="button primary" disabled={busy || !canCreate}>
            쪽지 붙이기
          </button>
        </form>
      )}
      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
