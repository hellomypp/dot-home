import { CareAlbum } from "./components/CareAlbum";
import { BookOpen } from "lucide-react";
import { petGroup, petSpecies } from "./lib/pets";
import { AccountSettings } from "./components/AccountSettings";
import { MembershipPanel } from "./components/MembershipPanel";
import { HouseholdExportPanel } from "./components/HouseholdExportPanel";
import { HouseholdDeletionPanel } from "./components/HouseholdDeletionPanel";
import { ShareLink } from "./components/ShareLink";
import { SpaceSettings } from "./components/SpaceSettings";
import type { SpaceEditInbox } from "./components/SpaceEditRequests";
import { NotificationInbox, type NotificationPage } from "./components/NotificationInbox";
import { NotificationSettings } from "./components/NotificationSettings";
import { CharacterChoices } from "./components/CharacterStyleForm";
import { CareProfiles } from "./components/CareProfiles";
import { VisitPage, HousewarmingPanel } from "./components/Housewarming";
import { CareReactions } from "./components/CareReactions";
import { HouseholdRequestsPanel, type PromiseDraft } from "./components/HouseholdRequestsPanel";
import { ExpressionForm } from "./components/ExpressionForm";
import { FamilyCalendar } from "./components/FamilyCalendar";
import { ObjectNotes, type ObjectNote } from "./components/ObjectNotes";
import { Onboarding, OnboardingPreview } from "./onboarding/Onboarding";
export { Onboarding } from "./onboarding/Onboarding";
import { gameAppearance } from "./lib/appearance";
import { clearDrafts } from "./onboarding/draft";
import {
  lazy,
  Suspense,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import {
  Link,
  Navigate,
  Route,
  Routes,
  useNavigate,
  useParams,
  useLocation,
} from "react-router-dom";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  CalendarDays,
  ArrowLeft,
  ArrowRight,
  Bell,
  Check,
  ChevronRight,
  CircleHelp,
  PawPrint,
  ZoomIn,
  ZoomOut,
  Coffee,
  DoorOpen,
  Home,
  Leaf,
  LogOut,
  MessageCircle,
  Package,
  Pickaxe,
  GraduationCap,
  Plus,
  Refrigerator,
  Send,
  Settings2,
  Sun,
  Users,
  Utensils,
  X,
} from "lucide-react";
import { SessionBar } from "./components/SessionBar";
import { FormerNotesPanel } from "./components/FormerNotesPanel";
import { Portrait } from "./components/Portrait";
import { Modal } from "./components/Modal";
const WorldCanvas = lazy(() =>
  import("./components/WorldCanvas").then((module) => ({
    default: module.WorldCanvas,
  })),
);
import {
  api,
  ApiError,
  login,
  logout,
  places,
  statusText,
  activeExpression,
  type Identity,
  type Snapshot,
  type PantryItem,
} from "./lib/api";
import {
  safeInvitePath,
  googleLoginUrl,
  localDevLoginVisible,
  type AuthConfig,
} from "./lib/auth";
import { connectWorld, type ConnectionState } from "./lib/realtime";
import { homeDestination, rememberHome } from "./lib/lastHome";
import { currentSharedPlace, sharedPlaceLabel, sharedPlaces } from "./lib/expressions";
import { useWindowSky } from "./lib/windowWeather";
import { useForegroundLocation } from "./lib/foregroundLocation";
import { ForegroundLocationSettings } from "./components/ForegroundLocationSettings";
import type { Place, WorldEvent, WorldView } from "./game/types";

const now = new Date().toISOString();
const fixture: Snapshot = {
  household: { id: "studio", name: "느린 오후의 집", ownerId: "a" },
  meMemberId: "a",
  revision: 0,
  serverTime: now,
  members: [
    {
      id: "a",
      userId: "a",
      name: "하늘",
      appearance: "sage",
      role: "OWNER",
      place: "home",
      mood: "편안해요",
      food: "따뜻한 수프",
      thought: "오늘은 조금 느긋하게",
      statusExpiresAt: null,
      statusUpdatedAt: now,
      x: 306,
      y: 318,
      action: "idle",
    },
    {
      id: "b",
      userId: "b",
      name: "서윤",
      appearance: "rose",
      role: "MEMBER",
      place: "home",
      mood: "좋아요",
      food: "",
      thought: "같이 차 마실래?",
      statusExpiresAt: null,
      statusUpdatedAt: now,
      x: 430,
      y: 326,
      action: "idle",
    },
  ],
  pets: [{ id: "p", name: "모카", kind: "cat", appearance: "cream" }],
  pantryItems: [
    {
      id: "milk",
      name: "우유",
      location: "냉장",
      quantity: 2,
      unit: "개",
      expiresOn: null,
      version: 0,
      updatedAt: now,
    },
    {
      id: "egg",
      name: "달걀",
      location: "냉장",
      quantity: 6,
      unit: "개",
      expiresOn: null,
      version: 0,
      updatedAt: now,
    },
  ],
  events: [],
};
const eventTypes: Record<
  string,
  { label: string; icon: ReactNode; message: string }
> = {
  ARRIVED_HOME: {
    label: "귀가",
    icon: <Home size={18} />,
    message: "집에 도착했어요",
  },
  LEFT_WORK: {
    label: "퇴근",
    icon: <DoorOpen size={18} />,
    message: "오늘 일을 마쳤어요",
  },
  PARCEL_ARRIVED: {
    label: "택배",
    icon: <Package size={18} />,
    message: "택배가 도착했어요",
  },
  NOTE: { label: "한마디", icon: <MessageCircle size={18} />, message: "" },
};

