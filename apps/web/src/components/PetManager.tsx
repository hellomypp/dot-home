import { useRef, useState, type FormEvent } from "react";
import { api, type Snapshot } from "../lib/api";
import { petGroup, petSpecies, type Pet, type PetSpecies } from "../lib/pets";
import { TankPlacement } from "./TankPlacement";
import { mapFor } from "../game/spaces";
import "./pets.css";

function PetForm({ snapshot, pet, onSaved, onCancel }: {
  snapshot: Snapshot;
  pet?: Pet;
  onSaved: (world: Snapshot) => void;
  onCancel?: () => void;
}) {
  const [name, setName] = useState(pet?.name ?? "");
  const [group, setGroup] = useState(petGroup(pet));
  const [speciesName, setSpeciesName] = useState(pet?.speciesName ?? "");
  const [guardian, setGuardian] = useState(pet?.guardianMemberId ?? "");
  const [pickedSlot, setPickedSlot] = useState<number | null>(null);
  const [confirmArchive, setConfirmArchive] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const prefix = pet?.name ?? "반려동물";
  const kind = group === "CAT" ? "cat" : group === "DOG" ? "dog" : undefined;

  const map = mapFor(snapshot.design?.presetId);
  const occupied = new Set(snapshot.pets.flatMap((item) => item.id !== pet?.id && item.tankSlot != null ? [item.tankSlot] : []));
  const slot = pickedSlot ?? pet?.tankSlot ?? null;
  const validSlot = slot !== null && map.tankSlots.includes(slot) && !occupied.has(slot);
  const needsSlot = group === "FISH" && ((!pet || petGroup(pet) !== "FISH" || pickedSlot !== null) && !validSlot);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (confirmArchive || pending.current || needsSlot) return;
    if (!name.trim() || name.length > 40 || /[\u0000-\u001f\u007f-\u009f]/.test(name)) {
      setError("반려동물 이름은 줄바꿈 없이 1~40자로 입력해 주세요.");
      return;
    }
    void change(false);
  }

  async function change(archive: boolean) {
    if (pending.current) return;
    pending.current = true;
    setError("");
    setBusy(true);
    const path = `/api/households/${snapshot.household.id}/pets${pet ? "/" + pet.id : ""}`;
    try {
      const world = archive && pet
        ? await api<Snapshot>(`${path}?version=${pet.version ?? 0}`, "DELETE")
        : await api<Snapshot>(path, pet ? "PATCH" : "POST",
        {
          name: name.trim(),
          ...(kind ? { kind } : {}),
          speciesGroup: group,
          speciesName: speciesName.trim(),
          ...(group === "FISH" && validSlot ? { tankSlot: slot } : {}),
          ...(guardian ? { guardianMemberId: guardian } : {}),
          ...(pet ? { version: pet.version ?? 0 } : { appearance: "cream" }),
        },
      );
      onSaved(world);
      if (!pet) {
        setName("");
        setSpeciesName("");
        setPickedSlot(null);
      }
    } catch (error) {
      setError((error as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="pet-form">
      <label>{prefix} 이름
        <input required maxLength={40} value={name} disabled={busy} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>{prefix} 종류
        <select aria-label={`${prefix} 종류`} value={group} disabled={busy} onChange={(e) => setGroup(e.target.value as PetSpecies)}>
          {(Object.keys(petSpecies) as PetSpecies[]).map((id) => (
            <option key={id} value={id}>{petSpecies[id].name}</option>
          ))}
        </select>
      </label>
      <label>{prefix} 종 이름 · 선택
        <input maxLength={80} value={speciesName} disabled={busy} onChange={(e) => setSpeciesName(e.target.value)} />
      </label>
      {!kind && <p className="pet-habitat-hint">집 장면에서는 {petSpecies[group].habitat} 범용 그림으로 보여요.</p>}
      <label>{prefix} 보호자
        <select aria-label={`${prefix} 보호자`} value={guardian} disabled={busy} onChange={(e) => setGuardian(e.target.value)}>
          <option value="">{pet ? "기존 보호자 유지" : "내가 보호자 되기"}</option>
          {snapshot.members.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
        </select>
      </label>
      {group === "FISH" && !confirmArchive && (
        <TankPlacement design={snapshot.design} occupied={occupied}
          selected={pickedSlot ?? (validSlot ? slot : null)} onSelect={setPickedSlot} busy={busy} />
      )}
      {error && <p role="alert" className="error-message">{error}</p>}
      {confirmArchive ? (
        <>
          <p>집 장면과 새 일정의 대상 목록에서 숨겨요. 기존 일정은 가족 달력에 남아요.</p>
          <div className="form-actions">
            <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirmArchive(false)}>돌아가기</button>
            <button type="button" className="button secondary" disabled={busy} onClick={() => void change(true)}>보관 확인</button>
          </div>
        </>
      ) : (
        <div className="form-actions">
          {onCancel && <button type="button" className="button secondary" disabled={busy} onClick={onCancel}>취소</button>}
          {pet && <button type="button" className="button secondary" disabled={busy} onClick={() => setConfirmArchive(true)}>프로필 보관</button>}
          <button className="button primary" disabled={busy || needsSlot}>
            {busy ? "처리 중…" : pet ? "반려동물 변경 저장" : "반려동물 등록"}
          </button>
        </div>
      )}
    </form>
  );
}

export function PetManager({ snapshot, onSaved }: { snapshot: Snapshot; onSaved: (world: Snapshot) => void }) {
  const [editing, setEditing] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const owner = snapshot.members.some((member) => member.id === snapshot.meMemberId && member.role === "OWNER");
  return (
    <section className="pet-manager">
      <p>보호자가 반려동물의 정보를 관리해요. 집 속 모습과 움직임은 가상 연출이에요.</p>
      {!snapshot.pets.length && <p className="empty-text">아직 등록된 반려동물이 없어요.</p>}
      {snapshot.pets.map((pet) => {
        const group = petGroup(pet);
        const guardian = snapshot.members.find((member) => member.id === pet.guardianMemberId);
        return (
          <article className="pet-manager-card" key={pet.id}>
            {editing === pet.id ? (
              <PetForm snapshot={snapshot} pet={pet} onCancel={() => setEditing(null)} onSaved={(world) => {
                onSaved(world);
                setEditing(null);
                setMessage(world.pets.some((item) => item.id === pet.id) ? "반려동물 정보를 변경했어요." : "반려동물 프로필을 보관했어요.");
              }} />
            ) : (
              <>
                <div>
                  <strong>{pet.name}</strong>
                  <p>{pet.speciesName || petSpecies[group].name}{group !== "CAT" && group !== "DOG" && ` · ${petSpecies[group].habitat} 범용 표현`}</p>
                  <p>{guardian ? `${guardian.name} 님이 보호자로 관리 중` : "보호자 관리 프로필"}</p>
                </div>
                {(owner || pet.guardianMemberId === snapshot.meMemberId) && (
                  <button className="button secondary" aria-label={`${pet.name} 수정`} onClick={() => {
                    setEditing(pet.id);
                    setMessage("");
                  }}>수정</button>
                )}
              </>
            )}
          </article>
        );
      })}
      <h3>새 반려동물</h3>
      <PetForm snapshot={snapshot} onSaved={(world) => {
        onSaved(world);
        setMessage("반려동물을 등록했어요.");
      }} />
      {message && <p role="status">{message}</p>}
    </section>
  );
}
