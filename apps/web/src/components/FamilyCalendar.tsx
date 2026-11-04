import { useEffect, useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../lib/api";
export interface CalendarEvent {
  id: string;
  title: string;
  description: string;
  category: "GENERAL" | "BIRTHDAY" | "ANNIVERSARY" | "CARE";
  allDay: boolean;
  startDate: string | null;
  endDate: string | null;
  startAt: string | null;
  endAt: string | null;
  timeZone: string;
  version: number;
  scope?: "HOUSEHOLD" | "PERSONAL_SHARED";
  hiddenAt?: string | null;
  canEdit?: boolean;
  canHide?: boolean;
}
export function seoulDate(value = new Date()) {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(value);
}
export function seoulTime(value: string) {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  })
    .format(new Date(value))
    .replace(" ", "T");
}
export function occurs(event: CalendarEvent, date: string) {
  return event.allDay
    ? event.startDate! <= date && event.endDate! >= date
    : seoulDate(new Date(event.startAt!)) <= date &&
        seoulDate(
          new Date(
            Math.max(Date.parse(event.startAt!), Date.parse(event.endAt!) - 1),
          ),
        ) >= date;
}
export function FamilyCalendar({ householdId, isOwner = false }: { householdId: string; isOwner?: boolean }) {
  const titleField = useRef<HTMLInputElement>(null);
  const today = seoulDate(),
    [month, setMonth] = useState(today.slice(0, 7)),
    [day, setDay] = useState(today),
    [edit, setEdit] = useState<CalendarEvent | null>(null),
    [form, setForm] = useState(false),
    [showHidden, setShowHidden] = useState(false),
    [scope, setScope] = useState<NonNullable<CalendarEvent["scope"]>>("HOUSEHOLD"),
    [title, setTitle] = useState(""),
    [description, setDescription] = useState(""),
    [category, setCategory] = useState("GENERAL"),
    [allDay, setAllDay] = useState(true),
    [start, setStart] = useState(today),
    [end, setEnd] = useState(today),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    lock = useRef(false),
    client = useQueryClient();
  const root = `/api/households/${householdId}/calendar-events`,
    [year, mon] = month.split("-").map(Number),
    days = new Date(Date.UTC(year, mon, 0)).getUTCDate(),
    offset = new Date(Date.UTC(year, mon - 1, 1)).getUTCDay();
  const query = useQuery({
    queryKey: ["calendar", householdId, month],
    queryFn: () =>
      api<CalendarEvent[]>(`${root}?from=${month}-01&to=${month}-${days}`),
  });
  const hidden = useQuery({
    queryKey: ["calendar", householdId, "moderation", month],
    queryFn: () => api<{ id: string; hiddenAt: string; version: number }[]>(
      `${root}/moderation?from=${month}-01&to=${month}-${days}`,
    ),
    enabled: isOwner && showHidden,
    retry: false,
  });
  useEffect(() => {
    if (form) {
      titleField.current?.focus({ preventScroll: true });
      titleField.current?.scrollIntoView?.({ block: "nearest" });
    }
  }, [form]);
  function shift(delta: number) {
    const d = new Date(Date.UTC(year, mon - 1 + delta, 1));
    const next = d.toISOString().slice(0, 7);
    setMonth(next);
    setDay(next + "-01");
  }
  function open(e: CalendarEvent | null) {
    setEdit(e);
    setTitle(e?.title || "");
    setDescription(e?.description || "");
    setCategory(e?.category || "GENERAL");
    setScope(e?.scope ?? "HOUSEHOLD");
    setAllDay(e?.allDay ?? true);
    setStart(e ? (e.allDay ? e.startDate! : seoulTime(e.startAt!)) : day);
    setEnd(e ? (e.allDay ? e.endDate! : seoulTime(e.endAt!)) : day);
    setError("");
    setForm(true);
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      await api(root + (edit ? "/" + edit.id : ""), edit ? "PATCH" : "POST", {
        title,
        description,
        category,
        allDay,
        startDate: allDay ? start : null,
        endDate: allDay ? end : null,
        startAt: allDay ? null : new Date(start + ":00+09:00").toISOString(),
        endAt: allDay || !end ? null : new Date(end + ":00+09:00").toISOString(),
        timeZone: edit?.timeZone ?? "Asia/Seoul",
        version: edit?.version || 0,
        ...(!edit && scope !== "HOUSEHOLD" ? { scope } : {}),
      });
      setForm(false);
      await client.invalidateQueries({ queryKey: ["calendar", householdId] });
    } catch (e) {
      setError((e as Error).message);
      void query.refetch();
    } finally {
      setBusy(false);
      lock.current = false;
    }
  }
  async function remove(e: CalendarEvent) {
    if (busy || !confirm("일정을 삭제할까요?")) return;
    setBusy(true);
    try {
      await api(root + "/" + e.id + "?version=" + e.version, "DELETE");
      await query.refetch();
    } catch (e) {
      setError((e as Error).message);
      void query.refetch();
    } finally {
      setBusy(false);
    }
  }
  async function moderate(event: { id: string; version: number }, hide: boolean) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      await api(root + "/" + event.id + (hide ? "/hide" : "/unhide"), "POST", { version: event.version });
      await client.invalidateQueries({ queryKey: ["calendar", householdId] });
    } catch (e) {
      setError((e as Error).message);
      void client.invalidateQueries({ queryKey: ["calendar", householdId] });
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return (
    <section className="family-calendar">
      <p className="muted">
        가족이 함께 쓰는 달력이에요. 시간은 한국 시간을 기준으로 표시해요.
      </p>
      <nav className="calendar-nav" aria-label="달력 탐색">
        <button
          className="button secondary"
          aria-label="이전 달"
          onClick={() => shift(-1)}
        >
          ←
        </button>
        <strong>
          {year}년 {mon}월
        </strong>
        <button
          className="button secondary"
          aria-label="다음 달"
          onClick={() => shift(1)}
        >
          →
        </button>
        <button
          className="button secondary"
          onClick={() => {
            setMonth(today.slice(0, 7));
            setDay(today);
          }}
        >
          오늘
        </button>
      </nav>
      {query.isPending ? (
        <p role="status">일정을 가져오고 있어요…</p>
      ) : query.error ? (
        <p role="alert">
          {query.error.message}
          <button onClick={() => void query.refetch()}>다시 시도</button>
        </p>
      ) : (
        <>
          <div className="calendar-grid">
            {["일", "월", "화", "수", "목", "금", "토"].map((d) => (
              <span key={d}>{d}</span>
            ))}
            {Array.from({ length: offset }, (_, i) => (
              <span key={"blank" + i} />
            ))}
            {Array.from({ length: days }, (_, i) => {
              const date = month + "-" + String(i + 1).padStart(2, "0"),
                count = query.data?.filter((e) => occurs(e, date)).length || 0;
              return (
                <button
                  key={date}
                  aria-label={`${date}${count ? ` 일정 ${count}개` : ""}`}
                  aria-pressed={day === date}
                  aria-current={date === today ? "date" : undefined}
                  onClick={() => setDay(date)}
                >
                  {i + 1}
                  {count > 0 && <small>{count}개</small>}
                </button>
              );
            })}
          </div>
          <h3>
            {new Date(day + "T12:00:00+09:00").toLocaleDateString("ko-KR", {
              month: "long",
              day: "numeric",
              weekday: "long",
              timeZone: "Asia/Seoul",
            })}{" "}
            일정
          </h3>
          {query.data?.filter((e) => occurs(e, day)).length ? (
            query.data
              .filter((e) => occurs(e, day))
              .map((e) => (
                <article className="calendar-entry" key={e.id}>
                  <strong>{e.title}</strong>
                  <small>
                    {e.allDay
                      ? "종일"
                      : seoulTime(e.startAt!).replace("T", " ")}{" "}
                    ·{" "}
                    {
                      {
                        GENERAL: "일반",
                        BIRTHDAY: "생일",
                        ANNIVERSARY: "기념일",
                        CARE: "돌봄",
                      }[e.category]
                    }
                    {e.scope === "PERSONAL_SHARED" ? " · 개인 공유" : " · 공동 일정"}
                    {e.hiddenAt ? " · 가족 화면에서 숨김" : ""}
                  </small>
                  <p>{e.description}</p>
                  <div>
                    {e.canEdit !== false && (
                      <>
                        <button className="button secondary" onClick={() => open(e)}>상세 / 수정</button>
                        <button disabled={busy} className="button secondary" onClick={() => void remove(e)}>삭제</button>
                      </>
                    )}
                    {e.canHide && !e.hiddenAt && (
                      <button disabled={busy} className="button secondary" onClick={() => void moderate(e, true)}>가족 화면에서 숨기기</button>
                    )}
                  </div>
                </article>
              ))
          ) : (
            <p className="empty-text">
              아직 일정이 없어요. 함께할 하루를 남겨보세요.
            </p>
          )}
        </>
      )}
      {isOwner && (
        <details onToggle={(event) => setShowHidden(event.currentTarget.open)}>
          <summary>숨긴 개인 일정 관리</summary>
          {showHidden && (
            <div className="calendar-moderation">
              {hidden.isPending ? <p role="status">숨긴 일정을 확인하고 있어요…</p>
                : hidden.error ? <p role="alert">{hidden.error.message}</p>
                : hidden.data?.length ? hidden.data.map((event) => (
                  <div key={event.id}>
                    <span>숨긴 일정 · {seoulTime(event.hiddenAt).replace("T", " ")}</span>
                    <button className="button secondary" disabled={busy} onClick={() => void moderate(event, false)}>숨김 해제</button>
                  </div>
                )) : <p className="muted">이 달에 숨긴 개인 일정이 없어요.</p>}
            </div>
          )}
        </details>
      )}
      <button className="button primary" onClick={() => open(null)}>
        일정 등록
      </button>
      {form && (
        <form
          aria-label={edit ? "일정 수정" : "새 일정"}
          className="calendar-form"
          onSubmit={submit}
        >
          <h3>{edit ? "일정 수정" : "새 일정"}</h3>
          <label>
            제목
            <input
              required
              ref={titleField}
              maxLength={120}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </label>
          <label>
            설명
            <textarea
              maxLength={2000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
          {!edit && (
            <label>공유 종류
              <select value={scope} onChange={(event) => setScope(event.target.value as typeof scope)}>
                <option value="HOUSEHOLD">공동 일정 · 작성자와 집 대표가 수정</option>
                <option value="PERSONAL_SHARED">개인 공유 · 나만 수정</option>
              </select>
            </label>
          )}
          <label>
            종류
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              <option value="GENERAL">일반 일정</option>
              <option value="BIRTHDAY">생일</option>
              <option value="ANNIVERSARY">기념일</option>
              <option value="CARE">돌봄</option>
            </select>
          </label>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={allDay}
              onChange={(e) => {
                setAllDay(e.target.checked);
                setStart(day + (e.target.checked ? "" : "T09:00"));
                setEnd(day + (e.target.checked ? "" : "T10:00"));
              }}
            />
            종일 일정
          </label>
          <label>
            시작
            <input
              required
              type={allDay ? "date" : "datetime-local"}
              value={start}
              onChange={(e) => setStart(e.target.value)}
            />
          </label>
          <label>
            종료{!allDay && " · 선택"}
            <input
              required={allDay}
              min={start}
              type={allDay ? "date" : "datetime-local"}
              value={end}
              onChange={(e) => setEnd(e.target.value)}
            />
          </label>
          <div className="form-actions">
            <button
              type="button"
              disabled={busy}
              className="button secondary"
              onClick={() => setForm(false)}
            >
              취소
            </button>
            <button disabled={busy} className="button primary">
              {busy ? "저장 중…" : "일정 저장"}
            </button>
          </div>
        </form>
      )}
      {error && (
        <p role="alert" className="error-message">
          {error}
          {edit && query.data?.some((item) => item.id === edit.id) && (
            <button
              className="button secondary"
              type="button"
              onClick={() =>
                open(query.data!.find((item) => item.id === edit.id)!)
              }
            >
              최신 내용으로 다시 열기
            </button>
          )}
        </p>
      )}
    </section>
  );
}
