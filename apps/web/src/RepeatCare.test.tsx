import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { RepeatCarePanel } from "./components/RepeatCarePanel";
import { api, type Snapshot } from "./lib/api";

vi.mock("./lib/api", () => ({ api: vi.fn() }));
const world = {
  household: { id: "h" }, meMemberId: "me",
  members: [{ id: "me", role: "MEMBER" }],
  pets: [{ id: "p", name: "모카", kind: "cat", appearance: "cream", guardianMemberId: "me" }],
} as unknown as Snapshot;

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it("보호자가 시각과 선택 요일로 반복 돌봄을 저장한다", async () => {
  vi.mocked(api).mockResolvedValue([]);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><RepeatCarePanel snapshot={world} /></QueryClientProvider>);
  await screen.findByText("예약한 반복 돌봄이 없어요.");
  fireEvent.change(screen.getByLabelText("돌봄 이름"), { target: { value: "저녁 급여" } });
  fireEvent.change(screen.getByLabelText("돌봄 시각"), { target: { value: "19:30" } });
  fireEvent.click(screen.getByRole("button", { name: "일요일" }));
  fireEvent.click(screen.getByRole("button", { name: "반복 돌봄 저장" }));
  await waitFor(() => expect(api).toHaveBeenCalledWith("/api/households/h/care-rules", "POST", {
    profileType: "PET", profileId: "p", title: "저녁 급여",
    weekdays: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"],
    localTime: "19:30", timeZone: "Asia/Seoul",
  }));
});
