import { expect, it } from "vitest";
import { currentSharedPlace, type SharedPlace } from "./expressions";

it("treats a location as historical at the exact expiration boundary", () => {
  const expiresAt = "2026-11-05T10:00:00Z";
  const location: SharedPlace = {
    status: "MANUAL", place: "OFFICE", lastPlace: null, sharedAt: "2026-11-05T09:30:00Z", expiresAt,
  };
  expect(currentSharedPlace(location, Date.parse(expiresAt) - 1)).toBe(location);
  expect(currentSharedPlace(location, Date.parse(expiresAt))).toMatchObject({
    status: "UNKNOWN", place: null, lastPlace: "OFFICE",
  });
});
