import { useEffect, useRef } from "react";
import {
  makeAvatar,
  makeCat,
  makeDog,
  makeCottage,
  makeFurniture,
  makeGarden,
  makeHouse,
} from "../game/art";
import { gameAppearance } from "../lib/appearance";
export function PixelScene({
  kind,
  appearance = "v1:0000",
  petKind = "cat",
}: {
  kind: "garden" | "cottage" | "room";
  appearance?: string;
  petKind?: "cat" | "dog";
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const ctx = ref.current?.getContext("2d");
    if (!ctx) return;
    ctx.imageSmoothingEnabled = false;
    ctx.clearRect(0, 0, 360, 310);
    if (kind === "cottage") ctx.drawImage(makeCottage(), 60, 0);
    if (kind === "garden") ctx.drawImage(makeGarden(), 0, 0);
    if (kind === "room") {
      ctx.drawImage(makeHouse("warm"), 90, 40, 460, 380, 0, 0, 360, 310);
      ctx.drawImage(makeFurniture("sofa"), -18, 145, 120, 96);
      ctx.drawImage(makeFurniture("plant"), 292, 72, 49, 88);
      ctx.drawImage(makeFurniture("lamp"), 74, 95, 31, 65);
      ctx.fillStyle = "#eee0bb";
      ctx.beginPath();
      ctx.ellipse(180, 259, 82, 26, 0, 0, Math.PI * 2);
      ctx.fill();
    }
    if (kind !== "cottage") {
      const avatar = makeAvatar(
        {
          id: "preview",
          name: "",
          place: "home",
          appearance: gameAppearance(appearance),
        },
        "down",
        0,
      );
      ctx.drawImage(
        avatar,
        kind === "garden" ? 144 : 148,
        kind === "garden" ? 212 : 176,
        64,
        96,
      );
      if (kind === "garden")
        ctx.drawImage(
          petKind === "dog" ? makeDog(0) : makeCat(0),
          215,
          251,
          51,
          42,
        );
    }
  }, [kind, appearance, petKind]);
  return (
    <canvas
      className={`pixel-scene ${kind}`}
      ref={ref}
      width={360}
      height={kind === "cottage" ? 176 : kind === "room" ? 280 : 310}
      role="img"
      aria-label={
        kind === "room"
          ? "집 안에서 바라본 내 캐릭터"
          : kind === "garden"
            ? "구름과 나무 사이, 작은 집 앞의 캐릭터와 반려동물 일러스트"
            : "우리 집 미리보기"
      }
    />
  );
}
