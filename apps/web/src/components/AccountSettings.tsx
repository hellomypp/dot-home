import {
  LogOut,
  Home,
  Users,
  Paintbrush,
  DoorOpen,
  Download,
  Trash2,
  ChevronRight,
} from "lucide-react";
import { Link } from "react-router-dom";
import type { Snapshot } from "../lib/api";
import type { Place } from "../game/types";
import { Portrait } from "./Portrait";
import { NicknameForm } from "./NicknameForm";
import { CharacterStyleForm } from "./CharacterStyleForm";
export function AccountSettings({
  snapshot,
  studio,
  place,
  palette,
  reduced,
  onPalette,
  onReduced,
  onOpen,
  onSaved,
  onLogout,
}: {
  snapshot: Snapshot;
  studio: boolean;
  place: Place;
  palette: "warm" | "dusk";
  reduced: boolean;
  onPalette: (p: "warm" | "dusk") => void;
  onReduced: (v: boolean) => void;
  onOpen: (p: "design" | "housewarming" | "invite" | "membership" | "exports" | "deletion") => void;
  onSaved: (s: Snapshot) => void;
  onLogout: () => void;
}) {
  const own = snapshot.members.find((m) => m.id === snapshot.meMemberId)!;
  return (
    <div className="settings-panel">
      <section className="settings-section">
        <div className="settings-account">
          <Portrait appearance={own.appearance} />
          <div>
            <strong>{own.name}</strong>
            <p>
              {own.role === "OWNER" ? "집 대표" : "가족 구성원"} ·{" "}
              {snapshot.household.name}
            </p>
          </div>
        </div>
        {!studio && (
          <>
            <NicknameForm
              householdId={snapshot.household.id}
              nickname={own.name}
              onSaved={onSaved}
            />
            <details className="settings-details">
              <summary>캐릭터 외형 바꾸기</summary>
              <CharacterStyleForm snapshot={snapshot} onSaved={onSaved} />
            </details>
          </>
        )}
      </section>
      <section className="settings-section">
        <h3>우리 집</h3>
        <div className="settings-links">
          {!studio && (
            <Link to="/homes">
              <Home size={20} />
              <span>다른 집으로 전환</span>
              <ChevronRight size={18} />
            </Link>
          )}
          <button onClick={() => onOpen("design")}>
            <Paintbrush size={20} />
            <span>우리 집 설정 · 집 분위기</span>
            <ChevronRight size={18} />
          </button>
          <button onClick={() => onOpen("invite")}>
            <Users size={20} />
            <span>가족 초대 관리</span>
            <ChevronRight size={18} />
          </button>
          {!studio && (
            <button onClick={() => onOpen("membership")}>
              <Users size={20} />
              <span>가족 구성원 · 집 참여</span>
              <ChevronRight size={18} />
            </button>
          )}
          {!studio && (
            <button onClick={() => onOpen("exports")}>
              <Download size={20} />
              <span>공동 기록 내보내기</span>
              <ChevronRight size={18} />
            </button>
          )}
          {own.role === "OWNER" && (
            <button onClick={() => onOpen("housewarming")}>
              <DoorOpen size={20} />
              <span>집들이 · 친구 방문</span>
              <ChevronRight size={18} />
            </button>
          )}
        </div>
        {!studio && own.role === "OWNER" && (snapshot.household.deletionAvailable || snapshot.household.pendingDeleteUntil) && (
          <div className="settings-links">
            <button onClick={() => onOpen("deletion")}>
              <Trash2 size={20} />
              <span>{snapshot.household.pendingDeleteUntil ? "집 삭제 대기 관리" : "집 삭제"}</span>
              <ChevronRight size={18} />
            </button>
          </div>
        )}
      </section>
      <section className="settings-section">
        <h3>화면과 움직임</h3>
        {place !== "home" && (
          <label>
            외부 공간 시안
            <select
              value={palette}
              onChange={(e) => onPalette(e.target.value as "warm" | "dusk")}
            >
              <option value="warm">포근한 오후</option>
              <option value="dusk">차분한 저녁</option>
            </select>
          </label>
        )}
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={reduced}
            onChange={(e) => onReduced(e.target.checked)}
          />{" "}
          자율 움직임 줄이기
        </label>
        <p className="muted">
          다른 가족을 구경해도 기록은 항상 내 이름으로 남아요.
        </p>
      </section>
      {studio ? (
        <Link className="button primary" to="/login">
          실제 우리 집 시작하기
        </Link>
      ) : (
        <button className="button secondary logout-button" onClick={onLogout}>
          <LogOut size={18} />
          로그아웃
        </button>
      )}
    </div>
  );
}
