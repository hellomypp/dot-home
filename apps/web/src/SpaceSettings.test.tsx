import { useState } from "react";
import { afterEach, it, expect, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { SpaceSettings } from "./components/SpaceSettings";
import { api, type Snapshot } from "./lib/api";
vi.mock("./lib/api", () => ({ api: vi.fn() }));
vi.mock("./components/SpacePreview", () => ({
  SpacePreview: ({ design }: { design: { presetId: string } }) => (
    <div data-testid="preview">{design.presetId}</div>
  ),
}));
const initial = {
  household: { id: "h" },
  meMemberId: "me",
  members: [{ id: "me", role: "OWNER" }],
  design: { presetId: "garden_home", version: 3, variants: { sofa: "blue" } },
} as unknown as Snapshot;
function App({ world = initial }: { world?: Snapshot }) {
  const [value, setValue] = useState(world);
  return <SpaceSettings snapshot={value} onSaved={setValue} />;
}
afterEach(() => cleanup());
it("preview and cancel never persist; save keeps per-object override across presets", async () => {
  vi.mocked(api).mockResolvedValueOnce({
    ...initial,
    design: { ...initial.design, presetId: "brick_loft", version: 4 },
  });
  render(<App />);
  fireEvent.click(screen.getByRole("button", { name: "컬러 타운하우스" }));
  expect(api).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "미리보기 취소" }));
  expect(
    screen.getByRole("button", { name: "따뜻한 나무집" }).getAttribute("aria-pressed"),
  ).toBe("true");
  fireEvent.click(screen.getByRole("button", { name: "컬러 타운하우스" }));
  fireEvent.click(screen.getByRole("button", { name: "집 분위기 저장" }));
  await screen.findByRole("status");
  expect(api).toHaveBeenCalledWith("/api/households/h/design", "PATCH", {
    presetId: "brick_loft",
    version: 3,
    variants: { sofa: "blue" },
  });
});
it("family can see shared design without mutation controls", () => {
  render(
    <App
      world={{
        ...initial,
        members: [{ ...initial.members[0], role: "MEMBER" }],
      }}
    />,
  );
  expect(screen.getByTestId("preview").textContent).toBe("garden_home");
  expect(screen.queryByRole("button", { name: "집 분위기 저장" })).toBeNull();
});
it("deduplicates save and preserves failed draft, then permits fetching latest version", async () => {
  let reject!: (e: Error) => void;
  vi.mocked(api).mockImplementationOnce(
    () =>
      new Promise((_resolve, r) => {
        reject = r;
      }),
  );
  render(<App />);
  fireEvent.click(screen.getByRole("button", { name: "모던한 아파트" }));
  const button = screen.getByRole("button", { name: "집 분위기 저장" });
  fireEvent.click(button);
  fireEvent.click(button);
  expect(api).toHaveBeenCalledTimes(1);
  reject(new Error("다른 기기에서 변경했어요"));
  await screen.findByRole("alert");
  expect(
    screen
      .getByRole("button", { name: "모던한 아파트" })
      .getAttribute("aria-pressed"),
  ).toBe("true");
  vi.mocked(api).mockResolvedValueOnce({
    presetId: "brick_loft",
    version: 4,
    variants: {},
  });
  fireEvent.click(
    screen.getByRole("button", { name: "최신 설정 다시 가져오기" }),
  );
  await waitFor(() =>
    expect(
      screen
        .getByRole("button", { name: "컬러 타운하우스" })
        .getAttribute("aria-pressed"),
    ).toBe("true"),
  );
});
