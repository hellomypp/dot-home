import { useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
}: {
  householdId: string;
  memberId: string;
  isOwner: boolean;
  members?: { id: string; name: string }[];
}) {
  const [archive, setArchive] = useState(false),
    [body, setBody] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    lock = useRef(false),
    client = useQueryClient();
  const root = `/api/households/${householdId}`;
  const query = useQuery({
    queryKey: ["object-notes", householdId, archive],
    queryFn: () =>
      api<ObjectNote[]>(
        root + (archive ? "/notes/archive" : "/objects/fridge/notes"),
      ),
  });
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
    if (body.trim())
      void mutate("/objects/fridge/notes", "POST", { body: body.trim() });
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
      ) : query.error ? (
        <p role="alert">
          {query.error.message}
          <button
            className="button secondary"
            onClick={() => void query.refetch()}
          >
            다시 시도
          </button>
        </p>
      ) : query.data?.length ? (
        query.data.map((n) => (
          <article className="object-note" key={n.id}>
            <p>{n.body}</p>
            <small>
              {members.find((m) => m.id === n.authorMemberId)?.name || "가족"} ·{" "}
              {new Date(n.createdAt).toLocaleString("ko-KR")}
              {n.takenAt
                ? ` · ${members.find((m) => m.id === n.takenByMemberId)?.name || "가족"}님이 ${new Date(n.takenAt).toLocaleString("ko-KR")}에 보관`
                : ""}
            </small>
            <div>
              {!archive && (
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
              {(isOwner || n.authorMemberId === memberId) && (
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
          </article>
        ))
      ) : (
        <p className="empty-text">
          {archive
            ? "아직 보관한 쪽지가 없어요."
            : "냉장고에 첫 쪽지를 붙여보세요."}
        </p>
      )}
      {!archive && (
        <form onSubmit={create}>
          <label>
            가족에게 남길 쪽지
            <textarea
              required
              maxLength={1000}
              value={body}
              onChange={(e) => setBody(e.target.value)}
            />
          </label>
          <button className="button primary" disabled={busy || !body.trim()}>
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
