import {PresetPicker} from '../components/SpaceSettings';
import {CharacterChoices} from '../components/CharacterStyleForm';
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronRight,
  Home,
  Mail,
} from "lucide-react";
import { api, ApiError, type Identity, type Snapshot } from "../lib/api";
import { safeInvitePath } from "../lib/auth";
import { SessionBar } from "../components/SessionBar";
import { Portrait } from "../components/Portrait";
import { PixelScene } from "./PixelScene";
import { readDraft, saveDraft, draftPrefix, type Draft } from "./draft";
import "./onboarding.css";

export function Onboarding() {
  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Identity>("/api/me"),
    retry: false,
  });
  if (me.error instanceof ApiError && me.error.status === 401)
    return <Navigate to="/login" replace />;
  if (!me.data)
    return (
      <div className="onboarding-shell">
        <p role={me.error ? "alert" : "status"}>
          {me.error?.message || "작은 세계를 준비하고 있어요…"}
        </p>
      </div>
    );
  return <OnboardingExperience key={me.data.user.id} identity={me.data} />;
}
export function OnboardingPreview() {
  return (
    <OnboardingExperience
      identity={{
        user: { id: "design-preview", name: "하늘" },
        memberships: [],
      }}
      preview
    />
  );
}
function Progress({ step }: { step: number }) {
  return (
    <ol className="onboarding-progress" aria-label="입주 준비 단계">
      {["집 정보", "캐릭터", "완료"].map((name, i) => (
        <li
          key={name}
          aria-current={step === i + 1 ? "step" : undefined}
          className={step >= i + 1 ? "active" : ""}
        >
          <span>{step > i + 1 ? <Check size={16} /> : i + 1}</span>
          <b>{name}</b>
        </li>
      ))}
    </ol>
  );
}
export function OnboardingExperience({
  identity,
  preview = false,
}: {
  identity: Identity;
  preview?: boolean;
}) {
  const { user, memberships } = identity,
    nav = useNavigate(),
    client = useQueryClient();
  const [draft, setDraft] = useState(() => readDraft(user.id, user.name)),
    [join, setJoin] = useState(false),
    [invite, setInvite] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [complete, setComplete] = useState<Snapshot | null>(null);
  useEffect(() => {
    if (!complete || preview) return;
    heading.current?.focus();
    const timer = setTimeout(
      () => nav(`/home/${complete.household.id}`, { replace: true }),
      1600,
    );
    return () => clearTimeout(timer);
  }, [complete, preview, nav]);
  const lock = useRef(false),
    heading = useRef<HTMLHeadingElement>(null),
    stage = useRef(draft.step);
  useEffect(() => {
    if (!complete) saveDraft(user.id, draft);
  }, [draft, user.id, complete]);
  useEffect(() => {
    if (stage.current !== draft.step) {
      stage.current = draft.step;
      heading.current?.focus();
      window.scrollTo?.({ top: 0, behavior: "instant" });
    }
  }, [draft.step]);
  const change = (patch: Partial<Draft>) =>
    setDraft((old) => ({ ...old, ...patch }));
  const move = (step: Draft["step"]) => {
    setError("");
    change({ step });
  };
  function followInvite(e: FormEvent) {
    e.preventDefault();
    try {
      const url = new URL(invite, window.location.origin);
      const path =
        url.origin === window.location.origin && url.pathname.startsWith("/invite/") && !url.search && !url.hash
          ? safeInvitePath(url.pathname)
          : null;
      if (!path) throw new Error();
      nav(path);
    } catch {
      setError("이 도트홈에서 받은 /invite/ 초대 링크를 확인해 주세요.");
    }
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      if (
        !draft.name.trim() ||
        !draft.memberName.trim()
      )
        throw new Error("집과 캐릭터 이름을 입력해 주세요.");
      const body = {
        name: draft.name.trim(),
        memberName: draft.memberName.trim(),
        appearance: draft.appearance,
        presetId:draft.presetId,

      };
      const data = preview
        ? ({ household: { id: "studio", name: body.name } } as Snapshot)
        : await api<Snapshot>("/api/households", "POST", body);
      setComplete(data);
      if (!preview) {
        try {
          sessionStorage.removeItem(draftPrefix + user.id);
        } catch {
          /* Creation succeeded even when storage is unavailable. */
        }
        void client.invalidateQueries({ queryKey: ["me"] });
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  const isStory = draft.step === 0 && !complete;
  return (
    <div className={`onboarding-shell ${isStory ? "story-shell" : ""}`}>
      <div className="onboarding-frame">
        <header className="onboarding-header">
          <Link
            to={preview ? "/studio/onboarding" : "/onboarding"}
            onClick={() => move(0)}
            className="onboarding-brand"
            aria-label="도트홈 시작"
          >
            <Home size={25} />
            <span>
              도트홈<small>OUR LITTLE EVERYDAY</small>
            </span>
          </Link>
          {preview ? (
            <span className="preview-label">저장되지 않는 아트 시안</span>
          ) : (
            <SessionBar name={user.name} />
          )}
        </header>
        {!isStory && <Progress step={complete ? 3 : draft.step} />}
        {complete ? (
          <main className="onboarding-complete">
            <span className="chapter">WELCOME HOME</span>
            <h1 tabIndex={-1} ref={heading}>
              우리 집에 오신 걸 환영해요!
            </h1>
            <PixelScene kind="garden" appearance={draft.appearance} />
            <h2>{complete.household.name}</h2>
            <p>함께 쌓아갈 작은 일상이 시작됐어요.</p>
            <Link
              className="button primary"
              to={preview ? "/studio" : `/home/${complete.household.id}`}
            >
              우리 집으로 들어가기 <ArrowRight size={19} />
            </Link>
          </main>
        ) : isStory ? (
          <main className="story-start">
            <section className="story-picture">
              <div className="story-bubble">
                <small>{user.name}님, 반가워요</small>
                <h1 ref={heading} tabIndex={-1}>
                  우리만의 작은 집을
                  <br />
                  만들어볼까요?
                </h1>
              </div>
              <PixelScene kind="garden" appearance={draft.appearance} />
            </section>
            <section className="story-choices" aria-label="우리 집 시작하기">
              <button className="story-choice new-home" onClick={() => move(1)}>
                <span className="choice-art">
                  <Home />
                </span>
                <span>
                  <strong>새로운 집 만들기</strong>
                  <small>우리 가족만의 공간을 시작해요</small>
                </span>
                <ChevronRight size={21} />
              </button>
              <button
                className="story-choice"
                aria-expanded={join}
                onClick={() => {
                  setJoin(!join);
                  setError("");
                }}
              >
                <span className="choice-art">
                  <Mail />
                </span>
                <span>
                  <strong>초대받은 집에 입주하기</strong>
                  <small>가족에게 받은 초대 링크로 만나요</small>
                </span>
                <ChevronRight size={21} />
              </button>
              {memberships.map((m) => (
                <Link
                  key={m.householdId}
                  className="story-choice"
                  to={`/home/${m.householdId}`}
                >
                  <span className="choice-art">
                    <Home />
                  </span>
                  <span>
                    <strong>기존 집으로 돌아가기</strong>
                    <small>{m.householdName}</small>
                  </span>
                  <ChevronRight size={21} />
                </Link>
              ))}
              {join && (
                <form className="join-instructions" onSubmit={followInvite}>
                  <p>
                    가족이 보내준 초대 링크를 열어 주세요. 초대 화면에서 직접
                    입주를 수락해야 함께 살 수 있어요.
                  </p>
                  <label>
                    초대 링크
                    <input
                      type="text"
                      inputMode="url"
                      autoComplete="off"
                      value={invite}
                      onChange={(e) => setInvite(e.target.value)}
                      placeholder="https://…/invite/…"
                      required
                    />
                  </label>
                  <button className="button secondary">
                    초대 확인하기 <ArrowRight size={17} />
                  </button>
                </form>
              )}
              <p className="story-footnote">
                작은 집에서 시작되는, 우리의 다정한 일상.
              </p>
            </section>
          </main>
        ) : draft.step === 1 ? (
          <main className="house-step">
            <div className="step-copy">
              <span className="chapter">01 / A PLACE TO CALL OURS</span>
              <h1 ref={heading} tabIndex={-1}>
                우리 집의 기본 정보를
                <br />
                알려주세요
              </h1>
              <p>함께할 일상을 담을 공간이에요.</p>
            </div>
            <figure className="cottage-preview">
              <PixelScene kind="cottage" presetId={draft.presetId}/>
              <figcaption>{draft.name || "우리 집 이름"}</figcaption>
            </figure>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (draft.name.trim()) move(2);
                else setError("집 이름을 입력해 주세요.");
              }}
            >
              <label>
                집 이름
                <input
                  required
                  maxLength={60}
                  value={draft.name}
                  onChange={(e) => change({ name: e.target.value })}
                  autoComplete="off"
                />
              </label>
              <PresetPicker value={draft.presetId} onChange={presetId=>change({presetId})}/>
              <div className="onboarding-actions">
                <button
                  type="button"
                  className="button secondary"
                  onClick={() => move(0)}
                >
                  <ArrowLeft size={17} />
                  이전
                </button>
                <button className="button primary">
                  다음: 캐릭터 꾸미기 <ArrowRight size={18} />
                </button>
              </div>
            </form>
          </main>
        ) : (
          <main className="character-step">
            <section className="dressing-room">
              <div className="character-bubble">
                <h1 ref={heading} tabIndex={-1}>
                  나를 표현할 캐릭터를
                  <br />
                  만들어볼까요?
                </h1>
              </div>
              <PixelScene kind="room" appearance={draft.appearance} presetId={draft.presetId}/>
            </section>
            <form onSubmit={submit}>
              <CharacterChoices value={draft.appearance} onChange={appearance=>change({appearance})}/>
              <label>
                내 캐릭터 이름
                <input
                  required
                  maxLength={30}
                  value={draft.memberName}
                  onChange={(e) => change({ memberName: e.target.value })}
                  autoComplete="nickname"
                />
              </label>
              <div className="onboarding-actions">
                <button
                  type="button"
                  disabled={busy}
                  className="button secondary"
                  onClick={() => move(1)}
                >
                  <ArrowLeft size={17} />
                  이전
                </button>
                <button className="button primary" disabled={busy}>
                  {busy ? "우리 집을 만드는 중…" : "우리 집 만들기"}
                  <ArrowRight size={18} />
                </button>
              </div>
            </form>
          </main>
        )}
        {error && (
          <p className="onboarding-error" role="alert">
            {error}
          </p>
        )}
      </div>
    </div>
  );
}
