import { useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "../lib/api";

export interface BrowserPushConfiguration {
  available: boolean;
  publicKey: string | null;
}

export const browserPushPath = "/api/me/browser-push";

function applicationKey(base64: string) {
  return Uint8Array.from(atob(base64.replaceAll("-", "+").replaceAll("_", "/")), character => character.charCodeAt(0));
}

async function currentSubscription() {
  return (await navigator.serviceWorker.getRegistration("/push-worker.js"))?.pushManager.getSubscription();
}

function registerSubscription(subscription: PushSubscription) {
  const keys = subscription.toJSON().keys;
  if (!keys?.p256dh || !keys.auth) throw new Error("이 브라우저에서 암호화된 알림 구독을 준비할 수 없어요.");
  return api(browserPushPath, "PUT", { endpoint: subscription.endpoint, keys: { p256dh: keys.p256dh, auth: keys.auth } });
}

function iosNeedsHomeScreen() {
  const navigatorWithStandalone = navigator as Navigator & { standalone?: boolean };
  const ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  return ios && !navigatorWithStandalone.standalone && !window.matchMedia("(display-mode: standalone)").matches;
}

async function readyWorker() {
  const registration = await navigator.serviceWorker.register("/push-worker.js", { updateViaCache: "none" });
  if (registration.active) return registration;
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    return await Promise.race([navigator.serviceWorker.ready, new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error("브라우저 알림을 준비하지 못했어요. 다시 시도해 주세요.")), 10_000);
    })]);
  } finally {
    clearTimeout(timer);
  }
}

export function BrowserPushControl({ configuration }: { configuration: BrowserPushConfiguration }) {
  const installed = !iosNeedsHomeScreen();
  const supported = window.isSecureContext && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const status = useQuery({
    queryKey: ["browser-push-status"],
    enabled: supported && installed,
    retry: false,
    queryFn: async () => {
      const subscription = await currentSubscription();
      if (!subscription) return false;
      const result = await api<{ enabled: boolean; payloadReady?: boolean }>(browserPushPath + "/status", "POST", { endpoint: subscription.endpoint });
      if (result.enabled && result.payloadReady === false) await registerSubscription(subscription);
      return result.enabled;
    },
  });

  async function setEnabled(enable: boolean) {
    if (pending.current || !configuration.publicKey) return;
    pending.current = true;
    setBusy(true);
    setError("");
    try {
      if (enable) {
        const permission = await Notification.requestPermission();
        if (permission !== "granted") throw new Error("브라우저에서 알림을 허용한 뒤 다시 시도해 주세요.");
        const registration = await readyWorker();
        const publicKey = applicationKey(configuration.publicKey);
        let subscription = await registration.pushManager.getSubscription();
        const subscribedKey = subscription?.options.applicationServerKey;
        if (subscription && (!subscribedKey || new Uint8Array(subscribedKey).toString() !== publicKey.toString())) {
          await api(browserPushPath + "/disable", "POST", { endpoint: subscription.endpoint });
          await subscription.unsubscribe();
          subscription = null;
        }
        const subscribe = () => registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: publicKey });
        subscription ??= await subscribe();
        try {
          await registerSubscription(subscription);
        } catch (error) {
          if (!(error instanceof ApiError) || error.code !== "PUSH_SUBSCRIPTION_OWNED") throw error;
          await subscription.unsubscribe();
          subscription = await subscribe();
          await registerSubscription(subscription);
        }
      } else {
        const subscription = await currentSubscription();
        if (subscription) {
          await api(browserPushPath + "/disable", "POST", { endpoint: subscription.endpoint });
          await subscription.unsubscribe();
        }
      }
      await status.refetch();
    } catch (error) {
      setError(error instanceof ApiError ? error.message
        : (error as Error).name === "AbortError" ? "브라우저 알림에 연결할 수 없어요. 잠시 뒤 다시 시도해 주세요."
          : (error as Error).message);
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="notification-device">
      <h3>이 기기의 브라우저 알림</h3>
      <p className="muted">참여 중인 집에서 켠 알림을 받아요. 잠금 화면에는 집별 설정에 따라 알림이 표시돼요.</p>
      {!installed ? <p>iPhone이나 iPad에서는 Safari의 공유 메뉴에서 ‘홈 화면에 추가’를 선택한 뒤, 홈 화면의 도트홈에서 알림을 켜 주세요.</p>
        : !supported ? <p>이 브라우저에서는 앱 안의 알림함을 이용할 수 있어요.</p> : (
          <>
            {status.isPending && <p role="status">이 기기의 알림을 확인하고 있어요…</p>}
            {status.isError && <p role="alert" className="error-message">기기 알림 설정을 불러오지 못했어요.</p>}
            <button type="button" className="button secondary" disabled={busy || status.isPending}
              onClick={() => status.isError ? void status.refetch() : void setEnabled(!status.data)}>
              {status.isError ? "다시 불러오기" : status.data ? "이 기기 알림 끄기" : "이 기기 알림 켜기"}
            </button>
          </>
        )}
      {error && <p role="alert" className="error-message">{error}</p>}
    </section>
  );
}
