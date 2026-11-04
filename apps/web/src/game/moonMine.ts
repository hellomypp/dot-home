import Phaser from "phaser";
import { assetBase, nativeCharacter } from "./spaces";
import { makeMoonMine, mineSize } from "./moonMineArt";
import type { WorldHandle, WorldView } from "./types";

const avatarX = 145;
const feetY = 181;
const cycleLength = 1650;
const impactTime = 700;
const fragments = [
  [-31, -64, 0xcaf9df], [-15, -77, 0xffdc87], [10, -92, 0xe8fff0],
  [30, -66, 0xffcf6d], [51, -45, 0x94ddce], [75, -61, 0xffdc87],
];

class MoonMine extends Phaser.Scene {
  private miner?: Phaser.GameObjects.Image;
  private motion?: Phaser.GameObjects.Graphics;
  private elapsed = 0;

  constructor(private view: WorldView) { super("moon-mine"); }

  private character() {
    return this.view.members.find((member) => member.id === (this.view.observedMemberId || this.view.ownMemberId));
  }

  preload() {
    for (const kind of ["adult_female", "adult_male", "child"])
      this.load.spritesheet(kind, `${assetBase}assets/characters/${kind}.png`, { frameWidth: 24, frameHeight: 40 });
  }

  create() {
    this.textures.addCanvas("moon-mine-art", makeMoonMine());
    this.add.image(0, 0, "moon-mine-art").setOrigin(0);
    this.miner = this.add.image(avatarX, feetY, nativeCharacter(this.character()?.characterStyle))
      .setOrigin(0.5, 1).setScale(1.5);
    this.motion = this.add.graphics();
    this.setView(this.view);
  }

  setView(view: WorldView) {
    this.view = view;
    const character = this.character();
    this.miner?.setVisible(Boolean(character)).setTexture(nativeCharacter(character?.characterStyle), 0);
    if (this.elapsed === 0 || view.reducedMotion) this.paint(0);
  }

  update(_time: number, delta: number) {
    if (this.view.reducedMotion) return;
    this.elapsed += Math.min(delta, 50);
    this.paint(this.elapsed);
  }

  private paint(elapsed: number) {
    if (!this.motion || !this.miner) return;
    const g = this.motion.clear();
    if (!this.character()) return;
    const phase = elapsed % cycleLength;
    const raise = Phaser.Math.Clamp((phase - 150) / 330, 0, 1);
    const swing = Phaser.Math.Clamp((phase - 520) / (impactTime - 520), 0, 1);
    const rest = Phaser.Math.Clamp((phase - 780) / 410, 0, 1);
    const angle = 0.45 - 2.55 * raise + 2.15 * swing ** 2 + 0.35 * rest;
    const dip = phase >= 630 && phase <= 800 ? 2 : 0;
    this.miner.setY(feetY + dip).setFrame(phase >= 560 && phase < 810 ? 1 : 0);
    this.helmet(g, dip);

    const handX = avatarX + 10;
    const handY = 153 + dip;
    const tipX = Math.round(handX + Math.cos(angle) * 29);
    const tipY = Math.round(handY + Math.sin(angle) * 29);
    g.lineStyle(4, 0x393042).lineBetween(handX, handY, tipX, tipY);
    g.lineStyle(2, 0xc79567).lineBetween(handX, handY, tipX, tipY);
    const nx = Math.round(Math.sin(angle) * 7);
    const ny = Math.round(-Math.cos(angle) * 7);
    g.lineStyle(5, 0x374454).lineBetween(tipX - nx, tipY - ny, tipX + nx, tipY + ny);
    g.lineStyle(3, 0xa3c8cb).lineBetween(tipX - nx, tipY - ny, tipX + nx, tipY + ny);
    g.fillStyle(0xe8f4df).fillRect(tipX + nx - 1, tipY + ny - 1, 2, 2);
    g.fillStyle(0xf0c38f).fillRect(handX - 1, handY - 1, 4, 4);

    const t = (phase - impactTime) / 1000;
    if (t >= 0 && t < 0.68) {
      for (const [i, [vx, vy, color]] of fragments.entries()) {
        const x = Math.round(186 + vx * t);
        const y = Math.round(153 + vy * t + 100 * t * t);
        g.fillStyle(color, 1 - t).fillRect(x, y, i % 2 === 0 ? 3 : 2, 2);
        if (i % 2 === 0) g.fillStyle(0xfff6d0, 1 - t).fillRect(x + 1, y - 1, 1, 4);
      }
      if (t < 0.15) {
        g.fillStyle(0xfff2b5, 1 - t * 4).fillRect(181, 152, 11, 2);
        g.fillRect(186, 148, 2, 9);
      }
    }
    const added = Math.floor((elapsed + 480) / cycleLength) % 5;
    for (const [x, y, count] of [[211, 190, 3], [221, 193, 2], [237, 173, 3], [247, 173, 2 + added]]) {
      for (let i = 0; i < count; i++) {
        g.fillStyle(0x794f45).fillRect(x - 1, y - 2 * i, 9, 3);
        g.fillStyle(0xddab5c).fillRect(x, y - 2 * i, 7, 2);
        g.fillStyle(0xffe4a0).fillRect(x + 1, y - 2 * i, 4, 1);
      }
    }
  }

  private helmet(g: Phaser.GameObjects.Graphics, dip: number) {
    const y = 119 + dip;
    g.fillStyle(0x573c41).fillRect(132, y + 6, 26, 6).fillRect(136, y, 17, 9);
    g.fillStyle(0xedab50).fillRect(135, y + 4, 21, 5).fillRect(138, y + 1, 13, 5);
    g.fillStyle(0xffdc77).fillRect(136, y + 5, 15, 3).fillRect(142, y + 1, 4, 6);
    g.fillStyle(0xbd7c43).fillRect(133, y + 10, 24, 2);
    g.fillStyle(0xedead0).fillRect(152, y + 5, 4, 3);
  }
}

export function mountMoonMine(parent: HTMLElement, initial: WorldView): WorldHandle {
  parent.classList.add("moon-mine");
  const scene = new MoonMine(initial);
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    width: mineSize.width,
    height: mineSize.height,
    pixelArt: true,
    backgroundColor: "#171a35",
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
      parent.classList.remove("moon-mine");
    },
  };
}
