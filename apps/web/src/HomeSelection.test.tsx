import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import App from "./App";
import { rememberHome } from "./lib/lastHome";
import { connectWorld } from "./lib/realtime";

vi.mock("./components/WorldCanvas", () => ({ WorldCanvas: () => null }));
vi.mock("./onboarding/PixelScene", () => ({ PixelScene: () => null }));
vi.mock("./lib/realtime", () => ({ connectWorld: vi.fn() }));
vi.mock("./components/Modal", () => ({
  Modal: ({ children }: { children: React.ReactNode }) => <div role="dialog">{children}</div>,
}));

const member = { id: "me-member", userId: "account", name: "테스트", appearance: "sage", role: "OWNER", place: "home" };
const homes = [
  { householdId: "one", householdName: "첫 번째 집", memberId: member.id, role: "OWNER" },
  { householdId: "two", householdName: "두 번째 집", memberId: member.id, role: "MEMBER" },
];
const account = { id: "account", name: "테스트" };
const destroyed = vi.fn();
let denyWorld: boolean;
let denySession: boolean;
let allowed: typeof homes;

beforeEach(() => {
  localStorage.clear();
  allowed = homes;
  denyWorld = false;
  denySession = false;
  destroyed.mockReset();
  vi.mocked(connectWorld).mockReset().mockReturnValue({
    destroy: destroyed, refresh: vi.fn(), send: vi.fn(),
  });
  vi.stubGlobal("scrollTo", vi.fn());
  vi.stubGlobal("fetch", vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path === "/api/me") return denySession
      ? Response.json({}, { status: 401 })
      : Response.json({ user: account, memberships: allowed });
    if (path === "/api/auth/config") return Response.json({ googleEnabled: true });
    if (path.includes("/world")) {
      const home = homes.find(({ householdId }) => path.includes("/" + householdId + "/"));
      if (denyWorld || !home) return Response.json({}, { status: denySession ? 401 : 403 });
      return Response.json({
        household: { id: home.householdId, name: home.householdName, ownerId: account.id },
        meMemberId: member.id, members: [{ ...member, role: home.role }],
        revision: 1, serverTime: new Date().toISOString(), pets: [], pantryItems: [], events: [],
      });
    }
    if (path.includes("/objects/")) return Response.json([]);
    throw new Error(path);
  }));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

function show(path = "/", cachedIdentity = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  if (cachedIdentity) client.setQueryData(["me"], { user: account, memberships: homes });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}><App /></MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}

it("lists all memberships when the remembered home is inaccessible", async () => {
  rememberHome(account.id, "removed");
  show();
  expect(await screen.findByRole("heading", { name: "어느 집으로 갈까요?" })).toBeTruthy();
  expect(screen.getByRole("link", { name: /첫 번째 집/ }).getAttribute("href")).toBe("/home/one");
  expect(screen.getByRole("link", { name: /두 번째 집/ }).getAttribute("href")).toBe("/home/two");
});

it("restores the last accessible home and replaces its socket and panel on switch", async () => {
  rememberHome(account.id, "two");
  const client = show();
  await screen.findByRole("heading", { name: "두 번째 집", level: 1 });
  await waitFor(() => expect(connectWorld).toHaveBeenLastCalledWith("two", expect.any(Function), expect.any(Function), expect.any(Function)));
  fireEvent.click(screen.getByRole("button", { name: "내 계정과 보기 설정" }));
  fireEvent.click(screen.getByRole("link", { name: "다른 집으로 전환" }));
  expect(destroyed).toHaveBeenCalledTimes(1);
  fireEvent.click(await screen.findByRole("link", { name: /첫 번째 집/ }));
  await screen.findByRole("heading", { name: "첫 번째 집", level: 1 });
  expect(screen.queryByRole("dialog")).toBeNull();
  await waitFor(() => expect(connectWorld).toHaveBeenLastCalledWith("one", expect.any(Function), expect.any(Function), expect.any(Function)));
  await waitFor(() => expect(client.getQueryData(["world", "two"])).toBeUndefined());
  expect(localStorage.getItem("dot-home:last-home:account")).toBe("one");
});

it("discards a denied home and refreshes the membership list", async () => {
  denyWorld = true;
  allowed = [homes[0]];
  show("/home/two");
  expect(await screen.findByRole("link", { name: /첫 번째 집/ })).toBeTruthy();
  expect(screen.queryByRole("link", { name: /두 번째 집/ })).toBeNull();
  expect(connectWorld).not.toHaveBeenCalled();
});


it("returns an expired session to login even with a cached identity", async () => {
  denyWorld = true;
  denySession = true;
  rememberHome(account.id, "two");
  show("/home/two", true);
  expect(await screen.findByRole("link", { name: "Google로 시작하기" })).toBeTruthy();
  expect(screen.queryByRole("heading", { name: "두 번째 집" })).toBeNull();
});
