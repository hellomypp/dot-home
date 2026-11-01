import { useEffect, useRef } from "react";
import { makeAvatar } from "../game/art";
import { gameAppearance } from "../lib/appearance";
export function Portrait({
  color = "#7c9170",
  small = false,
  appearance,
}: {
  color?: string;
  small?: boolean;
  appearance?: string;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const context = ref.current?.getContext("2d");
    if (!context) return;
    context.clearRect(0, 0, 32, 48);
    context.drawImage(
      makeAvatar(
        {
          id: "portrait",
          name: "",
          place: "home",
          appearance: appearance
            ? gameAppearance(appearance)
            : { shirt: color },
        },
        "down",
        0,
      ),
      0,
      0,
    );
  }, [color, appearance]);
  return (
    <span className={`portrait ${small ? "small" : ""}`} aria-hidden="true">
      <canvas ref={ref} width={32} height={48} />
    </span>
  );
}
