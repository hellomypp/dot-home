import { useEffect, useRef, useState } from "react";
import {
  defaultDesign,
  mapFor,
  mapForDesign,
  renderPreview,
  type SpaceDesign,
} from "../game/spaces";
export function SpacePreview({
  design = defaultDesign,
  selected,
}: {
  design?: SpaceDesign;
  selected?: string;
}) {
  const ref = useRef<HTMLCanvasElement>(null),
    [error, setError] = useState(false),
    [art, setArt] = useState<{ preset: string; images: Map<string, HTMLImageElement> }>();
  const m = mapFor(design.presetId);
  const [x, y, width, height] = m.previewCrop;
  useEffect(() => {
    let alive = true;
    setError(false);
    const files = new Set([m.backdrop, ...m.objects.map((o) => o.sprite)]);
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
            image.src = file;
          }),
      ),
    )
      .then(() => {
        if (alive) setArt({ preset: m.id, images });
      })
      .catch(() => {
        if (alive) setError(true);
      });
    return () => {
      alive = false;
    };
  }, [m]);
  useEffect(() => {
    if (art?.preset !== m.id) return;
    const context = ref.current?.getContext("2d");
    if (!context) return;
    const buffer = document.createElement("canvas");
    buffer.width = 448;
    buffer.height = 320;
    renderPreview(buffer.getContext("2d")!, mapForDesign(design), art.images, design, selected);
    context.imageSmoothingEnabled = false;
    context.clearRect(0, 0, width, height);
    context.drawImage(buffer, -x, -y);
  }, [art, m.id, design, selected, x, y, width, height]);
  return (
    <div className="space-preview full-room">
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
