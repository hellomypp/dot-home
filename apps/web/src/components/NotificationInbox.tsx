import { Bell, ChevronRight, Home, Paintbrush } from "lucide-react";
import { useRef, useState } from "react";
import { api, type Member } from "../lib/api";

const messages = {
  EDIT_REQUESTED: "집 편집을 요청했어요.",
  EDIT_APPROVED: "집 편집을 한 번 승인했어요.",
  EDIT_REJECTED: "집 편집 요청을 거절했어요.",
  EDIT_REVOKED: "집 편집 권한을 종료했어요.",
  FAMILY_TASK: "새로운 가족 부탁을 남겼어요.",
  FAMILY_NOTICE: "가족 공지를 남겼어요.",
  ARRIVED_HOME: "집에 도착했어요.",
} as const;
type NotificationKind = keyof typeof messages;
interface NotificationItem {
  id: string;
  kind: NotificationKind;
  actorMemberId: string;
  createdAt: string;
  readAt: string | null;
}
export interface NotificationPage {
  items: NotificationItem[];
  nextCursor: string | null;
  unreadCount: number;
}

const time = new Intl.DateTimeFormat("ko-KR", {
  month: "long", day: "numeric", hour: "numeric", minute: "2-digit",
});

export function NotificationInbox({ items, members, loading, failed, more, loadMore, reload, open, settings }: {
  items: NotificationItem[];
  members: Member[];
  loading: boolean;
  failed: boolean;
  more: boolean;
  loadMore: () => Promise<unknown>;
  reload: () => Promise<unknown>;
  open: (target: "design" | "requests" | "records") => void;
  settings: () => void;
}) {
  const opening = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function openItem(item: NotificationItem) {
    if (opening.current) return;
    opening.current = true;
    setBusy(true);
    setError("");
    try {
      if (!item.readAt) {
        await api(`/api/notifications/${item.id}/read`, "POST");
        void reload();
      }
      open(item.kind === "ARRIVED_HOME" ? "records" : item.kind.startsWith("EDIT_") ? "design" : "requests");
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      opening.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="notification-inbox" aria-busy={busy || loading}>
      <div className="notification-tools">
        <button type="button" className="button secondary" onClick={settings}>알림 설정</button>
      </div>
      {loading && <p role="status">알림을 불러오는 중이에요.</p>}
      {failed && (
        <div>
          <p role="alert">알림을 불러오지 못했어요.</p>
          <button className="button secondary" onClick={() => void reload()}>다시 불러오기</button>
        </div>
      )}
      {!loading && !failed && !items.length && <p>최근 30일 동안 받은 알림이 없어요.</p>}
      {items.length > 0 && (
        <ul className="notification-list">
          {items.map(item => {
            const name = members.find(member => member.id === item.actorMemberId)?.name ?? "가족";
            const Icon = item.kind === "ARRIVED_HOME" ? Home : item.kind.startsWith("EDIT_") ? Paintbrush : Bell;
            return (
              <li key={item.id}>
                <button className="notification-row" data-unread={!item.readAt || undefined}
                  disabled={busy} onClick={() => void openItem(item)}>
                  <span className="notification-icon"><Icon size={18} aria-hidden="true" /></span>
                  <span>
                    <strong>{name} 님이 {messages[item.kind]}</strong>
                    <time dateTime={item.createdAt}>{time.format(new Date(item.createdAt))}</time>
                    {!item.readAt && <small>읽지 않음</small>}
                  </span>
                  <ChevronRight size={17} aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
      {more && <button className="button secondary" disabled={loading || busy}
        onClick={() => void loadMore()}>이전 알림 더 보기</button>}
      {error && <p role="alert" className="error-message">{error}</p>}
    </section>
  );
}
