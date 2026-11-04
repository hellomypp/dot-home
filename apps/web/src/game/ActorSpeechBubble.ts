import Phaser from "phaser";

export class ActorSpeechBubble extends Phaser.GameObjects.Container {
  private readonly frame: Phaser.GameObjects.Graphics;
  private readonly caption: Phaser.GameObjects.Text;
  private readonly hitArea = new Phaser.Geom.Rectangle();
  private speech = "";
  private clipped = false;
  private bubbleWidth = 0;
  private bubbleHeight = 0;
  private tailX = Number.NaN;
  private below = false;

  constructor(scene: Phaser.Scene, private readonly own: boolean, depth: number, reveal: (text: string) => void) {
    super(scene);
    scene.add.existing(this);
    this.frame = scene.add.graphics();
    this.caption = scene.add.text(8, 5, "", {
      fontFamily: "sans-serif",
      fontSize: "11px",
      color: "#302e23",
      lineSpacing: 2,
      wordWrap: { width: 114, useAdvancedWrap: true },
    });
    this.add([this.frame, this.caption]);
    this.setDepth(depth + (own ? 1 : 0)).setVisible(false);
    this.setInteractive({ hitArea: this.hitArea, hitAreaCallback: Phaser.Geom.Rectangle.Contains, useHandCursor: true });
    this.input!.enabled = false;
    this.on("pointerdown", (_pointer: unknown, _x: number, _y: number, event: Phaser.Types.Input.EventData) => {
      event.stopPropagation();
      reveal(this.speech);
    });
  }

  setSpeech(text: string) {
    if (text === this.speech) return;
    this.speech = text;
    this.clipped = this.caption.getWrappedText(text).length > 3;
    if (this.clipped) {
      const glyphs = Array.from(new Intl.Segmenter().segment(text), ({ segment }) => segment);
      let first = 0;
      let last = glyphs.length;
      while (first < last) {
        const mid = Math.ceil((first + last) / 2);
        const candidate = glyphs.slice(0, mid).join("").trimEnd() + "…";
        if (this.caption.getWrappedText(candidate).length <= 3) first = mid;
        else last = mid - 1;
      }
      text = glyphs.slice(0, first).join("").trimEnd() + "…";
    }
    this.caption.setText(text);
    this.bubbleWidth = Math.max(36, this.caption.width + 16);
    this.bubbleHeight = this.caption.height + 10;
    this.hitArea.setTo(0, 0, this.bubbleWidth, this.bubbleHeight);
    this.tailX = Number.NaN;
    if (!text) this.setVisible(false);
  }

  follow(x: number, y: number, bounds: Phaser.Geom.Rectangle, headHeight: number) {
    const onScreen = Boolean(this.speech) && bounds.contains(x, y - headHeight / 2);
    this.setVisible(onScreen);
    this.input!.enabled = onScreen && this.clipped;
    if (!onScreen) return;

    const w = this.bubbleWidth;
    const h = this.bubbleHeight;
    const left = Math.round(Phaser.Math.Clamp(x - w / 2, bounds.left + 5, bounds.right - w - 5));
    const preferredTop = y - headHeight - 6 - h;
    const below = preferredTop < bounds.top + 5 && y + 27 + h <= bounds.bottom - 5;
    const top = Phaser.Math.Clamp(below ? y + 27 : preferredTop, bounds.top + 5, bounds.bottom - h - 5);
    this.setPosition(left, Math.round(top));
    const tailX = Math.round(Phaser.Math.Clamp(x - left, 9, w - 9));
    if (tailX !== this.tailX || below !== this.below) this.paint(tailX, below);
  }

  private paint(tailX: number, below: boolean) {
    this.tailX = tailX;
    this.below = below;
    const w = this.bubbleWidth;
    const h = this.bubbleHeight;
    const edge = below ? 0 : h;
    const tip = below ? -5 : h + 5;
    const border = this.own ? 0x596b47 : 0x78624a;
    const paper = this.own ? 0xf5f5dd : 0xfff6e5;
    const point = (x: number, y: number) => new Phaser.Math.Vector2(x, y);
    const corners = [
      point(3, 0), point(w - 3, 0), point(w, 3), point(w, h - 3),
      point(w - 3, h), point(3, h), point(0, h - 3), point(0, 3),
    ];
    const tail = [point(tailX - 4, edge), point(tailX, tip), point(tailX + 4, edge)];
    this.frame.clear()
      .fillStyle(0x3c2b1d, 0.26).fillRect(3, h, w - 6, 2)
      .fillStyle(paper).fillPoints(corners, true)
      .lineStyle(1, border).strokePoints(corners, true)
      .fillStyle(paper).fillPoints(tail, true)
      .lineStyle(1, border).strokePoints(tail);
  }
}
