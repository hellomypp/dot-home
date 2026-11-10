import Phaser from "phaser";
import { assetBase, nativeCharacter, type NativeCharacter } from "./spaces";
import { mountObservationScene } from "./observationScene";
import { makeClassroom } from "./classroomArt";
import type { WorldView } from "./types";

const seats = [
  { x: 64, y: 143, art: "child", tint: 0xc7dce3 },
  { x: 145, y: 143, art: "adult_male", tint: 0xeac8dd },
  { x: 224, y: 143, art: "child", tint: 0xf5dbb7 },
  { x: 82, y: 197, art: "child", tint: 0xe8c4ae },
  { x: 160, y: 197, art: "child", tint: 0xffffff },
  { x: 242, y: 197, art: "adult_female", tint: 0xc5ddd0 },
] satisfies { x: number; y: number; art: NativeCharacter; tint: number }[];
const mainSeat = 4;
const chalk = [
  [129, 47, 135, 39], [135, 39, 141, 47], [131, 44, 139, 44],
  [149, 43, 159, 43], [154, 39, 154, 47],
  [168, 39, 168, 47], [168, 39, 175, 40], [175, 40, 175, 43], [175, 43, 168, 44], [168, 44, 177, 45], [177, 45, 175, 48], [175, 48, 168, 47],
  [130, 63, 177, 63], [190, 53, 209, 53], [209, 53, 215, 65], [215, 65, 186, 65], [186, 65, 190, 53],
];

class Classroom extends Phaser.Scene {
  private pupils: Phaser.GameObjects.Image[] = [];
  private teacher?: Phaser.GameObjects.Image;
  private animation?: Phaser.GameObjects.Graphics;
  private elapsed = 0;

  constructor(private view: WorldView) { super("sunlit-classroom"); }

  preload() {
    for (const style of ["adult_female", "adult_male", "child"])
      this.load.spritesheet(style, `${assetBase}assets/characters/${style}.png`, { frameWidth: 24, frameHeight: 40 });
  }

  create() {
    this.textures.addCanvas("classroom-art", makeClassroom());
    this.add.image(0, 0, "classroom-art").setOrigin(0);
    this.teacher = this.add.image(238, 111, "adult_female", 4).setOrigin(0.5, 1);
    for (const [index, seat] of seats.entries()) {
      const { x, y } = seat;
      this.add.graphics().fillStyle(0x9a6d51).fillRect(x - 14, y - 19, 28, 15)
        .fillStyle(0xd2a778).fillRect(x - 12, y - 17, 24, 11).setDepth(y - 3);
      const pupil = this.add.image(x, y - 3, seat.art, 0).setOrigin(0.5, 1)
        .setScale(index === mainSeat ? 1.18 : 1).setDepth(y - 2).setTint(seat.tint);
      this.pupils.push(pupil);
      const desk = this.add.graphics().setDepth(y);
      desk.fillStyle(0x875b45).fillRect(x - 22, y - 7, 44, 21);
      desk.fillStyle(0xe0b481).fillRect(x - 21, y - 7, 42, 14);
      desk.fillStyle(0xf1cc98).fillRect(x - 19, y - 6, 38, 2);
      desk.fillStyle(0xb08258).fillRect(x - 21, y + 8, 42, 3);
      desk.fillStyle(0x79553f).fillRect(x - 19, y + 12, 4, 7).fillRect(x + 15, y + 12, 4, 7);
      desk.fillStyle(index === mainSeat ? 0x6d9583 : 0xb77c61).fillRect(x - 9, y - 5, 17, 10);
      desk.fillStyle(0xfff0cc).fillRect(x - 8, y - 5, 14, 8);
      desk.fillStyle(0xd5bea0).fillRect(x - 2, y - 4, 1, 6).fillRect(x, y - 2, 4, 1);
    }
    this.animation = this.add.graphics().setDepth(400);
    this.setView(this.view);
  }

  setView(view: WorldView) {
    this.view = view;
    const character = view.members.find((m) => m.id === (view.observedMemberId || view.ownMemberId));
    this.pupils[mainSeat]?.setVisible(Boolean(character)).setTexture(nativeCharacter(character?.characterStyle), 0);
    if (this.elapsed === 0 || view.reducedMotion) this.paint(0);
  }

  update(_time: number, delta: number) {
    if (this.view.reducedMotion) return;
    this.elapsed += Math.min(delta, 50);
    this.paint(this.elapsed);
  }

  private paint(elapsed: number) {
    if (!this.animation || !this.teacher) return;
    const g = this.animation.clear();
    const cycle = elapsed % 8600;
    const writing = elapsed > 0 && cycle < 4100;
    const markTime = cycle / (4100 / chalk.length);
    const completed = writing ? Math.floor(markTime) : chalk.length;
    for (const [x, y, x2, y2] of chalk.slice(0, completed))
      g.lineStyle(1, 0xe1e8c6).lineBetween(x, y, x2, y2);
    if (writing) {
      const [x, y, x2, y2] = chalk[completed];
      const fraction = markTime - completed;
      const tipX = Math.round(Phaser.Math.Linear(x, x2, fraction));
      const tipY = Math.round(Phaser.Math.Linear(y, y2, fraction));
      const teacherX = 155 + Math.round((cycle / 4100) * 81);
      this.teacher.setFrame(4).setPosition(teacherX, 99);
      g.lineStyle(1, 0xe1e8c6).lineBetween(x, y, tipX, tipY);
      g.lineStyle(2, 0xac8767).lineBetween(teacherX - 6, this.teacher.y - 17, tipX, tipY + 2);
      g.fillStyle(0xfff2cf).fillRect(tipX - 1, tipY, 3, 2);
      if (fraction > 0.6) g.fillStyle(0xe1e8c6, 0.64).fillRect(tipX - 3, tipY + 3, 2, 1);
    } else {
      this.teacher.setFrame(0).setPosition(245, 99);
    }

    for (const [index, { x, y }] of seats.entries()) {
      const beat = (elapsed + index * 535) % 2900;
      const writingNote = elapsed > 0 && beat > 720 && beat < 2060;
      const listening = elapsed > 0 && beat >= 2060 && beat < 2590;
      this.pupils[index].setFrame(writingNote ? 1 : listening ? (x <= this.teacher.x ? 8 : 4) : 0);
      const wobble = writingNote ? Math.round(Math.sin(beat / 75) * 2) : 0;
      g.lineStyle(2, 0x714e42).lineBetween(x + 5 + wobble, y - 6, x + 1 + wobble, y + 1);
      g.lineStyle(1, 0xc79553).lineBetween(x + 5 + wobble, y - 6, x + 2 + wobble, y - 1);
      if (writingNote) g.fillStyle(0xad9276).fillRect(x + 1, y, 2 + Math.floor((beat - 720) / 340), 1);
    }
    for (let i = 0; i < 6; i++) {
      const x = 22 + (i * 13 + Math.floor(elapsed / 115)) % 70;
      const y = 111 + (i * 11) % 45;
      g.fillStyle(0xffe7b2, 0.45).fillRect(x, y, 2, 1);
    }
  }
}

export function mountClassroom(parent: HTMLElement, view: WorldView) {
  return mountObservationScene(parent, new Classroom(view), "sunlit-classroom", "#eee1bf");
}
