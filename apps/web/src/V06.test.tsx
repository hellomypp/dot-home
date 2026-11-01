import { beforeEach, afterEach, it, expect, vi } from "vitest";
import {
  render,
  screen,
  fireEvent,
  cleanup,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ObjectNotes, type ObjectNote } from "./components/ObjectNotes";
import {
  FamilyCalendar,
  occurs,
  seoulDate,
  type CalendarEvent,
} from "./components/FamilyCalendar";
import { VisitPage } from "./components/Housewarming";
import { safeInvitePath } from "./lib/auth";
import { api } from "./lib/api";
vi.mock("./lib/api", () => ({ api: vi.fn() }));
vi.mock("./onboarding/PixelScene", () => ({ PixelScene: () => null }));
const mocked = vi.mocked(api);
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
beforeEach(() => {
  mocked.mockReset();
});
function show(element: React.ReactNode, path = "/") {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      <MemoryRouter initialEntries={[path]}>{element}</MemoryRouter>
    </QueryClientProvider>,
  );
}
it("쪽지 본문은 HTML 실행 없이 표시하고 떼기와 보관함을 API에 연결한다", async () => {
  let attached = true;
  const note: ObjectNote = {
    id: "n",
    objectId: "fridge",
    authorMemberId: "a",
    body: "<img src=x onerror=alert(1)>",
    state: "ATTACHED",
    createdAt: new Date().toISOString(),
    takenByMemberId: null,
    takenAt: null,
    version: 0,
  };
  mocked.mockImplementation(async (path, method) => {
    if (method === "POST") {
      attached = false;
      return { ...note, state: "TAKEN" } as never;
    }
    return (
      path.endsWith("archive") ? [note] : attached ? [note] : []
    ) as never;
  });
  const { container } = show(
    <ObjectNotes householdId="h" memberId="b" isOwner={false} />,
  );
  expect(await screen.findByText(note.body)).toBeTruthy();
  expect(container.querySelector("img")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "떼어 읽기" }));
  await waitFor(() => expect(screen.queryByText(note.body)).toBeNull());
  expect(mocked).toHaveBeenCalledWith(
    "/api/households/h/notes/n/take",
    "POST",
    { version: 0 },
  );
  fireEvent.click(screen.getByRole("button", { name: "가족 보관함" }));
  expect(await screen.findByText(note.body)).toBeTruthy();
});
it("쪽지 작성 실패 시 본문을 보존한다", async () => {
  mocked.mockImplementation(async (_path, method) => {
    if (method === "POST") throw new Error("연결을 다시 확인해주세요");
    return [] as never;
  });
  show(<ObjectNotes householdId="h" memberId="a" isOwner />);
  const input = screen.getByLabelText("가족에게 남길 쪽지");
  fireEvent.change(input, { target: { value: "남길 이야기" } });
  fireEvent.click(screen.getByRole("button", { name: "쪽지 붙이기" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect((input as HTMLTextAreaElement).value).toBe("남길 이야기");
});
it("캘린더는 월 탐색과 종일 일정 생성을 연결한다", async () => {
  mocked.mockResolvedValue([] as never);
  show(<FamilyCalendar householdId="h" />);
  const month = seoulDate().slice(0, 7);
  await waitFor(() =>
    expect(mocked).toHaveBeenCalledWith(
      expect.stringContaining("from=" + month + "-01"),
    ),
  );
  fireEvent.click(screen.getByRole("button", { name: "다음 달" }));
  fireEvent.click(screen.getByRole("button", { name: "오늘" }));
  fireEvent.click(screen.getByRole("button", { name: "일정 등록" }));
  fireEvent.change(screen.getByLabelText("제목"), {
    target: { value: "가족 생일" },
  });
  fireEvent.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() =>
    expect(mocked).toHaveBeenCalledWith(
      "/api/households/h/calendar-events",
      "POST",
      expect.objectContaining({
        title: "가족 생일",
        allDay: true,
        startAt: null,
        endAt: null,
        timeZone: "Asia/Seoul",
      }),
    ),
  );
});
it("UTC 자정 경계와 종일 날짜는 브라우저 로컬 시간대에 의존하지 않는다", () => {
  const timed = {
    allDay: false,
    startAt: "2026-11-02T15:00:00Z",
    endAt: "2026-11-02T16:00:00Z",
  } as CalendarEvent;
  expect(occurs(timed, "2026-11-03")).toBe(true);
  expect(occurs(timed, "2026-11-02")).toBe(false);
  expect(
    occurs(
      {
        allDay: true,
        startDate: "2026-11-03",
        endDate: "2026-11-03",
      } as CalendarEvent,
      "2026-11-03",
    ),
  ).toBe(true);
});
it("집들이는 로그인 후 방문 버튼을 눌러야 수락하고 텍스트 방명록을 표시한다", async () => {
  const token = "a".repeat(43);
  mocked.mockImplementation(async (path, method) => {
    if (path === "/api/me")
      return {
        user: { id: "visitor", name: "친구" },
        memberships: [],
      } as never;
    if (method === "POST")
      return {
        householdName: "친구 집",
        theme: "warm",
        guestbook: [],
      } as never;
    throw new Error("unexpected");
  });
  show(
    <Routes>
      <Route path="/visit/:token" element={<VisitPage />} />
    </Routes>,
    "/visit/" + token,
  );
  expect(await screen.findByRole("button", { name: "방문하기" })).toBeTruthy();
  expect(mocked.mock.calls.filter((c) => c[1] === "POST")).toHaveLength(0);
  fireEvent.click(screen.getByRole("button", { name: "방문하기" }));
  expect(
    await screen.findByRole("heading", { name: "친구 집 집들이" }),
  ).toBeTruthy();
  expect(mocked).toHaveBeenCalledWith(
    "/api/visits/" + token + "/accept",
    "POST",
  );
  expect(safeInvitePath("/visit/" + token)).toBe("/visit/" + token);
  expect(safeInvitePath("/visit/" + token + "?redirect=evil")).toBeNull();
});
it("자정에 끝난 시간 일정은 다음 날짜를 차지하지 않는다", () => {
  const event = {
    allDay: false,
    startAt: "2026-11-02T14:00:00Z",
    endAt: "2026-11-02T15:00:00Z",
  } as CalendarEvent;
  expect(occurs(event, "2026-11-02")).toBe(true);
  expect(occurs(event, "2026-11-03")).toBe(false);
  expect(occurs({ ...event, startAt: event.endAt }, "2026-11-03")).toBe(true);
});
it("일정 충돌 뒤에는 사용자 선택으로 최신 버전을 다시 열 수 있다", async () => {
  let entry = {
    id: "e",
    title: "원래 일정",
    description: "",
    category: "GENERAL",
    allDay: true,
    startDate: seoulDate(),
    endDate: seoulDate(),
    startAt: null,
    endAt: null,
    timeZone: "Asia/Seoul",
    version: 0,
  };
  let updates = 0;
  mocked.mockImplementation(async (_path, method) => {
    if (method === "PATCH") {
      updates++;
      if (updates === 1) {
        entry = { ...entry, title: "가족이 수정한 일정", version: 1 };
        throw new Error("다른 가족이 일정을 수정했어요");
      }
      return { ...entry, version: 2 } as never;
    }
    return [entry] as never;
  });
  show(<FamilyCalendar householdId="h" />);
  fireEvent.click(await screen.findByRole("button", { name: "상세 / 수정" }));
  fireEvent.click(screen.getByRole("button", { name: "일정 저장" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  fireEvent.click(
    await screen.findByRole("button", { name: "최신 내용으로 다시 열기" }),
  );
  expect((screen.getByLabelText("제목") as HTMLInputElement).value).toBe(
    "가족이 수정한 일정",
  );
  fireEvent.click(screen.getByRole("button", { name: "일정 저장" }));
  await waitFor(() =>
    expect(mocked).toHaveBeenCalledWith(
      "/api/households/h/calendar-events/e",
      "PATCH",
      expect.objectContaining({ version: 1 }),
    ),
  );
});
