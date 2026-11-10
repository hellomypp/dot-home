import Phaser from "phaser";
import type { WorldHandle, WorldView } from "./types";

export const observationSize = { width: 320, height: 224 };

export function mountObservationScene(
  parent: HTMLElement, scene: Phaser.Scene & { setView(view: WorldView): void }, style: string, backgroundColor: string,
): WorldHandle {
  parent.classList.add("observation-world", style);
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    ...observationSize,
    pixelArt: true,
    backgroundColor,
    scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
    fps: { target: 30 },
    audio: { noAudio: true },
    render: { preserveDrawingBuffer: true },
    scene,
  });
  const observer = new ResizeObserver(() => {
    if (game.isBooted) game.scale.refresh();
  });
  observer.observe(parent);
  return {
    update: (view) => scene.setView(view),
    destroy: () => {
      observer.disconnect();
      game.destroy(true);
      parent.classList.remove("observation-world", style);
    },
  };
}
