import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { LogOut, UserRound } from "lucide-react";
import { logout } from "../lib/api";
import { clearDrafts } from "../onboarding/draft";
export function SessionBar({ name }: { name: string }) {
  const navigate = useNavigate(),
    client = useQueryClient(),
    ref = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  useEffect(() => {
    function close(e: PointerEvent) {
      if (!ref.current?.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, []);
  async function leave() {
    setBusy(true);
    setError("");
    try {
      await logout();
      await client.cancelQueries();
      client.clear();
      clearDrafts();
      navigate("/login", { replace: true });
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  }
  return (
    <div
      className="profile-menu"
      ref={ref}
      onKeyDown={(e) => {
        if (e.key === "Escape") {
          setOpen(false);
          ref.current?.querySelector("button")?.focus();
        }
      }}
    >
      <button
        className="profile-trigger"
        aria-label="계정 메뉴"
        aria-expanded={open}
        aria-controls="profile-popover"
        onClick={() => setOpen(!open)}
      >
        <UserRound size={22} />
      </button>
      {open && (
        <div id="profile-popover" className="profile-popover">
          <strong>{name}</strong>
          <span>Google 계정</span>
          <button type="button" disabled={busy} onClick={() => void leave()}>
            <LogOut size={17} />
            {busy ? "로그아웃 중…" : "로그아웃"}
          </button>
          {error && <p role="alert">{error}</p>}
        </div>
      )}
    </div>
  );
}