function Brand() {
  return (
    <Link to="/" className="brand">
      <span className="brand-mark">
        <Home size={22} />
      </span>
      <span>
        도트홈<small>OUR LITTLE EVERYDAY</small>
      </span>
    </Link>
  );
}
function ErrorMessage({ message }: { message: string }) {
  return message ? (
    <p className="error-message" role="alert">
      {message}
    </p>
  ) : null;
}
function Loading() {
  return (
    <div className="loading-page">
      <Brand />
      <span className="loading-dot" />
      <p>우리 집으로 가는 중이에요.</p>
    </div>
  );
}
function BackLink() {
  return (
    <Link className="back-link" to="/">
      <ArrowLeft size={16} /> 처음으로
    </Link>
  );
}
export function LoginPage() {
  const nav = useNavigate(),
    location = useLocation(),
    qc = useQueryClient();
  const identity = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Identity>("/api/me"),
    retry: false,
    refetchOnMount: "always",
  });
  const [key, setKey] = useState(""),
    [name, setName] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const auth = useQuery({
    queryKey: ["auth-config"],
    queryFn: () => api<AuthConfig>("/api/auth/config"),
    retry: false,
  });
  const params = new URLSearchParams(location.search);
  const next = safeInvitePath(params.get("next"));
  const authError = params.has("error")
    ? "Google 로그인을 완료하지 못했어요. 취소했다면 다시 시작해 주세요."
    : "";
  if (identity.isPending || identity.isFetching) return <Loading />;
  if (identity.data && !identity.error) return <Navigate to={next ?? homeDestination(identity.data)} replace />;
  async function enter(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const me = await login(key, name);
      qc.setQueryData(["me"], me);
      nav(next ?? homeDestination(me), { replace: true });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="welcome-page">
      <header>
        <Brand />
        {import.meta.env.DEV && (
          <Link to="/studio" className="text-link">
            아트 시안 둘러보기 <ArrowRight size={16} />
          </Link>
        )}
      </header>
      <main className="welcome-layout">
        <section className="welcome-copy">
          <span className="eyebrow">
            <Leaf size={14} /> 일상에 작은 다정함을
          </span>
          <h1>
            각자의 하루가
            <br />
            모이는 작은 집.
          </h1>
          <p>
            함께 사는 사람들과 반려동물,
            <br />
            말하지 못한 오늘의 기분까지.
            <br />
            우리의 일상을 도트홈에서 만나보세요.
          </p>
          {import.meta.env.DEV && (
            <Link className="button secondary" to="/studio">
              움직이는 집 구경하기 <ArrowRight size={17} />
            </Link>
          )}
          <div className="welcome-note">
            <span>01</span> 집을 만들고 <i>·</i>
            <span>02</span> 함께할 사람을 초대해요
          </div>
        </section>
        <section className="entry-card">
          <div className="entry-illustration">
            <Home size={48} strokeWidth={1} />
            <span className="mini-leaf">
              <Leaf size={26} />
            </span>
          </div>
          <span className="eyebrow">WELCOME HOME</span>
          <h2>우리 집에 들어가기</h2>
          <p>인증한 계정으로 나의 캐릭터를 만나요.</p>
          {auth.data?.googleEnabled ? (
            <a
              className="button primary google-login"
              href={googleLoginUrl(next)}
              onClick={() => setBusy(true)}
            >
              {busy ? "Google 로그인으로 이동 중…" : "Google로 시작하기"}
            </a>
          ) : (
            <>
              <button className="button primary google-login" disabled>
                Google로 시작하기
              </button>
              <div className="provider-note">
                {auth.isPending
                  ? "로그인 설정을 확인하고 있어요."
                  : "Google 로그인 연결을 준비하고 있어요. 잠시 후 다시 시도해 주세요."}
              </div>
              {!auth.isPending && (
                <button
                  className="button secondary"
                  onClick={() => void auth.refetch()}
                >
                  로그인 연결 다시 확인
                </button>
              )}
            </>
          )}

          {localDevLoginVisible(
            window.location.hostname,
            import.meta.env.DEV,
            auth.data?.devEnabled === true,
          ) && (
            <form onSubmit={enter} className="login-form">
              <div className="dev-label">로컬 개발용 로그인</div>
              <label>
                계정 키
                <input
                  required
                  value={key}
                  onChange={(e) => setKey(e.target.value)}
                  placeholder="예: haneul-test"
                  autoComplete="username"
                  pattern="[A-Za-z0-9_-]{2,64}"
                  title="영문·숫자·밑줄·하이픈 2~64자"
                  maxLength={64}
                />
              </label>
              <label>
                내 이름
                <input
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="집에서 부를 이름"
                  maxLength={30}
                  autoComplete="nickname"
                />
              </label>
              <button className="button primary" disabled={busy}>
                {busy ? "들어가는 중…" : "시작하기"}
                <ArrowRight size={17} />
              </button>
              <small className="muted">
                비밀번호가 없는 로컬 검증용 인증입니다. 실제 계정 정보를
                입력하지 마세요.
              </small>
            </form>
          )}
          <ErrorMessage message={error || authError} />
          {identity.error &&
            !(
              identity.error instanceof ApiError &&
              identity.error.status === 401
            ) && (
              <p className="muted">
                개발 서버 연결 대기 중 · 아트 시안은 바로 볼 수 있어요.
              </p>
            )}
        </section>
      </main>
      <footer className="welcome-footer">
        서두르지 않아도 괜찮은, 우리의 작은 세계.
        <span>우리의 작은 일상 · 도트홈</span>
      </footer>
    </div>
  );
}

function HomeSelectionPage() {
  const notice = (useLocation().state as { membershipNotice?: string } | null)?.membershipNotice;
  const identity = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Identity>("/api/me"),
    retry: false,
    refetchOnMount: "always",
  });
  if (identity.isPending || identity.isFetching) return <Loading />;
  if (identity.error instanceof ApiError && identity.error.status === 401)
    return <Navigate to="/login" replace />;
  if (!identity.error && identity.data && !identity.data.memberships.length && !notice)
    return <Navigate to="/onboarding" replace />;

  return (
    <div className="form-page">
      <div className="onboard-card home-selection">
        <header>
          <Brand />
          {identity.data && !identity.error && <SessionBar name={identity.data.user.name} />}
        </header>
        <h1>어느 집으로 갈까요?</h1>
        {notice && <p role="status">{notice}</p>}
        {identity.error ? (
          <>
            <ErrorMessage message={identity.error.message} />
            <button className="button secondary" onClick={() => void identity.refetch()}>
              다시 확인
            </button>
          </>
        ) : (
          <>
            <ul>
              {identity.data?.memberships.map((home) => (
                <li key={home.householdId}>
                  <Link to={"/home/" + encodeURIComponent(home.householdId)} className="button secondary">
                    <Home size={18} aria-hidden="true" />
                    <span>{home.householdName}</span>
                    <small>{home.role === "OWNER" ? "집 대표" : "가족"}</small>
                  </Link>
                </li>
              ))}
            </ul>
            <Link to="/onboarding" className="button primary">새 집 만들기 또는 초대받기</Link>
          </>
        )}
      </div>
    </div>
  );
}

function AccountPage() {
  const identity = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Identity>("/api/me"),
    retry: false,
    refetchOnMount: "always",
  });
  if (identity.isPending || identity.isFetching) return <Loading />;
  if (identity.error instanceof ApiError && identity.error.status === 401)
    return <Navigate to="/login" replace />;
  return (
    <main className="form-page">
      <div className="onboard-card home-selection former-records-page">
        <header>
          <Brand />
          {identity.data && !identity.error && <SessionBar name={identity.data.user.name} />}
        </header>
        <h1>이전 집에 남긴 내 기록</h1>
        {identity.error ? (
          <>
            <ErrorMessage message={identity.error.message} />
            <button className="button secondary" onClick={() => void identity.refetch()}>다시 확인</button>
          </>
        ) : identity.data && <FormerNotesPanel user={identity.data.user.id} />}
        <Link className="button secondary" to={identity.data?.memberships.length ? "/homes" : "/onboarding"}>
          {identity.data?.memberships.length ? "집 선택으로" : "시작 화면으로"}
        </Link>
      </div>
    </main>
  );
}

function InvitePage() {
  const { token } = useParams(),
    nav = useNavigate(),
    client = useQueryClient();
  const [name, setName] = useState(""),
    [appearance, setAppearance] = useState("v2:adult_male"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const invite = useQuery({
    queryKey: ["invite", token],
    queryFn: () =>
      api<{ householdName: string; expiresAt: string }>(
        `/api/invitations/${encodeURIComponent(token!)}`,
      ),
    retry: false,
  });
  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api<Identity>("/api/me"),
    retry: false,
  });
  async function accept(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const data = await api<Snapshot>(
        `/api/invitations/${encodeURIComponent(token!)}/accept`,
        "POST",
        { memberName: name || me.data?.user.name, appearance },
      );
      void client.invalidateQueries({ queryKey: ["me"] });
      nav(`/home/${data.household.id}`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="form-page">
      <Brand />
      <div className="onboard-card invite-card">
        {me.data && <SessionBar name={me.data.user.name} />}
        <span className="invite-art">
          <DoorOpen size={42} />
        </span>
        <span className="eyebrow">YOU'RE INVITED</span>
        <h1>
          {invite.data
            ? `${invite.data.householdName}에 초대받았어요.`
            : invite.error
              ? "초대를 사용할 수 없어요."
              : "초대를 확인하고 있어요."}
        </h1>
        <p>
          인증한 뒤 직접 수락해야 집에 입주해요.
          <br />
          링크를 열기만 해서는 캐릭터가 생기지 않아요.
        </p>
        <ErrorMessage message={invite.error?.message || error} />
        {invite.data &&
          (me.data ? (
            <form onSubmit={accept}>
              <label>
                집에서 부를 내 이름
                <input
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={30}
                  placeholder={me.data.user.name}
                />
              </label>
              <CharacterChoices
                value={appearance}
                onChange={setAppearance}
                disabled={busy}
              />
              <button className="button primary" disabled={busy}>
                초대 수락하고 입주하기 <ArrowRight size={17} />
              </button>
            </form>
          ) : (
            <Link
              className="button primary"
              to={`/login?next=${encodeURIComponent(`/invite/${token}`)}`}
            >
              로그인하고 초대 확인하기
            </Link>
          ))}
        <BackLink />
      </div>
    </div>
  );
}

