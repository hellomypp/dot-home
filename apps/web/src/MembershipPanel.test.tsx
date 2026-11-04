import { afterEach, expect, it, vi } from "vitest";
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

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it("대표의 가족 퇴실은 대상 확인 뒤 버전을 보낸다", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(<MembershipPanel snapshot={world} onLeave={vi.fn()} refresh={refresh} />);
  fireEvent.click(screen.getByRole("button", { name: "가족 참여 종료" }));
  expect(screen.getByRole("heading", { name: "가족 님의 참여를 종료할까요?" })).toBeTruthy();
  expect(screen.getByText("바다 집")).toBeTruthy();
  expect(api).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "참여 종료 확인" }));
  await screen.findByRole("status");
  expect(api).toHaveBeenCalledWith("/api/households/h/members/family?version=2", "DELETE");
});

it("가족 본인의 집 나가기 요청은 확인 후에만 보낸다", async () => {
  const onLeave = vi.fn();
  render(<MembershipPanel snapshot={{ ...world, meMemberId: "family" }} onLeave={onLeave} refresh={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: "이 집에서 나가기" }));
  expect(api).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "이 집에서 나가기 확인" }));
  await waitFor(() => expect(onLeave).toHaveBeenCalledOnce());
  expect(api).toHaveBeenCalledWith("/api/households/h/me/leave", "POST", { requestId: expect.any(String) });
});
