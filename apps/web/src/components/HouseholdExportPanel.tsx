import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";

type ExportJob = {
  id: string;
  state: "PENDING" | "READY" | "CANCELLED" | "TOO_LARGE" | "FAILED";
  expiresAt: string;
  downloadToken?: string | null;
};

export function HouseholdExportPanel({ householdId }: { householdId: string }) {
  const root = `/api/households/${householdId}/exports`;
  const client = useQueryClient();
  const key = ["household-exports", householdId];
  const latest = useQuery({
    queryKey: key,
    queryFn: () => api<ExportJob[]>(root),
    refetchInterval: (query) => query.state.data?.[0]?.state === "PENDING" ? 1500 : false,
  });
  const job = latest.data?.[0];
  const requestId = useRef<string>(undefined);
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(download: boolean) {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      if (download && job) {
        const status = await api<ExportJob>(`${root}/${job.id}`);
        if (!status.downloadToken) throw new Error("아직 내려받을 수 있는 파일이 없어요.");
        const document = await api<unknown>(`${root}/${job.id}/download`, "POST", { token: status.downloadToken });
        const url = URL.createObjectURL(new Blob([JSON.stringify(document, null, 2)], { type: "application/json" }));
        const link = window.document.createElement("a");
        link.href = url;
        link.download = "dot-home-records.json";
        window.document.body.append(link);
        link.click();
        link.remove();
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      } else {
        requestId.current ??= crypto.randomUUID();
        const created = await api<ExportJob>(root, "POST", { requestId: requestId.current });
        client.setQueryData(key, [created]);
        requestId.current = undefined;
      }
    } catch (error) {
      setError((error as Error).message);
      void latest.refetch();
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="pet-manager" aria-label="공동 기록 내보내기">
      <p>이 집의 공동 일정, 사물 쪽지, 부탁·공지와 생활 기록을 JSON 파일로 내려받을 수 있어요.</p>
      <p className="muted">개인 공유 일정과 현재 공유 상태는 제외돼요. 만든 파일은 한 시간 동안 내려받을 수 있어요.</p>
      {latest.isPending ? <p role="status">파일 상태를 확인하고 있어요…</p> : latest.isError ? (
        <div><p role="alert">{latest.error.message}</p><button className="button secondary" onClick={() => void latest.refetch()}>다시 확인</button></div>
      ) : job?.state === "PENDING" ? (
        <p role="status">공동 기록을 모으고 있어요. 이 창을 닫았다가 다시 열어도 돼요.</p>
      ) : job?.state === "READY" ? (
        <button className="button primary" disabled={busy} onClick={() => void submit(true)}>JSON 파일 내려받기</button>
      ) : (
        <>
          {job?.state === "TOO_LARGE" && <p role="status">기록이 한 파일에 담을 수 있는 크기를 넘었어요.</p>}
          {job?.state === "FAILED" && <p role="alert">파일을 만들지 못했어요. 잠시 후 다시 시도해 주세요.</p>}
          <button className="button primary" disabled={busy} onClick={() => void submit(false)}>내보내기 요청</button>
        </>
      )}
      {error && <p role="alert" className="error-message">{error}</p>}
    </section>
  );
}
