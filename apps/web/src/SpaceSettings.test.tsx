import { useState } from "react";
import { afterEach, it, expect, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { SpaceSettings } from "./components/SpaceSettings";
import { api, type Snapshot } from "./lib/api";

vi.mock("./lib/api", () => ({ api: vi.fn() }));
vi.mock("./components/SpacePreview", () => ({
  SpacePreview: ({ design }: { design: { presetId: string } }) => <div data-testid="preview">{design.presetId}</div>,
}));
const initial = {
  household: { id: "h" },
  meMemberId: "me",
  members: [{ id: "me", role: "OWNER" }],
  design: { presetId: "garden_home", version: 3, variants: { sofa: "blue" } },
} as unknown as Snapshot;
const path = "/api/households/h/edit-sessions";
const session = { id: "session", status: "ACTIVE", draft: initial.design!, draftVersion: 0, committedVersion: null };
const changed = { ...session, draft: { ...session.draft, presetId: "brick_loft" }, draftVersion: 1 };
function App({ world = initial }: { world?: Snapshot }) {
  const [value, setValue] = useState(world);
  return <SpaceSettings snapshot={value} onSaved={setValue} />;
}
afterEach(() => { cleanup(); vi.mocked(api).mockReset(); });

it("stores a private draft and commits with the same version and request ID", async () => {
  vi.mocked(api)
    .mockResolvedValueOnce({ session: null })
    .mockResolvedValueOnce(session)
    .mockResolvedValueOnce(changed)
    .mockResolvedValueOnce({ world: { ...initial, design: { ...changed.draft, version: 4 } }, committedVersion: 4 });
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "집 분위기 편집" }));
  fireEvent.click(await screen.findByRole("button", { name: "컬러 타운하우스" }));
  const save = await screen.findByRole("button", { name: "집 분위기 저장" });
  await waitFor(() => expect(save).toHaveProperty("disabled", false));
  expect(api).toHaveBeenCalledWith(path + "/session/draft", "PATCH", { draftVersion: 0, design: changed.draft });
  expect(api).not.toHaveBeenCalledWith(path + "/session/commit", expect.anything(), expect.anything());
  fireEvent.click(save);
  fireEvent.click(save);
  await screen.findByText("집 분위기를 저장했어요.");
  expect(vi.mocked(api).mock.calls.filter(([url]) => url.endsWith("/commit"))).toEqual([
    [path + "/session/commit", "POST", { requestId: "session", baseDesignVersion: 3, draftVersion: 1 }],
  ]);
});

it("family can see shared design without fetching private edit data", () => {
  render(<App world={{ ...initial, members: [{ ...initial.members[0], role: "MEMBER" }] }} />);
  expect(screen.getByTestId("preview").textContent).toBe("garden_home");
  expect(screen.queryByRole("button", { name: "집 분위기 편집" })).toBeNull();
  expect(api).not.toHaveBeenCalled();
});

it("keeps a failed draft in the preview and retries without committing", async () => {
  vi.mocked(api)
    .mockResolvedValueOnce({ session })
    .mockRejectedValueOnce(new Error("연결이 끊겼어요"))
    .mockResolvedValueOnce(changed);
  render(<App />);
  const preset = await screen.findByRole("button", { name: "컬러 타운하우스" });
  fireEvent.click(preset);
  await screen.findByRole("alert");
  expect(preset.getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByRole("button", { name: "집 분위기 저장" })).toHaveProperty("disabled", true);
  fireEvent.click(screen.getByRole("button", { name: "초안 다시 저장" }));
  await waitFor(() => expect(screen.getByRole("button", { name: "집 분위기 저장" })).toHaveProperty("disabled", false));
  expect(vi.mocked(api).mock.calls.filter(([url]) => url.endsWith("/draft"))).toHaveLength(2);
});

it("reopens an expired draft in a new edit session", async () => {
  vi.mocked(api)
    .mockResolvedValueOnce({ session: { ...changed, status: "RECOVERABLE" } })
    .mockResolvedValueOnce({ ...changed, id: "new-session" });
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "이전 초안 이어서" }));
  const preset = await screen.findByRole("button", { name: "컬러 타운하우스" });
  expect(preset.getAttribute("aria-pressed")).toBe("true");
  expect(api).toHaveBeenCalledWith(path, "POST", { requestId: expect.any(String), recoverId: "session" });
});
