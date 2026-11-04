import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, expect, it, vi } from "vitest";
import { CareReactions } from "./components/CareReactions";
import { api } from "./lib/api";

vi.mock("./lib/api", async (original) => ({ ...await original<typeof import("./lib/api")>(), api: vi.fn() }));
const mocked = vi.mocked(api);
const root = "/api/households/h/reactions";
afterEach(() => { cleanup(); mocked.mockReset(); });

it("loads reactions on demand and toggles only the user's own reaction", async () => {
  let active = false;
  mocked.mockImplementation(async (_path, method) => {
    if (method === "POST") active = true;
    if (method === "DELETE") active = false;
    return { counts: { THANKS: active ? 2 : 1 }, mine: active ? [{ id: "mine", code: "THANKS" }] : [] } as never;
  });
  render(<QueryClientProvider client={new QueryClient()}>
    <CareReactions householdId="h" sourceType="NOTE" sourceId="note" />
  </QueryClientProvider>);
  expect(mocked).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "마음 전하기" }));
  const initial = await screen.findByRole("button", { name: /고마워요/ });
  expect(initial.getAttribute("aria-pressed")).toBe("false");
  expect(screen.queryByRole("button", { name: /같이 먹어요/ })).toBeNull();
  fireEvent.click(initial);
  const chosen = await screen.findByRole("button", { name: "고마워요 · 2", pressed: true });
  expect(mocked).toHaveBeenCalledWith(root, "POST", {
    requestId: expect.any(String), sourceType: "NOTE", sourceId: "note", code: "THANKS",
  });
  fireEvent.click(chosen);
  await screen.findByRole("button", { name: "고마워요 · 1", pressed: false });
  expect(mocked).toHaveBeenCalledWith(root + "/mine", "DELETE");
});
