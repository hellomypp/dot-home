import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { HouseholdRequestsPanel } from "./components/HouseholdRequestsPanel";
import { api, type Snapshot } from "./lib/api";

vi.mock("./lib/api", async (original) => ({ ...await original<typeof import("./lib/api")>(), api: vi.fn() }));
const mocked = vi.mocked(api);
const root = "/api/households/h/requests";
const world = {
  household: { id: "h", name: "검증 집", ownerId: "a" },
  meMemberId: "b",
  members: [{ id: "a", name: "하늘", role: "OWNER" }, { id: "b", name: "서윤", role: "MEMBER" }],
} as Snapshot;
const task = {
  id: "request", kind: "TASK", title: "과일 씻기", body: "사과 두 개 부탁해요.",
  authorMemberId: "a", targetMemberIds: ["b"], assigneeMemberId: null, state: "OPEN",
  dueAt: "2026-11-06T01:00:00Z", completedByMemberId: null, version: 0,
};
function show(snapshot = world) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><HouseholdRequestsPanel snapshot={snapshot} /></QueryClientProvider>);
}
beforeEach(() => mocked.mockReset());
afterEach(cleanup);

it("lets the intended member claim a request and shows the saved assignee", async () => {
  let row = { ...task };
  mocked.mockImplementation(async (_path, method) => {
    if (method === "POST") row = { ...row, assigneeMemberId: "b" as never, state: "CLAIMED", version: 1 };
    return { items: [row], nextCursor: null } as never;
  });
  show();
  fireEvent.click(await screen.findByRole("button", { name: "제가 할게요" }));
  expect(await screen.findByText("맡았어요")).toBeTruthy();
  expect(screen.getByRole("button", { name: "맡기 취소" })).toBeTruthy();
  expect(mocked).toHaveBeenCalledWith(root + "/request/claim", "POST", {
    requestId: expect.any(String), version: 0,
  });
});

it("preserves a failed create draft and reuses the request ID on retry", async () => {
  let attempt = 0;
  mocked.mockImplementation(async (_path, method) => {
    if (method === "POST" && attempt++ === 0) throw new Error("다시 연결해 주세요.");
    return { items: [], nextCursor: null } as never;
  });
  show();
  await screen.findByText("아직 이 목록에 생활 요청이 없어요.");
  fireEvent.click(screen.getByRole("button", { name: "새 요청" }));
  fireEvent.change(screen.getByLabelText("제목"), { target: { value: "물 한 잔 부탁" } });
  fireEvent.click(screen.getByRole("button", { name: "가족에게 공유" }));
  await screen.findByRole("alert");
  expect(screen.getByDisplayValue("물 한 잔 부탁")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "가족에게 공유" }));
  await waitFor(() => expect(screen.queryByDisplayValue("물 한 잔 부탁")).toBeNull());
  const writes = mocked.mock.calls.filter((call) => call[1] === "POST");
  expect(writes[0]).toEqual(writes[1]);
});

it("lets an author edit only the changed field with the original version", async () => {
  mocked.mockResolvedValue({ items: [{ ...task, version: 2 }], nextCursor: null } as never);
  show({ ...world, meMemberId: "a" });
  fireEvent.click(await screen.findByRole("button", { name: "수정" }));
  fireEvent.change(screen.getByLabelText("제목"), { target: { value: "배 씻기" } });
  fireEvent.click(screen.getByRole("button", { name: "수정 저장" }));
  await waitFor(() => expect(mocked).toHaveBeenCalledWith(root + "/request", "PATCH", {
    version: 2, title: "배 씻기",
  }));
});

it("shows private notice confirmation and never sends an actor identifier", async () => {
  mocked.mockResolvedValue({
    items: [{ ...task, kind: "NOTICE", state: "ACTIVE", acknowledgedByMe: false }],
    nextCursor: null,
  } as never);
  show();
  expect(screen.queryByRole("button", { name: "수정" })).toBeNull();
  fireEvent.click(await screen.findByRole("button", { name: "공지 확인" }));
  expect(await screen.findByText("내가 확인한 공지")).toBeTruthy();
  expect(screen.queryByRole("button", { name: "공지 확인" })).toBeNull();
  expect(mocked).toHaveBeenCalledWith(root + "/request/ack", "POST");
});

it("prepares a linked promise without writing until the user confirms its title and deadline", async () => {
  mocked.mockResolvedValue({ items: [], nextCursor: null } as never);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}>
    <HouseholdRequestsPanel snapshot={world} promise={{
      sourceType: "EXPRESSION", sourceId: "member.food.1", title: "음식 준비하기",
    }} />
  </QueryClientProvider>);
  expect(screen.getByDisplayValue("음식 준비하기")).toBeTruthy();
  const deadline = screen.getByLabelText("기한 · 서울 시간") as HTMLInputElement;
  expect(deadline.value).toBeTruthy();
  expect(mocked.mock.calls.some((call) => call[1] === "POST")).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "이 약속을 맡을게요" }));
  await waitFor(() => expect(mocked).toHaveBeenCalledWith(root, "POST", expect.objectContaining({
    kind: "PROMISE", title: "음식 준비하기", sourceType: "EXPRESSION", sourceId: "member.food.1",
    dueAt: expect.any(String), requestId: expect.any(String),
  })));
});
