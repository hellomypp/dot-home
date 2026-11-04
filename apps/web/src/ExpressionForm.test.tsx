import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ExpressionForm } from "./components/ExpressionForm";
import { api, ApiError } from "./lib/api";
import type { ExpressionState } from "./lib/expressions";

vi.mock("./lib/api", async (original) => ({ ...await original<typeof import("./lib/api")>(), api: vi.fn() }));
const mocked = vi.mocked(api);
const path = "/api/households/h/me/state";
const empty = { value: null, updatedAt: null, expiresAt: null, version: 0 };
const active = { value: "CALM", updatedAt: "2026-11-05T00:00:00Z", expiresAt: "2026-11-05T04:00:00Z", version: 2 };
const state: ExpressionState = {
  place: empty, sharing: { enabled: false, version: 0 },
  revision: 1,
  serverTime: active.updatedAt,
  expressions: { thought: empty, food: empty, mood: active, physical: empty, availability: empty },
};
function show() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const saved = vi.fn();
  render(
    <QueryClientProvider client={client}>
      <ExpressionForm householdId="h" householdName="우리 집"
        onSaved={saved} onPlaceSaved={vi.fn()} legacy={null} />
    </QueryClientProvider>,
  );
  return saved;
}
beforeEach(() => mocked.mockReset());
afterEach(cleanup);

it("sends only changed expressions and keeps the same request ID on a retry", async () => {
  let attempt = 0;
  mocked.mockImplementation(async (_path, method) => {
    if (method === "PATCH" && attempt++ === 0) throw new Error("연결이 끊겼어요.");
    return state as never;
  });
  const saved = show();
  const food = await screen.findByLabelText("먹고 싶은 음식", { exact: true });
  fireEvent.change(food, { target: { value: "국수" } });
  fireEvent.click(screen.getByRole("button", { name: "표현 공유하기" }));
  await screen.findByRole("alert");
  expect((food as HTMLInputElement).value).toBe("국수");
  fireEvent.click(screen.getByRole("button", { name: "표현 공유하기" }));
  await waitFor(() => expect(saved).toHaveBeenCalledTimes(1));
  const writes = mocked.mock.calls.filter((call) => call[1] === "PATCH");
  expect(writes).toHaveLength(2);
  expect(writes[1]).toEqual(writes[0]);
  expect(writes[0]).toEqual([path, "PATCH", {
    requestId: expect.any(String), changes: { food: { value: "국수", version: 0 } },
  }]);
});

it("asks for explicit review before retrying with the latest field version", async () => {
  const latest = { ...state, expressions: { ...state.expressions, mood: { ...active, value: "HAPPY", version: 3 } } };
  let reads = 0;
  mocked.mockImplementation(async (_path, method) => {
    if (method === "PATCH" && reads === 1) throw new ApiError(409, "표현이 변경됐어요.");
    return (reads++ === 0 ? state : latest) as never;
  });
  show();
  const mood = await screen.findByLabelText("기분", { exact: true });
  fireEvent.change(mood, { target: { value: "" } });
  fireEvent.click(screen.getByRole("button", { name: "표현 공유하기" }));
  const review = await screen.findByRole("button", { name: "최신값 확인 · 내 입력 유지" });
  expect(screen.getByText(/최신 기뻐요.*내 입력 비우기/)).toBeTruthy();
  expect(screen.getByRole<HTMLButtonElement>("button", { name: "표현 공유하기" }).disabled).toBe(true);
  fireEvent.click(review);
  fireEvent.click(screen.getByRole("button", { name: "표현 공유하기" }));
  await waitFor(() => expect(mocked).toHaveBeenCalledWith(path, "PATCH", {
    requestId: expect.any(String), changes: { mood: { value: null, version: 3 } },
  }));
});

it("saves only the household-specific location consent", async () => {
  mocked.mockResolvedValue(state as never);
  show();
  const consent = await screen.findByLabelText("이 집 가족에게 위치 공유");
  fireEvent.click(consent);
  expect(screen.queryByLabelText("내가 공유할 장소")).toBeNull();
  expect(screen.queryByLabelText("장소 공개 시간")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "공유 설정 저장" }));
  await waitFor(() => expect(mocked).toHaveBeenCalledWith(path, "PATCH", {
    requestId: expect.any(String),
    changes: { sharing: { enabled: true, version: 0 } },
  }));
});
