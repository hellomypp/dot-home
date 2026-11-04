import { afterEach, beforeEach, it, expect, vi, type Mock } from "vitest";
import {
  render,
  screen,
  cleanup,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Routes, Route } from "react-router-dom";
vi.mock("./components/WorldCanvas", () => ({ WorldCanvas: () => null }));
vi.mock("./onboarding/PixelScene", () => ({ PixelScene: () => null }));
vi.mock("./components/Portrait", () => ({ Portrait: () => null }));
import { Onboarding, LoginPage } from "./App";
import { OnboardingExperience } from "./onboarding/Onboarding";
import { readDraft, saveDraft, emptyDraft } from "./onboarding/draft";
import { decodeOutfit, encodeOutfit, gameAppearance } from "./lib/appearance";
const identity = {
  user: { id: "google-user", name: "새 사용자" },
  memberships: [],
};
let post: Mock<(options?: RequestInit) => Promise<Response>>;
beforeEach(() => {
  sessionStorage.clear();
  vi.stubGlobal("scrollTo", vi.fn());
  post = vi.fn(
    async (_options?: RequestInit) =>
      new Response(
        JSON.stringify({
          household: { id: "created", name: "우리의 작은 집" },
        }),
        { status: 201 },
      ),
  );
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, options?: RequestInit) => {
      const path = String(input);
      if (path === "/api/auth/csrf")
        return new Response(
          JSON.stringify({ token: "csrf-test", headerName: "X-CSRF-TOKEN" }),
        );
      if (path === "/api/me") return new Response(JSON.stringify(identity));
      if (path === "/api/households") return post(options);
      throw new Error(path);
    }),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
