import { useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Snapshot } from "../lib/api";

const weekdays = [
  ["MONDAY", "월"], ["TUESDAY", "화"], ["WEDNESDAY", "수"], ["THURSDAY", "목"],
  ["FRIDAY", "금"], ["SATURDAY", "토"], ["SUNDAY", "일"],
] as const;
type Day = typeof weekdays[number][0];
type Profile = { id: string; name: string; type: "PET" | "BABY"; guardianMemberId?: string | null };
type Rule = {
  id: string; profileType: Profile["type"]; profileId: string; title: string;
  weekdays: Day[]; localTime: string; timeZone: string; nextAt: string; version: number; canEdit: boolean;
};

function RuleForm({ profiles, rule, root, done, cancel }: {
  profiles: Profile[]; rule?: Rule; root: string; done: () => Promise<void>; cancel?: () => void;
}) {
  const [profileId, setProfileId] = useState(rule?.profileId ?? profiles[0]?.id ?? "");
  const [title, setTitle] = useState(rule?.title ?? "");
  const [time, setTime] = useState(rule?.localTime.slice(0, 5) ?? "09:00");
  const [zone, setZone] = useState(rule?.timeZone ?? "Asia/Seoul");
  const [days, setDays] = useState<Day[]>(rule?.weekdays ?? weekdays.map(([id]) => id));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [confirm, setConfirm] = useState(false);
  const pending = useRef(false);

  async function save(remove = false) {
    if (pending.current) return;
    const profile = profiles.find((item) => item.id === profileId);
    if (!remove && (!profile || !title.trim() || !days.length)) {
      setError("대상과 제목을 입력하고 요일을 한 개 이상 선택해 주세요.");
      return;
    }
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      if (remove && rule) {
        await api(`${root}/${rule.id}?version=${rule.version}`, "DELETE");
      } else {
        await api(rule ? `${root}/${rule.id}` : root, rule ? "PATCH" : "POST", {
          profileType: profile?.type, profileId, title: title.trim(), weekdays: days,
          localTime: time, timeZone: zone.trim(), ...(rule ? { version: rule.version } : {}),
        });
      }
      await done();
      if (!rule) setTitle("");
    } catch (error) {
      setError((error as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!confirm) void save();
  }

  return (
    <form className="pet-form" onSubmit={submit}>
      <label>돌봄 대상
        <select aria-label="돌봄 대상" value={profileId} disabled={busy || !!rule} onChange={(e) => setProfileId(e.target.value)}>
          {profiles.map((profile) => <option key={profile.id} value={profile.id}>{profile.name} · {profile.type === "BABY" ? "아기" : "반려동물"}</option>)}
        </select>
      </label>
      <label>돌봄 이름<input required maxLength={80} value={title} disabled={busy} onChange={(e) => setTitle(e.target.value)} placeholder="예. 저녁 산책" /></label>
      <fieldset className="care-weekdays">
        <legend>반복 요일</legend>
        <div>
          {weekdays.map(([day, label]) => (
            <button className="button secondary" key={day} type="button" disabled={busy} aria-label={label + "요일"} aria-pressed={days.includes(day)}
              onClick={() => setDays((value) => value.includes(day) ? value.filter((item) => item !== day) : [...value, day])}>
              {label}
            </button>
          ))}
        </div>
        <small>{days.length === 7 ? "매일" : "선택한 요일에만 반복해요."}</small>
      </fieldset>
      <label>돌봄 시각<input required type="time" value={time} disabled={busy} onChange={(e) => setTime(e.target.value)} /></label>
      <label>시간대<input required maxLength={64} list="care-timezones" value={zone} disabled={busy} onChange={(e) => setZone(e.target.value)} /></label>
      <datalist id="care-timezones">
        {["Asia/Seoul", "Asia/Tokyo", "America/Los_Angeles", "America/New_York", "Europe/London", "Australia/Sydney"].map((item) => <option key={item} value={item} />)}
      </datalist>
      {error && <p role="alert" className="error-message">{error}</p>}
      {confirm ? (
        <>
          <p>앞으로 요청이 만들어지지 않고 진행 중인 요청은 취소돼요. 완료한 기록은 남아요.</p>
          <div className="form-actions">
            <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirm(false)}>돌아가기</button>
            <button type="button" className="button secondary" disabled={busy} onClick={() => void save(true)}>반복 종료 확인</button>
          </div>
        </>
      ) : (
        <div className="form-actions">
          {cancel && <button type="button" className="button secondary" disabled={busy} onClick={cancel}>닫기</button>}
          {rule && <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirm(true)}>반복 종료</button>}
          <button className="button primary" disabled={busy}>{busy ? "처리 중…" : "반복 돌봄 저장"}</button>
        </div>
      )}
    </form>
  );
}

export function RepeatCarePanel({ snapshot }: { snapshot: Snapshot }) {
  const [editing, setEditing] = useState<string | null>(null);
  const qc = useQueryClient();
  const house = snapshot.household.id;
  const root = `/api/households/${house}/care-rules`;
  const query = useQuery({ queryKey: ["care-rules", house], queryFn: () => api<Rule[]>(root) });
  const profiles: Profile[] = [
    ...snapshot.pets.map((pet) => ({ ...pet, type: "PET" as const })),
    ...(snapshot.dependents ?? []),
  ];
  const owner = snapshot.members.some((member) => member.id === snapshot.meMemberId && member.role === "OWNER");
  const managed = profiles.filter((profile) => owner || profile.guardianMemberId === snapshot.meMemberId);
  async function done() {
    await qc.invalidateQueries({ queryKey: ["care-rules", house] });
    setEditing(null);
  }

  return (
    <section className="pet-manager" aria-label="반복 돌봄 설정">
      <p>아기와 반려동물의 돌봄을 예약해요. 예정 시각이 되면 생활 기록의 ‘부탁·공지’에 요청이 생겨요.</p>
      {query.isPending && <p role="status">반복 돌봄을 불러오는 중이에요…</p>}
      {query.isError && <div><p role="alert">{query.error.message}</p><button className="button secondary" onClick={() => void query.refetch()}>다시 불러오기</button></div>}
      {query.data?.length === 0 && <p className="empty-text">예약한 반복 돌봄이 없어요.</p>}
      {query.data?.map((rule) => (
        <article className="pet-manager-card" key={rule.id}>
          {editing === rule.id && rule.canEdit ? (
            <RuleForm profiles={managed} rule={rule} root={root} done={done} cancel={() => setEditing(null)} />
          ) : (
            <>
              <div>
                <strong>{rule.title}</strong>
                <p>{profiles.find((profile) => profile.id === rule.profileId)?.name} · {rule.weekdays.length === 7 ? "매일" : weekdays.filter(([id]) => rule.weekdays.includes(id)).map(([, label]) => label).join(" ")} {rule.localTime.slice(0, 5)}</p>
                <p>다음 {new Intl.DateTimeFormat("ko-KR", { timeZone: rule.timeZone, dateStyle: "medium", timeStyle: "short" }).format(new Date(rule.nextAt))} · {rule.timeZone}</p>
              </div>
              {rule.canEdit && <button className="button secondary" aria-label={`${rule.title} 수정`} onClick={() => setEditing(rule.id)}>수정</button>}
            </>
          )}
        </article>
      ))}
      {!editing && (managed.length > 0
        ? <><h3>새 반복 돌봄</h3><RuleForm profiles={managed} root={root} done={done} /></>
        : <p className="muted">등록된 프로필의 보호자와 집 대표가 반복 돌봄을 만들 수 있어요.</p>)}
    </section>
  );
}
