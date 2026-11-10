import { describe, expect, it } from "vitest";
import { classifyFix } from "./foregroundLocation";
import { currentSharedPlace } from "./expressions";

const home = { latitude: 37, longitude: 127 };
const outside = { ...home, latitude: 37.003 };
const boundary = { ...home, latitude: 37.0018 };
const time = 1_700_000_000_000;
const fix = (point: typeof home, offset = 0, accuracy = 25) => ({
  ...point, time: time + offset, accuracy,
});

describe("접속 중 집 근처 판정", () => {
  it("같은 상태의 신선한 관측 두 번이 60초 떨어졌을 때만 확정한다", () => {
    const first = classifyFix(home, fix(home), null, time);
    expect(first.state).toBe("UNKNOWN");
    expect(classifyFix(home, fix(home, 59_999), first.candidate, time + 59_999).state).toBe("UNKNOWN");
    expect(classifyFix(home, fix(home, 60_000), first.candidate, time + 60_000).state).toBe("HOME");
    const changed = classifyFix(home, fix(outside, 65_000), first.candidate, time + 65_000);
    expect(changed.state).toBe("UNKNOWN");
    expect(classifyFix(home, fix(outside, 130_000), changed.candidate, time + 130_000).state).toBe("OUTSIDE");
  });

  it("경계·오차·오래된 관측은 판정 후보에서 제거한다", () => {
    const first = classifyFix(home, fix(home), null, time);
    expect(classifyFix(home, fix(boundary, 60_000), first.candidate, time + 60_000)).toEqual({ state: "UNKNOWN", candidate: null });
    expect(classifyFix(home, fix(home, 60_000, 101), first.candidate, time + 60_000).candidate).toBeNull();
    expect(classifyFix(home, fix(home), first.candidate, time + 120_001).candidate).toBeNull();
  });

  it("자동 공유가 만료되면 마지막 위치로 표시하지 않는다", () => {
    expect(currentSharedPlace({
      status: "AUTO", place: "HOME", lastPlace: null, sharedAt: new Date(time - 300_000).toISOString(),
      expiresAt: new Date(time).toISOString(),
    }, time)).toMatchObject({ status: "UNKNOWN", place: null, lastPlace: null });
  });
});