function show(component = <Onboarding />) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const result = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/onboarding"]}>
        <Routes>
          <Route path="/onboarding" element={component} />
          <Route path="/home/:id" element={<h1>우리 집 게임</h1>} />
          <Route path="/invite/:token" element={<h1>초대 수락 화면</h1>} />
          <Route path="/login" element={<LoginPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return { ...result, client };
}
async function house() {
  fireEvent.click(
    await screen.findByRole("button", { name: /새로운 집 만들기/ }),
  );
}
function character() {
  fireEvent.click(screen.getByRole("button", { name: "다음: 캐릭터 꾸미기" }));
}
it("인증된 신규 사용자는 스토리로 시작하고 존재하지 않는 복구 버튼이 없다", async () => {
  show();
  expect(
    await screen.findByRole("heading", { name: /우리만의 작은 집을/ }),
  ).toBeTruthy();
  expect(screen.queryByText("기존 집으로 돌아가기")).toBeNull();
  expect(post).not.toHaveBeenCalled();
});
it("소속이 있는 사용자는 기존 집으로 돌아갈 수 있다", () => {
  show(
    <OnboardingExperience
      identity={{
        ...identity,
        memberships: [
          {
            householdId: "existing",
            householdName: "기존 집",
            memberId: "m",
            role: "OWNER",
          },
        ],
      }}
    />,
  );
  fireEvent.click(screen.getByRole("link", { name: /기존 집으로 돌아가기/ }));
  expect(screen.getByText("우리 집 게임")).toBeTruthy();
});
it("가족 링크를 확인해 원래 초대 화면으로 이동하고 외부 링크는 거부한다", async () => {
  show();
  fireEvent.click(
    await screen.findByRole("button", { name: /초대받은 집에 입주하기/ }),
  );
  fireEvent.change(screen.getByLabelText("초대 링크"), {
    target: { value: "https://evil.example/invite/" + "a".repeat(43) },
  });
  fireEvent.click(screen.getByRole("button", { name: /초대 확인하기/ }));
  expect(screen.getByRole("alert")).toBeTruthy();
  fireEvent.change(screen.getByLabelText("초대 링크"), {
    target: { value: "/invite/" + "a".repeat(43) },
  });
  fireEvent.click(screen.getByRole("button", { name: /초대 확인하기/ }));
  expect(screen.getByText("초대 수락 화면")).toBeTruthy();
  expect(post).not.toHaveBeenCalled();
});
it("집 이름만 입력하고 캐릭터 단계로 이동하며 인원과 반려동물 입력이 없다", async () => {
  show();await house();
  expect(screen.queryByText("함께할 사람 수")).toBeNull();
  expect(screen.queryByText("반려동물 수")).toBeNull();
  character();expect(post).not.toHaveBeenCalled();
  expect(screen.queryByText("함께 입주할 반려동물")).toBeNull();
});
it("이전 단계와 새로고침에 집 이름, 캐릭터, 외형을 보존한다", async () => {
  const app = show();
  await house();
  fireEvent.change(screen.getByLabelText("집 이름"), {
    target: { value: "느린 집" },
  });
  character();
  fireEvent.change(screen.getByLabelText("내 캐릭터 이름"), {
    target: { value: "하늘" },
  });
  fireEvent.click(screen.getByRole("button", { name: "파란 겉옷" }));
  fireEvent.click(screen.getByRole("button", { name: "이전" }));
  expect((screen.getByLabelText("집 이름") as HTMLInputElement).value).toBe(
    "느린 집",
  );
  character();
  app.unmount();
  show();
  expect(
    ((await screen.findByLabelText("내 캐릭터 이름")) as HTMLInputElement)
      .value,
  ).toBe("하늘");
  fireEvent.click(screen.getByRole("button", { name: "우리 집 만들기" }));
  expect(await screen.findByText("우리 집에 오신 걸 환영해요!")).toBeTruthy();
  const body = JSON.parse(post.mock.calls[0][0]!.body as string);
  expect(body).toMatchObject({
    name: "느린 집",
    memberName: "하늘",
    appearance: "v2:adult_male",
  });
  expect(sessionStorage.getItem("dot-home:onboarding:google-user")).toBeNull();
});
it("0마리 생성과 중복 클릭 방지를 검증하고 완료 후 게임에 진입한다", async () => {
  let finish!: (response: Response) => void;
  post.mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  show();
  await house();
  character();
  const button = screen.getByRole("button", { name: "우리 집 만들기" });
  fireEvent.click(button);
  fireEvent.click(button);
  await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
  expect(Object.keys(JSON.parse(post.mock.calls[0][0]!.body as string)).sort()).toEqual(["appearance","memberName","name","presetId"]);
  finish(
    new Response(
      JSON.stringify({ household: { id: "created", name: "새 집" } }),
    ),
  );
  fireEvent.click(
    await screen.findByRole("link", { name: /우리 집으로 들어가기/ }),
  );
  expect(screen.getByText("우리 집 게임")).toBeTruthy();
});
it("API 오류가 나도 입력과 외형이 유지되어 재시도할 수 있다", async () => {
  post.mockResolvedValue(
    new Response(JSON.stringify({ message: "잠시 후 다시 시도해 주세요" }), {
      status: 500,
    }),
  );
  show();
  await house();
  character();
  fireEvent.change(screen.getByLabelText("내 캐릭터 이름"), {
    target: { value: "보존할 이름" },
  });
  fireEvent.click(screen.getByRole("button", { name: "파란 겉옷" }));
  fireEvent.click(screen.getByRole("button", { name: "우리 집 만들기" }));
  expect(await screen.findByRole("alert")).toHaveProperty(
    "textContent",
    "잠시 후 다시 시도해 주세요",
  );
  expect(
    (screen.getByLabelText("내 캐릭터 이름") as HTMLInputElement).value,
  ).toBe("보존할 이름");
  expect(
    screen
      .getByRole("button", { name: "파란 겉옷" })
      .getAttribute("aria-pressed"),
  ).toBe("true");
  expect(
    (
      screen.getByRole("button", {
        name: "우리 집 만들기",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(false);
});
it("로그아웃은 CSRF POST, 쿼리 캐시와 계정별 임시 입력을 정리한다", async () => {
  let authenticated = true;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: RequestInfo | URL, options?: RequestInit) => {
      const path = String(input);
      if (path === "/api/auth/csrf")
        return new Response(
          JSON.stringify({ token: "csrf-test", headerName: "X-CSRF-TOKEN" }),
        );
      if (path === "/api/logout") {
        expect(options?.method).toBe("POST");
        expect(
          (options?.headers as Record<string, string>)["X-CSRF-TOKEN"],
        ).toBe("csrf-test");
        authenticated = false;
        return new Response(null, { status: 204 });
      }
      if (path === "/api/me")
        return new Response(
          JSON.stringify(authenticated ? identity : { message: "로그인 필요" }),
          { status: authenticated ? 200 : 401 },
        );
      if (path === "/api/auth/config")
        return new Response(JSON.stringify({ googleEnabled: true }));
      throw new Error(path);
    }),
  );
  const { client } = show();
  fireEvent.click(await screen.findByRole("button", { name: "계정 메뉴" }));
  expect(screen.getByText("새 사용자")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));
  expect(
    await screen.findByRole("link", { name: "Google로 시작하기" }),
  ).toBeTruthy();
  await waitFor(() => expect(client.getQueryData(["me"])).toBeUndefined());
  expect(sessionStorage.getItem("dot-home:onboarding:google-user")).toBeNull();
});
it("오래되거나 잘못된 draft는 복원하지 않으며 계정 사이에 공유되지 않는다", () => {
  sessionStorage.setItem("dot-home:onboarding:a",JSON.stringify({updatedAt:Date.now(),draft:null}));
  expect(readDraft("a", "새 이름").memberName).toBe("새 이름");
  saveDraft("a", { ...emptyDraft("하늘"), name: "개인 집" });
  expect(readDraft("b", "보리").name).toBe("우리의 작은 집");
  sessionStorage.setItem(
    "dot-home:onboarding:a",
    JSON.stringify({ updatedAt: 0, draft: emptyDraft("하늘") }),
  );
  expect(readDraft("a", "새 이름").memberName).toBe("새 이름");
});
it("구형 색상은 유지하고 새 외형의 네 항목을 손실 없이 복원한다", () => {
  expect(gameAppearance("rose").shirt).toBe("#bb7975");
  const outfit = { hair: 3, top: 2, bottom: 1, accessory: 2 };
  expect(decodeOutfit(encodeOutfit(outfit))).toEqual(outfit);
  expect(gameAppearance("v1:3212")).toMatchObject({
    hairStyle: 3,
    accessory: 2,
    pants: "#526d87",
    shirt: "#6e88a1",
  });
  expect(decodeOutfit("invalid")).toEqual({
    hair: 0,
    top: 0,
    bottom: 0,
    accessory: 0,
  });
});

it("제공된 캐릭터 스타일을 선택하고 프리셋과 함께 저장한다", async () => {
  show();await house();
  fireEvent.click(screen.getByRole("button", {name:/컬러 타운하우스/}));
  character();
  fireEvent.click(screen.getByRole("button", {name:"노란 멜빵 캐릭터"}));
  fireEvent.click(screen.getByRole("button", {name:"우리 집 만들기"}));
  await waitFor(()=>expect(post).toHaveBeenCalledOnce());
  expect(JSON.parse(post.mock.calls[0][0]!.body as string)).toMatchObject({appearance:"v2:child",presetId:"brick_loft"});
});

it("구형 draft의 인원/반려동물 필드를 무시하고 필요한 값만 복원한다",()=>{
  sessionStorage.setItem("dot-home:onboarding:a",JSON.stringify({updatedAt:Date.now(),draft:{step:2,name:"이전 집",memberName:"이전 이름",appearance:"v1:3212",people:0,petCount:999,pets:[{name:"옛 동물"}],token:"비밀 아님 테스트"}}));
  expect(readDraft("a","기본")).toEqual({step:2,name:"이전 집",memberName:"이전 이름",appearance:"v1:3212",presetId:"warm-wood-home"});
});
