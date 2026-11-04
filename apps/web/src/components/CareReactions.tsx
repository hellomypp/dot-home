import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Heart } from "lucide-react";
import { api, ApiError } from "../lib/api";

type Source = "NOTE" | "REQUEST" | "EXPRESSION";
type Code = "THANKS" | "CHEER" | "ACK" | "SHARE_MEAL";
type Summary = { counts: Partial<Record<Code, number>>; mine: { id: string; code: Code }[] };
const labels: Record<Code, string> = {
  THANKS: "고마워요", CHEER: "응원해요", ACK: "알겠어요", SHARE_MEAL: "같이 먹어요",
};

export function CareReactions({ householdId, sourceType, sourceId, meal = false }: {
  householdId: string; sourceType: Source; sourceId: string; meal?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const receipt = useRef<{ code: Code; id: string } | undefined>(undefined);
  const client = useQueryClient();
  const key = ["household-reactions", householdId, sourceType, sourceId];
  const root = `/api/households/${householdId}/reactions`;
  const summary = useQuery({
    queryKey: key,
    queryFn: () => api<Summary>(root + "?" + new URLSearchParams({ sourceType, sourceId })),
    enabled: open,
    retry: false,
  });

  async function toggle(code: Code) {
    if (!summary.data || pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    const existing = summary.data.mine.find((reaction) => reaction.code === code);
    try {
      if (existing) {
        await api(root + "/" + existing.id, "DELETE");
      } else {
        if (receipt.current?.code !== code) receipt.current = { code, id: crypto.randomUUID() };
        await api(root, "POST", { requestId: receipt.current.id, sourceType, sourceId, code });
      }
      receipt.current = undefined;
      await client.invalidateQueries({ queryKey: key });
    } catch (err) {
      setError((err as Error).message);
      if (err instanceof ApiError && [403, 410].includes(err.status)) void summary.refetch();
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return (
    <div className="care-reactions">
      <button className="button secondary care-trigger" type="button" aria-expanded={open} onClick={() => setOpen(!open)}>
        <Heart size={15} aria-hidden="true" />
        마음 전하기
      </button>
      {open && (
        <>
          {summary.isPending && <p role="status">반응을 불러오고 있어요…</p>}
          {summary.error && <p role="alert">{summary.error.message}</p>}
          {summary.data && !summary.error && (
            <div className="care-options" role="group" aria-label="마음 전하기">
              {(Object.keys(labels) as Code[]).filter((code) => code !== "SHARE_MEAL" || meal).map((code) => {
                const mine = summary.data.mine.some((reaction) => reaction.code === code);
                const count = summary.data.counts[code];
                return (
                  <button
                    className="button secondary"
                    type="button"
                    key={code}
                    aria-pressed={mine}
                    disabled={busy}
                    onClick={() => void toggle(code)}
                  >
                    {labels[code]}{count ? ` · ${count}` : ""}
                  </button>
                );
              })}
            </div>
          )}
          {error && <p role="alert" className="error-message">{error}</p>}
        </>
      )}
    </div>
  );
}
