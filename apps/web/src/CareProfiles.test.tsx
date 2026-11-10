import { afterEach, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { CareProfiles } from "./components/CareProfiles";
import { api, type Snapshot } from "./lib/api";

vi.mock("./lib/api", () => ({ api: vi.fn() }));
const world = {
  household: { id: "h" }, meMemberId: "me",
  members: [{ id: "me", name: "보호자", role: "MEMBER" }],
  pets: [], dependents: [],
} as unknown as Snapshot;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it("가족이 아기 프로필을 만들 때 보호자를 지정한다", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  render(<CareProfiles snapshot={world} onPetSaved={vi.fn()} refresh={refresh} />);
  fireEvent.click(screen.getByRole("button", { name: "아기 프로필" }));
  fireEvent.change(screen.getByLabelText("아기 이름"), { target: { value: "별이" } });
  fireEvent.click(screen.getByRole("button", { name: "아기 프로필 추가" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
  expect(api).toHaveBeenCalledWith("/api/households/h/dependents", "POST", {
    name: "별이", type: "BABY", guardianMemberId: "me",
  });
});

it("보호자의 삭제 확인에만 버전을 보내며 일반 가족은 읽을 수 있다", async () => {
  const refresh = vi.fn().mockResolvedValue(undefined);
  const profile = { id: "baby", type: "BABY", name: "별이", guardianMemberId: "me", appearanceProfile: "v2:child", version: 2 } as const;
  const { rerender } = render(<CareProfiles snapshot={{ ...world, meMemberId: "other", dependents: [profile] }} onPetSaved={vi.fn()} refresh={refresh} />);
  fireEvent.click(screen.getByRole("button", { name: "아기 프로필" }));
  const card = screen.getByRole("article");
  expect(within(card).getByText("별이")).toBeTruthy();
  expect(within(card).queryByRole("button")).toBeNull();
  rerender(<CareProfiles snapshot={{ ...world, dependents: [profile] }} onPetSaved={vi.fn()} refresh={refresh} />);
  fireEvent.click(within(card).getByRole("button", { name: "별이 프로필 수정" }));
  fireEvent.click(within(card).getByRole("button", { name: "프로필 삭제" }));
  expect(api).not.toHaveBeenCalled();
  fireEvent.click(within(card).getByRole("button", { name: "삭제 확인" }));
  await waitFor(() => expect(refresh).toHaveBeenCalledOnce());
  expect(api).toHaveBeenCalledWith("/api/households/h/dependents/baby?version=2", "DELETE");
});
