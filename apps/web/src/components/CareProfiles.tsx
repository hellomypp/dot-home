import { useRef, useState, type FormEvent } from "react";
import { api, type Dependent, type Snapshot } from "../lib/api";
import { PetManager } from "./PetManager";
import { Portrait } from "./Portrait";
import { RepeatCarePanel } from "./RepeatCarePanel";

type Props = {
  snapshot: Snapshot;
  onPetSaved: (world: Snapshot) => void;
  refresh: () => Promise<unknown>;
};

function BabyForm({ snapshot, profile, onSave, onCancel }: {
  snapshot: Snapshot;
  profile?: Dependent;
  onSave: () => Promise<void>;
  onCancel?: () => void;
}) {
  const [name, setName] = useState(profile?.name ?? "");
  const [guardian, setGuardian] = useState(profile?.guardianMemberId ?? snapshot.meMemberId);
  const [confirmArchive, setConfirmArchive] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const path = `/api/households/${snapshot.household.id}/dependents${profile ? "/" + profile.id : ""}`;

  async function change(action: "save" | "archive") {
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      if (action === "archive" && profile) {
        await api(`${path}?version=${profile.version}`, "DELETE");
      } else {
        await api(path, profile ? "PATCH" : "POST", {
          type: "BABY",
          name: name.trim(),
          guardianMemberId: guardian,
          ...(profile ? { version: profile.version } : {}),
        });
      }
      await onSave();
      if (!profile) setName("");
    } catch (error) {
      setError((error as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError("아기 이름을 입력해 주세요.");
      return;
    }
    void change("save");
  }

  return (
    <form className="pet-form" onSubmit={submit}>
      <label>아기 이름
        <input required maxLength={40} value={name} disabled={busy} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>아기 보호자
        <select aria-label="아기 보호자" value={guardian} disabled={busy} onChange={(e) => setGuardian(e.target.value)}>
          {snapshot.members.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
        </select>
      </label>
      {error && <p role="alert" className="error-message">{error}</p>}
      {confirmArchive ? (
        <>
          <p>현재 프로필과 새 일정의 대상 목록에서 숨겨요. 기존 일정은 가족 달력에 남아요.</p>
          <div className="form-actions">
            <button className="button secondary" type="button" disabled={busy} onClick={() => setConfirmArchive(false)}>돌아가기</button>
            <button className="button secondary" type="button" disabled={busy} onClick={() => void change("archive")}>보관 확인</button>
          </div>
        </>
      ) : (
        <div className="form-actions">
          {onCancel && <button className="button secondary" type="button" disabled={busy} onClick={onCancel}>취소</button>}
          {profile && <button className="button secondary" type="button" disabled={busy} onClick={() => setConfirmArchive(true)}>프로필 보관</button>}
          <button className="button primary" disabled={busy}>{busy ? "처리 중…" : profile ? "아기 프로필 저장" : "아기 프로필 추가"}</button>
        </div>
      )}
    </form>
  );
}

export function CareProfiles({ snapshot, onPetSaved, refresh }: Props) {
  const [section, setSection] = useState<"pets" | "babies" | "repeat">("pets");
  const [editing, setEditing] = useState<string | null>(null);
  const babies = snapshot.dependents ?? [];
  const owner = snapshot.members.some((member) => member.id === snapshot.meMemberId && member.role === "OWNER");
  async function refreshList() {
    await refresh();
    setEditing(null);
  }

  return (
    <>
      <div className="care-profile-switch" role="group" aria-label="돌봄 프로필 종류">
        <button type="button" className="button secondary" aria-pressed={section === "pets"} onClick={() => setSection("pets")}>반려동물</button>
        <button type="button" className="button secondary" aria-pressed={section === "babies"} onClick={() => setSection("babies")}>아기 프로필</button>
        <button type="button" className="button secondary" aria-pressed={section === "repeat"} onClick={() => setSection("repeat")}>반복 돌봄</button>
      </div>
      {section === "repeat" ? <RepeatCarePanel snapshot={snapshot} /> : section === "pets" ? <PetManager snapshot={snapshot} onSaved={onPetSaved} /> : (
        <section className="pet-manager" aria-label="아기 돌봄">
          <p>보호자가 관리하는 돌봄 프로필이에요. 가족 달력에서 아기를 돌봄 일정의 대상으로 선택할 수 있어요.</p>
          {!babies.length && <p className="empty-text">등록한 아기 프로필이 없어요.</p>}
          {babies.map((profile) => {
            const guardian = snapshot.members.find((member) => member.id === profile.guardianMemberId);
            return (
              <article className="pet-manager-card" key={profile.id}>
                {editing === profile.id ? (
                  <BabyForm snapshot={snapshot} profile={profile} onSave={refreshList} onCancel={() => setEditing(null)} />
                ) : (
                  <>
                    <div className="care-profile-summary">
                      <Portrait small appearance={profile.appearanceProfile} />
                      <div>
                        <strong>{profile.name}</strong>
                        <p>{guardian ? `${guardian.name} 님이 보호자로 관리 중` : "보호자 관리 프로필"}</p>
                      </div>
                    </div>
                    {(owner || profile.guardianMemberId === snapshot.meMemberId) && (
                      <button className="button secondary" aria-label={`${profile.name} 프로필 수정`} onClick={() => setEditing(profile.id)}>수정</button>
                    )}
                  </>
                )}
              </article>
            );
          })}
          {!editing && <>
            <h3>새 아기 프로필</h3>
            <BabyForm snapshot={snapshot} onSave={refreshList} />
          </>}
        </section>
      )}
    </>
  );
}
