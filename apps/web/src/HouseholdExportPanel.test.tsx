import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { HouseholdExportPanel } from "./components/HouseholdExportPanel";
import { api } from "./lib/api";

vi.mock("./lib/api", () => ({ api: vi.fn() }));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it("공동 기록 내보내기를 요청하고 준비 중 상태를 표시한다", async () => {
  vi.mocked(api).mockResolvedValueOnce([]).mockResolvedValueOnce({ id: "job", state: "PENDING" });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><HouseholdExportPanel householdId="h" /></QueryClientProvider>);
  fireEvent.click(await screen.findByRole("button", { name: "내보내기 요청" }));
  await screen.findByText(/공동 기록을 모으고 있어요/);
  expect(api).toHaveBeenCalledWith("/api/households/h/exports", "POST", { requestId: expect.any(String) });
});