type Panel =
  | "story"
  | "pantry"
  | "event"
  | "invite"
  | "settings"
  | "calendar"
  | "housewarming"
  | "pets"
  | "album"
  | "membership"
  | "exports"
  | "deletion"
  | "design"
  | "notifications"
  | "notification-settings"
  | null;
function HouseholdRoute() {
  const { id } = useParams();
  // A new household owns a new canvas, socket and set of form drafts.
  return <GamePage key={id} />;
}

function GamePage({ studio = false }: { studio?: boolean }) {
  const { search } = useLocation();
  const { id } = useParams(),
    nav = useNavigate(),
    qc = useQueryClient();
  const query = useQuery({
    queryKey: ["world", id],
    queryFn: () => api<Snapshot>(`/api/households/${id}/world`),
    enabled: !studio,
    retry: false,
    refetchInterval: 30_000,
    gcTime: 0,
  });
  const [promise, setPromise] = useState<PromiseDraft>();
  const [activityTab, setActivityTab] = useState<"records" | "requests">("records");
  const [fridgeTab, setFridgeTab] = useState<"food" | "notes">("food");
  const attachedNotes = useQuery({
    queryKey: ["object-notes", id, false],
    queryFn: () =>
      api<ObjectNote[]>(`/api/households/${id}/objects/fridge/notes`),
    enabled: !studio && Boolean(query.data),
  });
  const [sample, setSample] = useState(fixture),
    [observed, setObserved] = useState(""),
    [panel, setPanel] = useState<Panel>(() =>
      !studio && new URLSearchParams(search).get("notifications") === "1" ? "notifications" : null),
    [connection, setConnection] = useState<ConnectionState>("connecting"),
    [activity, setActivity] = useState("잠깐 쉬어가도 좋은 오후예요."),
    [error, setError] = useState(""),
    [toast, setToast] = useState(""),
    [palette, setPalette] = useState<"warm" | "dusk">("warm"),
    [closeView, setCloseView] = useState(false),
    [reduced, setReduced] = useState(() => {
      try {
        const saved = localStorage.getItem("dot-home:reduced-motion");
        if (saved !== null) return saved === "true";
      } catch {}
      return (
        window.matchMedia?.("(prefers-reduced-motion: reduce)").matches || false
      );
    });
  useEffect(() => {
    try {
      localStorage.setItem("dot-home:reduced-motion", String(reduced));
    } catch {}
  }, [reduced]);
  const [scenePreview, setScenePreview] = useState<{ text: string; duration: number } | null>(null);
  useEffect(() => {
    if (!scenePreview) return;
    const timer = window.setTimeout(() => setScenePreview(null), scenePreview.duration);
    return () => window.clearTimeout(timer);
  }, [scenePreview]);
  const [activityPending, setActivityPending] = useState(false);
  const [tick, setTick] = useState(0);
  const live = useRef<ReturnType<typeof connectWorld> | null>(null);
  const snapshot = studio ? sample : query.data,
    own = snapshot?.members.find((m) => m.id === snapshot.meMemberId);
  const observedMember = snapshot?.members.find((m) => m.id === observed) ?? own;
  const accessStatus = query.error instanceof ApiError ? query.error.status : 0;
  const accessLost = accessStatus === 401 || accessStatus === 403 || accessStatus === 404;
  const editAccess = useQuery({
    queryKey: ["space-edit-access", id],
    queryFn: () => api<SpaceEditInbox>(`/api/households/${id}/edit-requests`),
    enabled: Boolean(!studio && id && own && !accessLost && !snapshot?.household.pendingDeleteUntil),
    refetchInterval: 30_000,
  });
  const notificationQuery = useInfiniteQuery({
    queryKey: ["notifications", id],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({ householdId: id ?? "" });
      if (pageParam) params.set("cursor", pageParam);
      return api<NotificationPage>(`/api/notifications?${params}`);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
    enabled: Boolean(!studio && id && own && !accessLost),
    refetchInterval: 30_000,
  });
  const unread = notificationQuery.data?.pages[0]?.unreadCount ?? 0;
  useEffect(() => {
    if (!studio && accessLost)
      qc.removeQueries({ predicate: (entry) => entry.queryKey[1] === id && entry.queryKey[0] !== "world" });
  }, [studio, accessLost, id, qc]);
  const generalSharing = Boolean(own?.sharedPlace && own.sharedPlace.status !== "UNSHARED");
  const foregroundLocation = useForegroundLocation(id, own?.userId,
    Boolean(!studio && !accessLost && !snapshot?.household.pendingDeleteUntil), panel === "story", generalSharing);
  const observedPresence = currentSharedPlace(observedMember?.sharedPlace);
  const activityScene = observedMember?.place === "office" && observedMember.action === "working" ||
    observedMember?.place === "school" && observedMember.action === "studying";
  const sharedActivity = activityScene && observedPresence?.status !== "UNSHARED";
  const neutral = observedMember?.id !== own?.id &&
    Boolean(observedPresence && observedPresence.status !== "MANUAL" && observedPresence.status !== "AUTO" && !sharedActivity);
  const windowSky = useWindowSky(!accessLost && !neutral && observedMember?.place === "home");
  useEffect(() => {
    if (!studio && !query.isError && query.data && own)
      rememberHome(own.userId, query.data.household.id);
  }, [studio, query.isError, query.data?.household.id, own?.userId]);
  useEffect(() => {
    const interval = setInterval(() => setTick((v) => v + 1), 30_000);
    return () => clearInterval(interval);
  }, []);
  useEffect(() => {
    if (own) setObserved(own.id);
  }, [own?.id]);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(""), 4200);
    return () => clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    if (studio || !id || !query.data || accessLost) return;
    let active = true;
    live.current = connectWorld(
      id,
      async () => {
        const { data } = await query.refetch({ throwOnError: true });
        if (!active || !data) return 0;
        await Promise.all([
          qc.invalidateQueries({ queryKey: ["object-notes", id] }),
          qc.invalidateQueries({ queryKey: ["calendar", id] }),
          qc.invalidateQueries({ queryKey: ["household-requests", id] }),
          qc.invalidateQueries({ queryKey: ["household-reactions", id] }),
          qc.invalidateQueries({ queryKey: ["promise-source", id] }),
          qc.invalidateQueries({ queryKey: ["care-rules", id] }),
          qc.invalidateQueries({ queryKey: ["care-album", id] }),
          qc.invalidateQueries({ queryKey: ["ownership-transfers", id] }),
          qc.invalidateQueries({ queryKey: ["space-edit-access", id] }),
          qc.invalidateQueries({ queryKey: ["notifications", id] }),
        ]);
        return data.revision;
      },
      setConnection,
      setError,
    );
    return () => {
      active = false;
      live.current?.destroy();
      live.current = null;
    };
  }, [id, studio, Boolean(query.data), accessLost]);
  async function refresh() {
    if (!studio) await query.refetch();
  }
  async function changeActivity(place: "office" | "school") {
    if (!snapshot || !own || activityPending) return;
    const starting = own.place !== place;
    setActivityPending(true);
    try {
      if (studio) {
        setSample({ ...snapshot, members: snapshot.members.map((member) =>
          member.id === own.id ? { ...member, place: starting ? place : "home" } : member) });
      } else {
        const endpoint = place === "office" ? "work-shift" : "school-day";
        const payload = place === "office" ? { working: starting } : { attending: starting };
        const data = await api<Snapshot>(`/api/households/${snapshot.household.id}/me/${endpoint}`, "PATCH", payload);
        qc.setQueryData(["world", id], data);
      }
      setObserved(own.id);
      setScenePreview(null);
    } catch (failure) {
      setError((failure as Error).message);
    } finally {
      setActivityPending(false);
    }
  }
  async function switchHome() {
    try {
      await foregroundLocation.disable();
      nav("/homes");
    } catch (error) {
      setError((error as Error).message);
    }
  }
  async function leave() {
    try {
      live.current?.destroy();
      await logout();
      clearDrafts();
      qc.clear();
      nav("/login");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  function event(e: WorldEvent) {
    if (e.type === "activity") setActivity(e.text);
    if (e.type === "reveal-name") setScenePreview({ text: e.name, duration: 3000 });
    if (e.type === "reveal-speech") setScenePreview({ text: e.text, duration: 6000 });
    if (e.type === "interact") {
      if (e.target === "fridge") {
        setFridgeTab(e.label === "냉장고 쪽지" ? "notes" : "food");
        setPanel("pantry");
      } else if (e.target === "calendar") setPanel("calendar");
      else if (e.target === "entrance") setPanel("invite");
      else if (e.target === "pet_rest") setPanel("pets");
      else if (e.target === "bookshelf") setPanel("album");
      else if (e.target === "table") setPanel("story");
      else
        setToast(
          e.target === "pet" ? "반려동물의 움직임은 가상 연출이에요." : e.label,
        );
    }
    if (e.type === "visibility-resume") void refresh();
    if (e.type === "move-intent" && !studio && own?.place !== "office" && own?.place !== "school" && !snapshot?.household.pendingDeleteUntil)
      live.current?.send({
        requestId: crypto.randomUUID(),
        kind: "MOVE_INTENT",
        scene: own?.place,
        x: e.x,
        y: e.y,
      });
  }
  if (!studio && accessLost)
    return <Navigate to="/homes" replace state={accessStatus === 403 ? {
      membershipNotice: "이 집에 접근할 수 없어요. 참여 중인 다른 집을 선택해 주세요.",
    } : undefined} />;
  if (!snapshot || !own || !observedMember) {
    if (query.error)
      return (
        <div className="form-page">
          <Brand />
          <div className="onboard-card">
            <h1>집에 들어갈 수 없어요.</h1>
            <ErrorMessage message={query.error.message} />
            <button
              className="button secondary"
              onClick={() => void query.refetch()}
            >
              다시 연결
            </button>
            <Link to="/onboarding" className="button primary">
              새로운 시작 화면으로
            </Link>
          </div>
        </div>
      );
    return <Loading />;
  }
  const place = observedMember.place;
  const view: WorldView = {
    readOnly: Boolean(snapshot.household.pendingDeleteUntil),
    closeView,
    windowSky,
    design: snapshot.design,
    objectNotes: attachedNotes.data?.map((n) => ({
      id: n.id,
      objectId: n.objectId,
    })),
    place,
    ownMemberId: own.id,
    observedMemberId: observedMember.id,
    members: snapshot.members.map((m) => ({
      id: m.id,
      name: m.name,
      place: m.place,
      characterStyle: m.appearance,
      appearance: gameAppearance(m.appearance),
      status: statusText(m),
      ...(!studio ? { x: m.x, y: m.y, action: m.action } : {}),
    })),
    pets: snapshot.pets.map((p) => ({ ...p, color: "#c79765" })),
    palette,
    reducedMotion: reduced,
  };
  void tick;
  return (
    <div className="game-app">
      <header className="app-header">
        <Brand />
        <div className="header-center">
          <span className="tiny-star">✧</span> 오늘도, 우리답게.
        </div>
        <div className="account-menu">
          <span className={`connection ${connection}`}>
            <i />
            {studio
              ? "아트 스튜디오"
              : connection === "online"
                ? "연결됨"
                : connection === "connecting"
                  ? "연결 중"
                  : "다시 연결 중"}
          </span>
          {!studio && (
            <button className="notification-button" type="button"
              aria-label={unread ? `알림함 · 읽지 않은 알림 ${unread}개` : "알림함"}
              onClick={() => setPanel("notifications")}>
              <Bell size={20} aria-hidden="true" />
              {unread > 0 && <span aria-hidden="true">{unread > 99 ? "99+" : unread}</span>}
            </button>
          )}
          <button
            className="account-button"
            onClick={() => setPanel("settings")}
            aria-label="내 계정과 보기 설정"
          >
            <Portrait appearance={own.appearance} small />
            <span>
              <span className="account-name-text">{own.name}</span>
              <small>내 계정</small>
            </span>
          </button>
        </div>
      </header>
      {studio && (
        <div className="studio-strip">
          <span>
            <span className="studio-dot" /> M0 아트 시안 · 두 사람과 반려동물은
            임시 데이터예요. 변경은 저장되지 않아요.
          </span>
          <Link to="/login">
            실제 우리 집 만들기 <ArrowRight size={14} />
          </Link>
        </div>
      )}
      {snapshot.household.pendingDeleteUntil && (
        <div className="deletion-banner" role="status">
          <p>이 집은 삭제 대기 중이에요. 공동 기록을 보고 내려받을 수 있어요.</p>
          <button className="button secondary" onClick={() => setPanel("exports")}>기록 내보내기</button>
          {own.role === "OWNER" && <button className="button secondary" onClick={() => setPanel("deletion")}>삭제 대기 관리</button>}
        </div>
      )}
      <div className="game-layout">
        <aside className="family-sidebar">
          <div className="house-label">
            <span className="eyebrow">OUR HOME</span>
            <h2>{snapshot.household.name}</h2>
            <span className="house-meta">
              함께 사는 사람 {snapshot.members.length}명 · 반려동물{" "}
              {snapshot.pets.length}마리
            </span>
          </div>
          <div className="sidebar-heading">
            함께하는 사람들 <span>{snapshot.members.length}</span>
          </div>
          <div className="member-list">
            {snapshot.members.map((m) => (
              <button
                key={m.id}
                className={`member-card ${observed === m.id ? "active" : ""}`}
                onClick={(event) => {
                  setObserved(m.id);
                  const name = event.currentTarget.querySelector(".member-name-text");
                  setScenePreview(name && name.scrollWidth > name.clientWidth ? { text: m.name, duration: 3000 } : null);
                }}
              >
                <Portrait appearance={m.appearance} />
                <span className="member-description">
                  <strong>
                    <span className="member-name-text">{m.name}</span>
                    {m.id === own.id && <em>나</em>}
                  </strong>
                  <small>{m.place === "office" && (m.id === own.id || m.sharedPlace?.status !== "UNSHARED")
                    ? "근무중" : m.place === "school" && (m.id === own.id || m.sharedPlace?.status !== "UNSHARED")
                    ? "수업중"
                    : sharedPlaceLabel(m.sharedPlace, places[m.place])}</small>
                </span>
                <ChevronRight size={15} />
              </button>
            ))}
          </div>
          <p className="observation-hint">
            얼굴을 누르면 그 사람의
            <br />
            가상 공간을 둘러볼 수 있어요.
          </p>
          <div className="sidebar-tools">
            <button
              className="button secondary family-invite-compact"
              onClick={() => setPanel("invite")}
            >
              <Users size={16} />
              가족 초대
            </button>
            <button
              className="button secondary sidebar-pet"
              aria-label="아기 · 반려동물 관리"
              onClick={() => setPanel("pets")}
            >
              <PawPrint size={16} />
              <span>돌봄</span>
            </button>
          </div>
          {snapshot.pets.length > 0 && (
            <>
              <div className="sidebar-heading pets-heading">작은 동거인들</div>
              {snapshot.pets.map((p) => (
                <div className="pet-card" key={p.id}>
                  <span className="pet-face">
                    {petSpecies[petGroup(p)].icon}
                  </span>
                  <span>
                    <strong>{p.name}</strong>
                    <small>집에서 쉬는 중 · 가상 연출</small>
                  </span>
                </div>
              ))}
            </>
          )}
          <button className="invite-tile" onClick={() => setPanel("invite")}>
            <span className="invite-plus">
              <Plus size={17} />
            </span>
            <span>
              우리 집에 초대하기
              <small>현재 가족 {snapshot.members.length}명</small>
            </span>
            <ChevronRight size={16} />
          </button>
          <div className="sidebar-footer">
            <Leaf size={18} />
            <p>
              특별한 일 없는 하루도
              <br />
              함께라서 좋아요.
            </p>
          </div>
        </aside>
        <main className="game-main">
          <div className="page-heading">
            <div>
              <span className="eyebrow">A LITTLE MOMENT TOGETHER</span>
              <h1>
                {observed !== own.id
                  ? `${observedMember.name}의 공간`
                  : snapshot.household.name}
              </h1>
              <p>
                {observed !== own.id
                  ? "둘러보는 중이에요. 내 계정과 작성자는 바뀌지 않아요."
                  : "작은 일상을 나누고, 각자의 하루를 함께해요."}
              </p>
            </div>
            <div className="day-chip">
              <Sun size={22} />
              <span>
                {new Date().toLocaleDateString("ko-KR", {
                  month: "long",
                  day: "numeric",
                  weekday: "short",
                })}
                <small>우리의 일상이 쌓이는 곳</small>
              </span>
            </div>
          </div>
          <div className="main-columns">
            <section className="world-section">
              {!snapshot.household.pendingDeleteUntil && (
                <div className="work-shift">
                  {place === "office" && !neutral && <span>달빛 광산 · 근무중</span>}
                  {place === "school" && !neutral && <span>햇살 교실 · 수업중</span>}
                  <div className="activity-actions">
                    {own.place !== "school" && (
                      <button className="button secondary" type="button" disabled={activityPending} onClick={() => void changeActivity("office")}>
                        <Pickaxe size={15} aria-hidden="true" /> {own.place === "office" ? "퇴근" : "출근"}
                      </button>
                    )}
                    {own.place !== "office" && (
                      <button className="button secondary" type="button" disabled={activityPending} onClick={() => void changeActivity("school")}>
                        <GraduationCap size={16} aria-hidden="true" /> {own.place === "school" ? "하교" : "등교"}
                      </button>
                    )}
                  </div>
                </div>
              )}
              <div className={`scene-card${place === "home" && !neutral ? " home-scene-card" : ""}`}>
                <div className={`scene-wrap ${palette}`}>
                  {scenePreview && <div className="scene-name-preview" role="status">{scenePreview.text}</div>}
                  {neutral ? (
                    <div className="neutral-scene">
                      <Leaf size={36} strokeWidth={1.4} aria-hidden="true" />
                      <strong>{observedPresence?.status === "UNSHARED" ? "위치 미공유" : "현재 장소 미확인"}</strong>
                      <p>{observedMember.name}님의 현재 장소는 표시되지 않아요.</p>
                      {observedPresence?.lastPlace && observedPresence.sharedAt && (
                        <small>마지막 공유 · {sharedPlaces[observedPresence.lastPlace]} · {new Date(observedPresence.sharedAt).toLocaleString("ko-KR")}</small>
                      )}
                    </div>
                  ) : (
                    <>
                      <span className="scene-caption" aria-hidden="true">
                        <i /> {place === "office" ? "달빛 광산" : place === "school" ? "햇살 교실" : observedPresence?.place ? sharedPlaces[observedPresence.place] : places[place]}
                      </span>
                      <Suspense
                        fallback={
                          <div className="world-canvas" role="status">
                            우리 집을 그리고 있어요…
                          </div>
                        }
                      >
                        <WorldCanvas view={view} onEvent={event} />
                      </Suspense>
                    </>
                  )}
                  {(place === "home" || observedMember.id !== own.id) && (
                    <div className="scene-actions" role="group" aria-label="장면 조작">
                      {observedMember.id !== own.id && (
                        <button
                          className="scene-control"
                          type="button"
                          aria-label="내 화면으로"
                          title="내 화면으로"
                          onClick={() => setObserved(own.id)}
                        >
                          <Home size={20} aria-hidden="true" />
                        </button>
                      )}
                      {place === "home" && !neutral && (
                        <button className="scene-control" type="button" aria-label="책장 추억 앨범"
                          title="책장 추억 앨범" onClick={() => setPanel("album")}>
                          <BookOpen size={19} aria-hidden="true" />
                        </button>
                      )}
                      {place === "home" && !neutral && (
                        <button
                          className="scene-control scene-zoom"
                          type="button"
                          aria-label="2배 확대"
                          aria-pressed={closeView}
                          title={closeView ? "전체 집 보기" : "2배 확대"}
                          onClick={() => setCloseView((zoomed) => !zoomed)}
                        >
                          {closeView ? (
                            <ZoomOut size={21} aria-hidden="true" />
                          ) : (
                            <ZoomIn size={21} aria-hidden="true" />
                          )}
                          <span aria-hidden="true">{closeView ? "2×" : "1×"}</span>
                        </button>
                      )}
                    </div>
                  )}
                </div>
              </div>
              <div className="action-bar">
                <button onClick={() => setPanel("calendar")}>
                  <span className="action-icon">
                    <CalendarDays size={20} />
                  </span>
                  <span>
                    <strong>가족 달력</strong>
                    <small>함께할 하루</small>
                  </span>
                </button>
                <button onClick={() => setPanel("story")}>
                  <span className="action-icon peach">
                    <MessageCircle size={22} />
                  </span>
                  <span>
                    <strong>내 이야기</strong>
                    <small>지금 내 마음은</small>
                  </span>
                  <ChevronRight size={16} />
                </button>
                <button onClick={() => setPanel("pantry")}>
                  <span className="action-icon green">
                    <Refrigerator size={22} />
                  </span>
                  <span>
                    <strong>우리 집 냉장고</strong>
                    <small>함께 채우는 일상</small>
                  </span>
                  <ChevronRight size={16} />
                </button>
                <button onClick={() => setPanel("event")}>
                  <span className="action-icon sand">
                    <Bell size={22} />
                  </span>
                  <span>
                    <strong>생활 기록</strong>
                    <small>소식과 부탁 나누기</small>
                  </span>
                  <ChevronRight size={16} />
                </button>
              </div>
              <div className="gentle-tip">
                <CircleHelp size={15} />
                <span>
                  가족의 얼굴을 누르면 그 가족의 모습을 볼 수 있어요.
                </span>
                <button
                  onClick={() => setPanel("settings")}
                  aria-label="보기 설정"
                >
                  <Settings2 size={16} />
                </button>
              </div>
            </section>
            <aside className="daily-panel">
              <section className="daily-card">
                <div className="card-heading">
                  <h2>오늘의 마음</h2>
                  <span className="small-leaf">
                    <Leaf size={16} />
                  </span>
                </div>
                {snapshot.members.map((member) => {
                  const expression = activeExpression(member);
                  const text = statusText(member);
                  const sourceId = expression ? `${member.id}.${expression.field}.${expression.version}` : "";
                  return (
                    <div className="thought-row" key={member.id}>
                      <Portrait appearance={member.appearance} small />
                      <div>
                        <strong>{member.name} <span>{member.id === own.id ? "나" : ""}</span></strong>
                        <p>{text || "아직 남긴 이야기가 없어요."}</p>
                        <small>{text ? "직접 공유한 이야기" : "기다리는 마음도 다정하게"}</small>
                        {!studio && expression && sourceId && (
                          <CareReactions key={sourceId} householdId={snapshot.household.id} sourceType="EXPRESSION"
                            sourceId={sourceId} meal={expression.field === "food"}
                            onPromise={member.id === own.id ? undefined : () => {
                              setPromise({
                                sourceType: "EXPRESSION", sourceId,
                                title: expression.field === "food" ? "함께 먹을 음식 준비하기" : "가족을 위해 내가 할 일",
                              });
                              setActivityTab("requests");
                              setPanel("event");
                            }} />
                        )}
                      </div>
                    </div>
                  );
                })}
                <button
                  className="soft-button"
                  onClick={() => setPanel("story")}
                >
                  <Plus size={15} /> 내 마음 남기기
                </button>
              </section>
              <section className="daily-card news-card">
                <div className="card-heading">
                  <h2>우리 집 소식</h2>
                  <button
                    aria-label="생활 소식 추가"
                    onClick={() => setPanel("event")}
                  >
                    <Plus size={18} />
                  </button>
                </div>
                {snapshot.events.length ? (
                  snapshot.events.slice(0, 5).map((e) => (
                    <div className="news-item" key={e.id}>
                      <span className="news-icon">
                        {eventTypes[e.type]?.icon || (
                          <MessageCircle size={16} />
                        )}
                      </span>
                      <div>
                        <p>{e.message}</p>
                        <small>
                          {e.authorName} ·{" "}
                          {new Date(e.createdAt).toLocaleTimeString("ko-KR", {
                            hour: "2-digit",
                            minute: "2-digit",
                          })}
                        </small>
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="empty-news">
                    <span>
                      <Coffee size={28} strokeWidth={1.3} />
                    </span>
                    <p>아직은 조용한 우리 집</p>
                    <small>
                      귀가, 퇴근, 택배 소식을
                      <br />
                      가볍게 남겨보세요.
                    </small>
                  </div>
                )}
                <button
                  className="soft-button"
                  onClick={() => setPanel("event")}
                >
                  소식 남기기 <ArrowRight size={14} />
                </button>
              </section>
              <div className="aside-note">
                <span>✧</span>
                <p>
                  작은 안부 하나가
                  <br />
                  하루를 따뜻하게 만들어요.
                </p>
              </div>
            </aside>
          </div>
          <footer className="game-footer">
            <span>
              DOT HOME <i>·</i> 우리의 작은 일상
              {place === "home" && !neutral && (
                <>
                  <i>·</i>
                  {windowSky.weather ? <a href="https://open-meteo.com/" target="_blank" rel="noreferrer">날씨 Open-Meteo</a>
                    : <span>기본 하늘</span>}
                </>
              )}
            </span>
            <button onClick={() => setPanel("settings")}>
              보기 설정 <Settings2 size={13} />
            </button>
          </footer>
        </main>
      </div>
      {error && (
        <div className="floating-error" role="alert">
          {error}
          <button aria-label="오류 닫기" onClick={() => setError("")}>
            <X size={16} />
          </button>
        </div>
      )}
      {toast && (
        <div className="toast" role="status">
          <Check size={16} />
          {toast}
        </div>
      )}
      {panel && (
        <Modal
          title={
            {
              story: "지금, 내 이야기",
              pantry: "우리 집 냉장고",
              event: "우리 집 생활 기록",
              invite: "우리 집에 초대하기",
              housewarming: "집들이 · 친구 방문",
              calendar: "가족 공유 캘린더",
              design: "우리 집 설정 · 집 분위기",
              notifications: "우리 집 알림함",
              "notification-settings": "우리 집 알림 설정",
              pets: "우리 집 돌봄 프로필",
              album: "추억 앨범",
              membership: "가족 구성원 · 집 참여",
              exports: "공동 기록 내보내기",
              deletion: "집 삭제 관리",
              settings: "내 계정과 보기 설정",
            }[panel]
          }
          onClose={() => {
            setPanel(null);
            setPromise(undefined);
          }}
        >
          {panel === "notifications" ? (
            <NotificationInbox
              items={notificationQuery.data?.pages.flatMap(page => page.items) ?? []}
              members={snapshot.members}
              loading={notificationQuery.isPending || notificationQuery.isFetchingNextPage}
              failed={notificationQuery.isError}
              more={notificationQuery.hasNextPage}
              loadMore={() => notificationQuery.fetchNextPage()}
              reload={() => notificationQuery.refetch()}
              settings={() => setPanel("notification-settings")}
              open={target => {
                if (target !== "design") setActivityTab(target);
                setPanel(target === "design" ? "design" : "event");
              }}
            />
          ) : panel === "notification-settings" ? (
            <NotificationSettings house={snapshot.household.id} readOnly={!!snapshot.household.pendingDeleteUntil}
              back={() => setPanel("notifications")} />
          ) : panel === "design" ? (
            studio ? (
              <p>집 분위기 설정은 실제 집에서 사용할 수 있어요.</p>
            ) : (
              <SpaceSettings
                snapshot={snapshot}
                inbox={editAccess.data}
                reloadAccess={() => editAccess.refetch()}
                onSaved={(data) => qc.setQueryData(["world", id], data)}
              />
            )
          ) : panel === "pets" ? (
            studio ? (
              <p>반려동물 관리는 실제 집에서 사용할 수 있어요.</p>
            ) : (
              <CareProfiles
                snapshot={snapshot}
                onPetSaved={(data) => qc.setQueryData(["world", id], data)}
                refresh={() => qc.invalidateQueries({ queryKey: ["world", id] })}
              />
            )
          ) : panel === "album" ? (
            studio ? <p>실제 집에서 돌봄 프로필을 삭제하면 여기에 사진이 남아요.</p>
              : <CareAlbum householdId={snapshot.household.id} />
          ) : panel === "deletion" ? (
            <HouseholdDeletionPanel household={snapshot.household} refresh={refresh} />
          ) : panel === "exports" ? (
            <HouseholdExportPanel householdId={snapshot.household.id} />
          ) : panel === "membership" ? (
            <MembershipPanel snapshot={snapshot} refresh={refresh} onLeave={() => {
              live.current?.destroy();
              qc.removeQueries({ predicate: (entry) => entry.queryKey[1] === id });
              nav("/homes", { replace: true, state: { membershipNotice: "이 집에서 나왔어요. 다른 집과 계정은 계속 이용할 수 있어요." } });
            }} />
          ) : panel === "housewarming" ? (
            studio ? (
              <p>집들이는 실제 집에서 사용할 수 있어요.</p>
            ) : (
              <HousewarmingPanel householdId={snapshot.household.id} />
            )
          ) : panel === "calendar" ? (
            studio ? (
              <p>일정은 실제 가족의 집에서 사용할 수 있어요.</p>
            ) : (
              <FamilyCalendar householdId={snapshot.household.id} isOwner={own.role === "OWNER"}
                readOnly={Boolean(snapshot.household.pendingDeleteUntil)} participants={[
                ...snapshot.members.map(({ id, name }) => ({ id, name })),
                ...snapshot.pets.map(({ id, name }) => ({ id, name, dependentLabel: "반려동물" })),
                ...(snapshot.dependents ?? []).map(({ id, name }) => ({ id, name, dependentLabel: "아기" })),
              ]} />
            )
          ) : panel === "story" ? (
            studio ? (
              <StoryForm snapshot={snapshot} studio onSaved={(data) => {
                setSample(data);
                setPanel(null);
                setToast("시안에 반영했어요. 저장되지는 않아요.");
              }} />
            ) : (
              <ExpressionForm
                householdId={snapshot.household.id}
                householdName={snapshot.household.name}
                onPlaceSaved={(enabled) => {
                  if (!enabled) foregroundLocation.forgetAnchor();
                  void refresh();
                  void foregroundLocation.refresh();
                }}
                automaticLocation={<ForegroundLocationSettings location={foregroundLocation}
                  house={snapshot.household.id} name={snapshot.household.name} />}
                legacy={<StoryForm snapshot={snapshot} studio={false} onSaved={async () => {
                  await refresh();
                  setPanel(null);
                  setToast("내 이야기를 공유했어요.");
                }} />}
                onSaved={() => {
                  void refresh();
                  setPanel(null);
                  setToast("내 이야기를 공유했어요.");
                }}
              />
            )
          ) : panel === "pantry" ? (
            <>
              <nav className="fridge-tabs" aria-label="냉장고 기능">
                <button
                  aria-pressed={fridgeTab === "food"}
                  className="button secondary"
                  onClick={() => setFridgeTab("food")}
                >
                  식료품 보기
                </button>
                <button
                  aria-pressed={fridgeTab === "notes"}
                  className="button secondary"
                  onClick={() => setFridgeTab("notes")}
                >
                  쪽지 보기 / 붙이기
                </button>
              </nav>
              {fridgeTab === "food" ? (
                <PantryPanel
                  snapshot={snapshot}
                  studio={studio}
                  onChanged={async (data) => {
                    if (studio) setSample(data);
                    else await refresh();
                  }}
                />
              ) : studio ? (
                <p>쪽지는 실제 가족의 집에서 사용할 수 있어요.</p>
              ) : (
                <ObjectNotes
                  householdId={snapshot.household.id}
                  members={snapshot.members}
                  memberId={own.id}
                  isOwner={own.role === "OWNER"}
                  readOnly={Boolean(snapshot.household.pendingDeleteUntil)}
                />
              )}
            </>
          ) : panel === "event" ? (
            <div className="activity-panel">
              {!studio && (
                <nav className="fridge-tabs" aria-label="생활 기록 분류">
                  <button className="button secondary" aria-pressed={activityTab === "records"} onClick={() => setActivityTab("records")}>기록 남기기</button>
                  <button className="button secondary" aria-pressed={activityTab === "requests"} onClick={() => setActivityTab("requests")}>부탁·공지</button>
                </nav>
              )}
              {!studio && activityTab === "requests" ? (
                <HouseholdRequestsPanel snapshot={snapshot} promise={promise} onPromiseClosed={() => setPromise(undefined)} />
              ) : (
                  <EventForm
                    snapshot={snapshot}
                    studio={studio}
                    onSaved={async (data) => {
                      if (studio) setSample(data);
                      else await refresh();
                      setPanel(null);
                      setToast(
                        studio
                          ? "시안에 소식을 남겼어요."
                          : "우리 집에 소식을 남겼어요.",
                      );
                    }}
                  />
              )}
            </div>
          ) : panel === "invite" ? (
            <InvitePanel snapshot={snapshot} studio={studio} />
          ) : (
            <AccountSettings
              snapshot={snapshot}
              studio={studio}
              place={place}
              palette={palette}
              reduced={reduced}
              windowWeather={place === "home" && !neutral ? windowSky : undefined}
              onPalette={setPalette}
              onReduced={setReduced}
              onOpen={setPanel}
              onSwitchHome={foregroundLocation.anchor?.house === id ? () => void switchHome() : undefined}
              onSaved={(data) => qc.setQueryData(["world", id], data)}
              onLogout={() => void leave()}
            />
          )}
        </Modal>
      )}
    </div>
  );
}

function StoryForm({
  snapshot,
  studio,
  onSaved,
}: {
  snapshot: Snapshot;
  studio: boolean;
  onSaved: (data: Snapshot) => void;
}) {
  const own = snapshot.members.find((m) => m.id === snapshot.meMemberId)!;
  const [place, setPlace] = useState(own.place),
    [mood, setMood] = useState(own.mood),
    [food, setFood] = useState(own.food),
    [thought, setThought] = useState(own.thought),
    [minutes, setMinutes] = useState(60),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const body = { place, mood, food, thought, expiresInMinutes: minutes };
      const data = studio
        ? {
            ...snapshot,
            members: snapshot.members.map((m) =>
              m.id === own.id
                ? {
                    ...m,
                    ...body,
                    statusExpiresAt: new Date(
                      Date.now() + minutes * 60000,
                    ).toISOString(),
                  }
                : m,
            ),
          }
        : await api<Snapshot>(
            `/api/households/${snapshot.household.id}/me/status`,
            "PATCH",
            body,
          );
      onSaved(data);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form onSubmit={submit}>
      <p className="form-intro">
        <strong>{snapshot.household.name}</strong>에 {own.name}의 이야기로 공유해요.
        직접 고른 상태는 캐릭터 머리 위에도 나타나요.
      </p>
      <label>
        내가 공유할 장소
        <select
          value={place}
          onChange={(e) => setPlace(e.target.value as Place)}
        >
          {Object.entries(places).map(([key, label]) => (
            <option value={key} key={key}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        지금 기분
        <input
          value={mood}
          onChange={(e) => setMood(e.target.value)}
          maxLength={60}
          placeholder="편안해요, 조금 피곤해요…"
        />
      </label>
      <div className="mood-chips">
        {["편안해요", "신나요", "쉬고 싶어요"].map((text) => (
          <button type="button" key={text} onClick={() => setMood(text)}>
            {text}
          </button>
        ))}
      </div>
      <label>
        먹고 싶은 음식
        <input
          value={food}
          onChange={(e) => setFood(e.target.value)}
          maxLength={60}
          placeholder="오늘은 따뜻한 수프"
        />
      </label>
      <label>
        머리 위에 띄울 한마디
        <input
          value={thought}
          onChange={(e) => setThought(e.target.value)}
          maxLength={100}
          placeholder="오늘도 수고했어"
        />
      </label>
      <label>
        이야기를 보여줄 시간
        <select
          value={minutes}
          onChange={(e) => setMinutes(Number(e.target.value))}
        >
          <option value="30">30분</option>
          <option value="60">1시간</option>
          <option value="180">3시간</option>
          <option value="1440">하루</option>
        </select>
      </label>
      <ErrorMessage message={error} />
      <div className="form-actions">
        <button
          className="button secondary"
          type="button"
          onClick={() => {
            setMood("");
            setFood("");
            setThought("");
          }}
        >
          내용 지우기
        </button>
        <button className="button primary" disabled={busy}>
          <Send size={16} />
          {studio ? "시안에 반영" : "공유하기"}
        </button>
      </div>
    </form>
  );
}

function PantryPanel({
  snapshot,
  studio,
  onChanged,
}: {
  snapshot: Snapshot;
  studio: boolean;
  onChanged: (data: Snapshot) => void;
}) {
  const [name, setName] = useState(""),
    [quantity, setQuantity] = useState(1),
    [location, setLocation] = useState("냉장"),
    [expiresOn, setExpiresOn] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function add(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const body = {
        name,
        quantity,
        location,
        unit: "개",
        expiresOn: expiresOn || null,
      };
      if (studio)
        onChanged({
          ...snapshot,
          pantryItems: [
            ...snapshot.pantryItems,
            { ...body, id: crypto.randomUUID(), version: 0, updatedAt: now },
          ],
        });
      else {
        await api(
          `/api/households/${snapshot.household.id}/pantry-items`,
          "POST",
          body,
        );
        await onChanged(snapshot);
      }
      setName("");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function consume(item: PantryItem) {
    setBusy(true);
    setError("");
    try {
      if (studio)
        onChanged({
          ...snapshot,
          pantryItems: snapshot.pantryItems.map((p) =>
            p.id === item.id
              ? { ...p, quantity: p.quantity - 1, version: p.version + 1 }
              : p,
          ),
        });
      else {
        await api(
          `/api/households/${snapshot.household.id}/pantry-items/${item.id}`,
          "PATCH",
          { quantity: item.quantity - 1, version: item.version },
        );
        await onChanged(snapshot);
      }
    } catch (e) {
      setError(
        e instanceof ApiError && e.status === 409
          ? "다른 구성원이 먼저 바꿨어요. 최신 수량을 확인하고 다시 눌러주세요."
          : (e as Error).message,
      );
      await onChanged(snapshot);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div>
      <p className="form-intro">
        실제로 사용한 뒤 기록해 주세요. 캐릭터의 행동은 재고를 바꾸지 않아요.
      </p>
      <div className="pantry-list">
        {snapshot.pantryItems.length ? (
          snapshot.pantryItems.map((item) => (
            <div className="pantry-row" key={item.id}>
              <span className="pantry-icon">
                <Utensils size={19} />
              </span>
              <div>
                <strong>{item.name}</strong>
                <small>
                  {item.location}
                  {item.expiresOn ? ` · ${item.expiresOn}까지` : ""}
                </small>
              </div>
              <b>
                {item.quantity}
                <small>{item.unit}</small>
              </b>
              <button
                className="small-button"
                disabled={busy || item.quantity < 1}
                onClick={() => void consume(item)}
              >
                1개 사용
              </button>
            </div>
          ))
        ) : (
          <p className="empty-text">냉장고에 첫 식료품을 기록해보세요.</p>
        )}
      </div>
      <form onSubmit={add} className="add-pantry">
        <h3>식료품 추가</h3>
        <label>
          품목 이름
          <input
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={80}
            placeholder="예: 우유"
          />
        </label>
        <div className="form-columns">
          <label>
            수량
            <input
              type="number"
              min="0"
              step="1"
              required
              value={quantity}
              onChange={(e) => setQuantity(Number(e.target.value))}
            />
          </label>
          <label>
            보관 위치
            <select
              value={location}
              onChange={(e) => setLocation(e.target.value)}
            >
              <option>냉장</option>
              <option>냉동</option>
              <option>실온</option>
            </select>
          </label>
        </div>
        <label>
          소비기한 <small>선택</small>
          <input
            type="date"
            value={expiresOn}
            onChange={(e) => setExpiresOn(e.target.value)}
          />
        </label>
        <ErrorMessage message={error} />
        <button className="button primary" disabled={busy}>
          <Plus size={16} />
          {busy ? "기록 중…" : studio ? "시안에 추가" : "냉장고에 기록하기"}
        </button>
      </form>
    </div>
  );
}

function EventForm({
  snapshot,
  studio,
  onSaved,
}: {
  snapshot: Snapshot;
  studio: boolean;
  onSaved: (data: Snapshot) => void;
}) {
  const [type, setType] = useState("ARRIVED_HOME"),
    [message, setMessage] = useState("집에 도착했어요"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const request = useRef<{ body: string; id: string } | null>(null);
  const pending = useRef(false);
  const [notice, setNotice] = useState("");
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (pending.current) return;
    pending.current = true;
    setBusy(true);
    setError("");
    setNotice("");
    const body = JSON.stringify({ type, message });
    if (request.current?.body !== body)
      request.current = { body, id: crypto.randomUUID() };
    try {
      if (studio) {
        const own = snapshot.members.find((m) => m.id === snapshot.meMemberId)!;
        onSaved({
          ...snapshot,
          events: [
            {
              id: request.current.id,
              type,
              message,
              authorName: own.name,
              authorMemberId: own.id,
              createdAt: new Date().toISOString(),
            },
            ...snapshot.events,
          ],
        });
      } else {
        const path = `/api/households/${snapshot.household.id}`;
        const input = { requestId: request.current.id, type, message };
        if (type === "ARRIVED_HOME") {
          try {
            const result = await api<{ alreadyHome: boolean }>(path + "/me/arrivals", "POST", {
              requestId: input.requestId, message,
            });
            if (result.alreadyHome) {
              setNotice("이미 집에 있는 상태예요. 귀가 소식을 다시 남기지 않았어요.");
              return;
            }
          } catch (error) {
            if (!(error instanceof ApiError) || error.status !== 404) throw error;
            await api(path + "/events", "POST", input);
          }
        } else await api(path + "/events", "POST", input);
        onSaved(snapshot);
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return (
    <form onSubmit={submit}>
      <p className="form-intro">
        직접 확인한 생활 소식을 남겨요. 자동 위치 감지나 배송 조회가 아닙니다.
      </p>
      <div className="event-choices">
        {Object.entries(eventTypes).map(([key, value]) => (
          <button
            type="button"
            aria-pressed={type === key}
            className={type === key ? "selected" : ""}
            key={key}
            onClick={() => {
              setType(key);
              setMessage(value.message);
              setNotice("");
            }}
          >
            {value.icon}
            {value.label}
          </button>
        ))}
      </div>
      <label>
        함께 나눌 소식
        <textarea
          required
          value={message}
          maxLength={300}
          onChange={(e) => setMessage(e.target.value)}
        />
      </label>
      {!studio && type === "ARRIVED_HOME" && <p className="muted">
        위치를 공유 중이라면 귀가 소식을 남길 때 가족에게 집에 있는 상태도 보여요.
      </p>}
      {notice && <p role="status">{notice}</p>}
      <ErrorMessage message={error} />
      <button className="button primary" disabled={busy}>
        <Send size={16} />
        {studio ? "시안에 남기기" : "소식 남기기"}
      </button>
      {snapshot.events.length > 0 && (
        <div className="event-history">
          <h3>최근 소식</h3>
          {snapshot.events.map((event) => (
            <div key={event.id}>
              <p>{event.message}{event.source === "AUTO" && <small> · 자동 확인</small>}</p>
              <small>
                {event.authorName} ·{" "}
                {new Date(event.createdAt).toLocaleString("ko-KR")}
              </small>
            </div>
          ))}
        </div>
      )}
    </form>
  );
}

function InvitePanel({
  snapshot,
  studio,
}: {
  snapshot: Snapshot;
  studio: boolean;
}) {
  const [invite, setInvite] = useState<{
      id: string;
      url: string;
      expiresAt: string;
    } | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const own = snapshot.members.find((m) => m.id === snapshot.meMemberId)!;
  async function create() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      setInvite(
        await api(
          `/api/households/${snapshot.household.id}/invitations`,
          "POST",
        ),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function cancel() {
    if (!invite) return;
    setBusy(true);
    try {
      await api(
        `/api/households/${snapshot.household.id}/invitations/${invite.id}`,
        "DELETE",
      );
      setInvite(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (studio)
    return (
      <div className="invitation-explainer">
        <Users size={42} />
        <h3>진짜 함께 사는 사람을 초대해요.</h3>
        <p>
          이곳은 그래픽과 움직임을 확인하는 시안입니다.
          <br />
          실제 집에서 초대 링크를 만들고, 인증과 수락을 거쳐 구성원으로 참여할
          수 있어요.
        </p>
        <Link className="button primary" to="/login">
          내 집 만들러 가기 <ArrowRight size={16} />
        </Link>
      </div>
    );
  return (
    <div>
      <p className="form-intro">
        현재 {snapshot.members.length}명 입주. 인증과 초대 수락을 마친 사람만
        나타나요.
      </p>
      {own.role === "OWNER" ? (
        <>
          {invite ? (
            <>
              <ShareLink url={new URL(invite.url, location.origin).href} />
              <p className="muted">
                한 사람만 수락할 수 있어요. 만료:{" "}
                {new Date(invite.expiresAt).toLocaleString("ko-KR")}
              </p>
              <button
                className="button secondary"
                disabled={busy}
                onClick={() => void cancel()}
              >
                {busy ? "취소 중…" : "초대 취소"}
              </button>
            </>
          ) : (
            <button
              className="button primary"
              disabled={busy}
              onClick={() => void create()}
            >
              <Plus size={16} />
              {busy ? "링크 만드는 중…" : "초대 링크 만들기"}
            </button>
          )}
        </>
      ) : (
        <p>초대 링크는 집 대표가 만들 수 있어요.</p>
      )}
      <ErrorMessage message={error} />
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/onboarding" element={<Onboarding />} />
      <Route path="/visit/:token" element={<VisitPage />} />
      <Route path="/invite/:token" element={<InvitePage />} />
      <Route path="/home/:id" element={<HouseholdRoute />} />
      <Route path="/homes" element={<HomeSelectionPage />} />
      {import.meta.env.DEV && (
        <Route path="/studio/onboarding" element={<OnboardingPreview />} />
      )}
      {import.meta.env.DEV && (
        <Route path="/studio" element={<GamePage studio />} />
      )}
      <Route path="/settings" element={<AccountPage />} />
      <Route
        path="*"
        element={
          <div className="form-page">
            <Brand />
            <h1>이 길에는 아직 집이 없어요.</h1>
            <BackLink />
          </div>
        }
      />
    </Routes>
  );
}
