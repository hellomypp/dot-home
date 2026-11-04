import { afterEach, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ComponentProps } from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MembershipPanel } from "./components/MembershipPanel";
import { api, type Snapshot } from "./lib/api";

vi.mock("./lib/api", () => ({ api: vi.fn() }));
const world = {
  household: { id: "h", name: "바다 집" }, meMemberId: "owner",
  members: [
    { id: "owner", name: "대표", role: "OWNER" },
    { id: "family", name: "가족", role: "MEMBER", membershipVersion: 2 },
  ],
} as unknown as Snapshot;

function panel(props: ComponentProps<typeof MembershipPanel>, offers: unknown[] = []) {
  vi.mocked(api).mockResolvedValue(offers);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><MembershipPanel {...props} /></QueryClientProvider>);
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it("대표의 가족 퇴실은 대상 확인 뒤 버전을 보낸다", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  panel({ snapshot: world, onLeave: vi.fn(), refresh });
  await screen.findByText(/상대가 24시간/);
  fireEvent.click(screen.getByRole("button", { name: "가족 참여 종료" }));
  expect(screen.getByRole("heading", { name: "가족 님의 참여를 종료할까요?" })).toBeTruthy();
  expect(screen.getByText("바다 집")).toBeTruthy();
  expect(vi.mocked(api).mock.calls.every(([, method]) => method === undefined)).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "참여 종료 확인" }));
  await screen.findByText("가족 님의 참여가 종료됐어요.");
  expect(api).toHaveBeenCalledWith("/api/households/h/members/family?version=2", "DELETE");
});

it("가족 본인의 집 나가기 요청은 확인 후에만 보낸다", async () => {
  const onLeave = vi.fn();
  panel({ snapshot: { ...world, meMemberId: "family" }, onLeave, refresh: vi.fn() });
  await screen.findByText(/현재 받은 이전 제안/);
  fireEvent.click(screen.getByRole("button", { name: "이 집에서 나가기" }));
  expect(vi.mocked(api).mock.calls.every(([, method]) => method === undefined)).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "이 집에서 나가기 확인" }));
  await waitFor(() => expect(onLeave).toHaveBeenCalledOnce());
  expect(api).toHaveBeenCalledWith("/api/households/h/me/leave", "POST", { requestId: expect.any(String) });
});

it("제안을 받은 가족의 명시적 확인 전에는 대표 권한을 받지 않는다", async () => {
  const offered = { id: "offer", fromMemberId: "owner", targetMemberId: "family", state: "PENDING", version: 3, expiresAt: "2026-11-06T14:00:00Z" };
  const refresh = vi.fn().mockResolvedValue(undefined);
  panel({ snapshot: { ...world, meMemberId: "family" }, onLeave: vi.fn(), refresh }, [offered]);
  fireEvent.click(await screen.findByRole("button", { name: "이전 제안 확인" }));
  const accept = screen.getByRole("button", { name: "이전 수락 확인" }) as HTMLButtonElement;
  expect(accept.disabled).toBe(true);
  fireEvent.click(screen.getByRole("checkbox", { name: /저는 성인이고/ }));
  fireEvent.click(accept);
  await waitFor(() => expect(refresh).toHaveBeenCalled());
  expect(api).toHaveBeenCalledWith("/api/households/h/ownership-transfers/offer/accept", "POST", {
    version: 3, adultConfirmed: true,
  });
});
