import { useEffect, useRef, useState } from "react";
import {
  defaultDesign,
  assetUrl,
  mapFor,
  renderPreview,
  spriteFor,
  type SpaceDesign,
} from "../game/spaces";
export function SpacePreview({
  design = defaultDesign,
}: {
  design?: SpaceDesign;
}) {
  const ref = useRef<HTMLCanvasElement>(null),
    [error, setError] = useState(false);
  const m = mapFor(design.presetId);
  const [x, y, width, height] = m.previewCrop ?? [64, 48, 320, 224];
  useEffect(() => {
    let alive = true;
    setError(false);
    const files = new Set([
      ...(m.tileset ? [m.tileset] : []),
      ...(m.backdrop ? [m.backdrop] : []),
      ...m.objects.map((o) => spriteFor(o, design)),
    ]);
    const images = new Map<string, HTMLImageElement>();
    Promise.all(
      [...files].map(
        (file) =>
          new Promise<void>((resolve, reject) => {
            const image = new Image();
            image.onload = () => {
              images.set(file, image);
              resolve();
            };
            image.onerror = reject;
            image.src = assetUrl(file);
          }),
      ),
    )
      .then(() => {
        if (!alive) return;
        const buffer = document.createElement("canvas");
        buffer.width = 448;
        buffer.height = 320;
        renderPreview(buffer.getContext("2d")!, m, images, design);
        const c = ref.current?.getContext("2d");
        if (c) {
          c.imageSmoothingEnabled = false;
          c.clearRect(0, 0, width, height);
          c.drawImage(buffer, -x, -y);
        }
      })
      .catch(() => {
        if (alive) setError(true);
      });
    return () => {
      alive = false;
    };
  }, [design.presetId, JSON.stringify(design.variants)]);
  return (
    <div className={m.previewCrop ? "space-preview full-room" : "space-preview"}>
      {error ? (
        <p role="alert">집 이미지를 불러오지 못했어요.</p>
      ) : (
        <canvas
          ref={ref}
          width={width}
          height={height}
          aria-label="실제 게임 맵으로 보는 집 미리보기"
          role="img"
        />
      )}
    </div>
  );
}
