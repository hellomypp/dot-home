import Phaser from "phaser";

type NameplateKind = "own" | "member" | "pet";

const colors = {
  own: { fill: 0x324d39, border: 0xe2d399, text: "#fffdf2" },
  member: { fill: 0xfff8e9, border: 0x82684e, text: "#36291e" },
  pet: { fill: 0xf5e4c7, border: 0x9c7853, text: "#493522" },
};

export class ActorNameplate extends Phaser.GameObjects.Container {
  private readonly background: Phaser.GameObjects.Graphics;
  private readonly nameText: Phaser.GameObjects.Text;
  private readonly badge?: Phaser.GameObjects.Text;
  private readonly hitArea = new Phaser.Geom.Rectangle();
  private fullName?: string;

  constructor(scene: Phaser.Scene, name: string, private readonly kind: NameplateKind, depth: number,
    private readonly reveal?: (name: string) => void) {
    super(scene);
    scene.add.existing(this);
    const palette = colors[kind];
    this.background = scene.add.graphics();
    this.nameText = scene.add.text(0, 0, "", {
      fontFamily: "sans-serif",
      fontSize: kind === "pet" ? "10px" : "11px",
      fontStyle: kind === "pet" ? "normal" : "bold",
      color: palette.text,
      ...(kind === "pet" ? { wordWrap: { width: 108, useAdvancedWrap: true }, maxLines: 2 } : {}),
    });
    this.add([this.background, this.nameText]);

    if (kind === "own") {
      this.badge = scene.add.text(0, 0, "나", {
        fontFamily: "sans-serif",
        fontSize: "10px",
        fontStyle: "bold",
        color: "#263e2e",
        padding: { x: 3, y: 0 },
      });
      this.add(this.badge);
    }
    this.setDepth(depth + (kind === "own" ? 1 : 0));
    if (reveal) {
      this.setInteractive({ hitArea: this.hitArea, hitAreaCallback: Phaser.Geom.Rectangle.Contains, useHandCursor: true });
      this.on("pointerdown", (_pointer: unknown, _x: number, _y: number, event: Phaser.Types.Input.EventData) => {
        event.stopPropagation();
        if (this.fullName !== undefined) reveal(this.fullName);
      });
    }
    this.setLabel(name);
  }

  setLabel(name: string) {
    if (this.fullName === name) return;
    this.fullName = name;
    this.nameText.setText(name);
    if (this.reveal && this.nameText.width > 76) {
      const glyphs = Array.from(new Intl.Segmenter().segment(name), ({ segment }) => segment);
      const ctx = this.nameText.context;
      while (glyphs.length && ctx.measureText(glyphs.join("") + "…").width > 76) glyphs.pop();
      this.nameText.setText(glyphs.join("") + "…");
    }
    if (this.input) this.input.enabled = this.nameText.text !== name;
    const padding = this.kind === "pet" ? 4 : 5;
    const badgeWidth = this.badge ? this.badge.width + 5 : 0;
    const width = this.nameText.width + 2 * padding + badgeWidth;
    const height = Math.max(this.nameText.height, this.badge?.height ?? 0) + 4;
    const left = -Math.floor(width / 2);
    this.hitArea.setTo(left, 0, width, height);
    const palette = colors[this.kind];

    this.background.clear()
      .fillStyle(0x2e2118, 0.32).fillRoundedRect(left, 2, width, height, 4)
      .fillStyle(palette.fill).fillRoundedRect(left, 0, width, height, 4)
      .lineStyle(1, palette.border).strokeRoundedRect(left, 0, width, height, 4);
    this.nameText.setPosition(left + padding, 2);

    if (this.badge) {
      const x = left + width - padding - this.badge.width;
      const y = Math.floor((height - this.badge.height) / 2);
      this.background.fillStyle(0xe2d399).fillRoundedRect(x, y, this.badge.width, this.badge.height, 3);
      this.badge.setPosition(x, y);
    }
  }
}
