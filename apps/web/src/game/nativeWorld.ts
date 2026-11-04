import Phaser from "phaser";
import { ActorNameplate } from "./ActorNameplate";
import { paintWindowSky } from "./windowSky";
import { makeCat } from "./art";
import {
  assetBase,
  catalogue,
  mapFor,
  defaultDesign,
  fridgeMagnet,
  nativeCharacter,
  nearest,
  path,
  canStand,
  occludes,
  fade,
  type MapObject,
  type Point,
  type RuntimeMap,
} from "./spaces";
import type { WorldView, WorldEvent, WorldHandle } from "./types";
type Actor = {
  id: string;
  sprite: Phaser.GameObjects.Image;
  label: ActorNameplate;
  bubble: Phaser.GameObjects.Text;
  path: Point[];
  direction: number;
  frame: number;
  kind: string;
  pet: boolean;
  remote: string;
  manual: number;
  cycle: number;
  after?: () => void;
};
const typing = () =>
  !!document.activeElement?.matches(
    "input,textarea,select,[contenteditable=true]",
  ) || !!document.querySelector("dialog[open]");
const logicalTargets: Record<
  string,
  "fridge" | "calendar" | "sofa" | "table" | "entrance" | "pet_rest"
> = {
  fridge: "fridge",
  calendar: "calendar",
  sofa: "sofa",
  dining_table: "table",
  entrance: "entrance",
  pet_rest: "pet_rest",
};
class NativeHome extends Phaser.Scene {
  view: WorldView;
  emit: (e: WorldEvent) => void;
  map!: RuntimeMap;
  actors = new Map<string, Actor>();
  pets = new Map<string, Actor>();
  keys = new Set<string>();
  objects: { meta: MapObject; image: Phaser.GameObjects.Image }[] = [];
  markers: Phaser.GameObjects.GameObject[] = [];
  lastInput = 0;
  designKey = "";
  skyKey = "";
  windowTexture!: Phaser.Textures.CanvasTexture;
  constructor(view: WorldView, emit: (e: WorldEvent) => void) {
    super("native-home");
    this.view = view;
    this.emit = emit;
  }
  preload() {
    const files = new Set<string>();
    for (const m of Object.values(catalogue.maps)) {
      files.add(m.backdrop);
      for (const o of m.objects) files.add(o.sprite);
    }
    for (const file of files) this.load.image(file, file);
    for (const kind of ["adult_female", "adult_male", "child"])
      this.load.spritesheet(
        kind,
        assetBase + "assets/characters/" + kind + ".png",
        { frameWidth: 24, frameHeight: 40 },
      );
    this.load.spritesheet(
      "native-dog-rest",
      assetBase + "assets/pets/small_dog_states.png",
      { frameWidth: 20, frameHeight: 16 },
    );
    this.load.spritesheet(
      "native-dog",
      assetBase + "assets/pets/small_dog_walk.png",
      { frameWidth: 20, frameHeight: 16 },
    );
  }
  create() {
    // No demo spawns are instantiated: every actor comes from authenticated World data.
    for (let f = 0; f < 4; f++) {
      const canvas = document.createElement("canvas");
      canvas.width = 20;
      canvas.height = 16;
      const c = canvas.getContext("2d")!;
      c.imageSmoothingEnabled = false;
      c.drawImage(makeCat(f, "#c79765"), 0, 0, 20, 16);
      this.textures.addCanvas("native-cat-" + f, canvas);
    }
    this.windowTexture = this.textures.createCanvas("window-sky", 52, 37)!;
    this.draw();
    this.sync();
    this.input.on("pointerdown", (p: Phaser.Input.Pointer) => {
      if (typing() || !this.canControl()) return;
      const q = p.positionToCamera(this.cameras.main) as Phaser.Math.Vector2;
      this.move(
        this.actors.get(this.view.ownMemberId)!,
        nearest(q, this.map),
        true,
      );
    });
    const down = (e: KeyboardEvent) => {
      if (typing() || !this.canControl()) return;
      if (
        [
          "ArrowLeft",
          "ArrowRight",
          "ArrowUp",
          "ArrowDown",
          "w",
          "a",
          "s",
          "d",
        ].includes(e.key)
      ) {
        e.preventDefault();
        this.keys.add(e.key);
      }
    };
    const up = (e: KeyboardEvent) => this.keys.delete(e.key),
      blur = () => this.keys.clear(),
      visible = () => {
        this.keys.clear();
        if (document.hidden) this.game.loop.sleep();
        else {
          this.game.loop.wake();
          this.emit({ type: "visibility-resume" });
        }
      };
    window.addEventListener("keydown", down);
    window.addEventListener("keyup", up);
    window.addEventListener("blur", blur);
    document.addEventListener("visibilitychange", visible);
    this.events.once("shutdown", () => {
      window.removeEventListener("keydown", down);
      window.removeEventListener("keyup", up);
      window.removeEventListener("blur", blur);
      document.removeEventListener("visibilitychange", visible);
    });
    this.emit({ type: "ready" });
  }
  canControl() {
    return (
      (!this.view.observedMemberId ||
        this.view.observedMemberId === this.view.ownMemberId) &&
      this.actors.has(this.view.ownMemberId)
    );
  }
  draw() {
    this.children.removeAll(true);
    this.actors.clear();
    this.pets.clear();
    this.objects = [];
    this.markers = [];
    const design = this.view.design || defaultDesign;
    this.map = mapFor(design.presetId);
    this.designKey = JSON.stringify(design);
    this.add.image(0, 0, this.map.backdrop).setOrigin(0, 0).setDepth(-1000);
    const magnet = fridgeMagnet(design);
    const add = (o: MapObject) => {
      const image = this.add
        .image(o.x, o.y, o.sprite)
        .setOrigin(0, 0)
        .setDepth(
          o.layer === "ground"
            ? -900
            : o.layer === "wall"
              ? -700
              : o.depth,
        );
      if (o.id === "window" || o.id === "window_b") {
        this.add.image(o.x, o.y, "window-sky")
          .setOrigin(0, 0)
          .setDepth(image.depth + 1);
      }
      if (o.occlusion) this.objects.push({ meta: o, image });
      if (o.interactive) {
        image.setInteractive({
          hitArea: new Phaser.Geom.Rectangle(
            (o.width - Math.max(44, o.width)) / 2,
            (o.height - Math.max(44, o.height)) / 2,
            Math.max(44, o.width),
            Math.max(44, o.height),
          ),
          hitAreaCallback: Phaser.Geom.Rectangle.Contains,
          useHandCursor: true,
        });
        image.on(
          "pointerdown",
          (
            _p: unknown,
            _x: unknown,
            _y: unknown,
            event: Phaser.Types.Input.EventData,
          ) => {
            event.stopPropagation();
            if (typing()) return;
            const target = logicalTargets[o.id],
              act = () =>
                this.emit({
                  type: "interact",
                  target,
                  label: {
                    fridge: "냉장고 열기",
                    calendar: "가족 달력",
                    sofa: "소파 곁에서 잠깐 쉬어요.",
                    table: "같이 먹고 싶은 음식이 있나요?",
                    entrance: "가족 초대",
                    pet_rest: "반려동물 관리",
                  }[target],
                });
            if (this.canControl()) {
              const actor = this.actors.get(this.view.ownMemberId)!;
              this.move(
                actor,
                { x: o.interactionAnchor![0], y: o.interactionAnchor![1] },
                true,
              );
              if (actor.path.length) actor.after = act;
              else act();
            } else act();
          },
        );
      }
      if (o.id === "fridge" && magnet !== undefined) {
        this.add
          .rectangle(o.x + 20, o.y + 20, 4, 4, magnet)
          .setDepth(o.depth + 0.1);
        this.add
          .rectangle(o.x + 20, o.y + 20, 2, 8, magnet)
          .setDepth(o.depth + 0.1);
      }
    };
    this.map.objects.forEach(add);
    this.updateWindowSky();
    this.cameras.main
      .setBounds(0, 0, 448, 320)
      .setRoundPixels(true)
      .setBackgroundColor("#e8d9bf");
  }
  actor(
    id: string,
    name: string,
    kind: string,
    point: Point,
    pet = false,
  ): Actor {
    const texture = pet
      ? kind === "dog"
        ? "native-dog"
        : "native-cat-0"
      : kind;
    if (pet && kind !== "dog")
      for (let f = 0; f < 4; f++)
        if (!this.textures.exists("native-cat-" + f)) {
          const canvas = document.createElement("canvas");
          canvas.width = 20;
          canvas.height = 16;
          const c = canvas.getContext("2d")!;
          c.imageSmoothingEnabled = false;
          c.drawImage(makeCat(f, "#c79765"), 0, 0, 20, 16);
          this.textures.addCanvas("native-cat-" + f, canvas);
        }
    const p = nearest(point, this.map, pet),
      sprite = this.add.image(p.x, p.y, texture).setOrigin(0.5, 1),
      label = new ActorNameplate(
        this,
        name,
        pet ? "pet" : id === this.view.ownMemberId ? "own" : "member",
        11000,
      ).setPosition(p.x, p.y + 4),
      bubble = this.add
        .text(0, 0, "", {
          fontFamily: "sans-serif",
          fontSize: "10px",
          color: "#44251b",
          backgroundColor: "#fff6dc",
          wordWrap: { width: 110 },
          padding: { x: 5, y: 3 },
        })
        .setOrigin(0.5, 1)
        .setDepth(11002)
        .setVisible(false);
    return {
      id,
      sprite,
      label,
      bubble,
      path: [],
      direction: 0,
      frame: 0,
      kind,
      pet,
      remote: "",
      manual: Date.now(),
      cycle: -1,
    };
  }
  sync() {
    for (const [id, a] of this.actors)
      if (!this.view.members.some((m) => m.id === id && m.place === "home")) {
        a.sprite.destroy();
        a.label.destroy();
        a.bubble.destroy();
        this.actors.delete(id);
      }
    for (const m of this.view.members.filter((m) => m.place === "home")) {
      const kind = nativeCharacter(m.characterStyle);
      let a = this.actors.get(m.id);
      if (!a) {
        const [x, y] = this.map.spawns[0];
        a = this.actor(m.id, m.name, kind, { x: m.x ?? x, y: m.y ?? y });
        this.actors.set(m.id, a);
      }
      a.kind = kind;
      a.label.setLabel(m.name);
      a.bubble.setText(m.status || "").setVisible(!!m.status);
      const key = `${m.x},${m.y}`;
      if (a.remote !== key && m.x !== undefined && m.y !== undefined) {
        a.remote = key;
        a.manual = Date.now();
        this.move(a, { x: m.x, y: m.y }, false);
      }
    }
    for (const [id, a] of this.pets)
      if (!this.view.pets.some((p) => p.id === id)) {
        a.sprite.destroy();
        a.label.destroy();
        a.bubble.destroy();
        this.pets.delete(id);
      }
    for (const [i, p] of this.view.pets.entries()) {
      let a = this.pets.get(p.id);
      if (!a) {
        const rest = this.map.petSpawn;
        a = this.actor(
          p.id,
          p.name,
          p.kind || "cat",
          { x: rest[0] + i * 12, y: rest[1] },
          true,
        );
        this.pets.set(p.id, a);
      }
      a.kind = p.kind || "cat";
      a.label.setLabel(p.name);
    }
    for (const marker of this.markers) marker.destroy();
    this.markers = [];
    const notes =
      this.view.objectNotes?.filter((n) => n.objectId === "fridge") || [];
    if (notes.length) {
      const fridge = this.map.objects.find((o) => o.id === "fridge")!,
        marker = this.add
          .rectangle(fridge.x + 16, fridge.y + 24, 8, 8, 0xffe7a5)
          .setStrokeStyle(1, 0x8b4a25)
          .setDepth(fridge.depth + 0.2)
          .setInteractive({
            hitArea: new Phaser.Geom.Rectangle(-18, -18, 44, 44),
            hitAreaCallback: Phaser.Geom.Rectangle.Contains,
            useHandCursor: true,
          });
      marker.on(
        "pointerdown",
        (
          _p: unknown,
          _x: unknown,
          _y: unknown,
          e: Phaser.Types.Input.EventData,
        ) => {
          e.stopPropagation();
          this.emit({
            type: "interact",
            target: "fridge",
            label: "냉장고 쪽지",
          });
        },
      );
      this.markers.push(marker);
    }
  }
  updateWindowSky() {
    const sky = this.view.windowSky;
    const key = JSON.stringify([this.map.id, sky]);
    if (this.skyKey === key) return;
    this.skyKey = key;
    const context = this.windowTexture.getContext();
    if (sky) paintWindowSky(context, this.map.id, sky);
    else context.clearRect(0, 0, 52, 37);
    this.windowTexture.refresh();
  }

  setView(view: WorldView) {
    this.view = view;
    if (!this.sys.isActive()) return;
    if (this.designKey !== JSON.stringify(view.design || defaultDesign))
      this.draw();
    else this.updateWindowSky();
    this.sync();
  }
  move(a: Actor, to: Point, manual: boolean) {
    a.path = path({ x: a.sprite.x, y: a.sprite.y }, to, this.map, a.pet);
    a.after = undefined;
    if (manual) {
      a.manual = Date.now();
      const end = a.path.at(-1);
      if (end) {
        // Acknowledge the expected snapshot so our own STOMP echo cannot cancel
        // the pending interaction callback or restart the same route.
        a.remote = `${end.x},${end.y}`;
        this.emit({ type: "move-intent", x: end.x, y: end.y });
      }
    }
  }
  update(time: number, delta: number) {
    const own = this.actors.get(this.view.ownMemberId),
      now = Date.now();
    if (
      own &&
      this.keys.size &&
      this.canControl() &&
      !typing() &&
      now - this.lastInput > 180
    ) {
      const dx =
          (this.keys.has("ArrowRight") || this.keys.has("d") ? 1 : 0) -
          (this.keys.has("ArrowLeft") || this.keys.has("a") ? 1 : 0),
        dy =
          (this.keys.has("ArrowDown") || this.keys.has("s") ? 1 : 0) -
          (this.keys.has("ArrowUp") || this.keys.has("w") ? 1 : 0);
      const to = { x: own.sprite.x + dx * 8, y: own.sprite.y + dy * 8 };
      if (canStand(to, this.map)) this.move(own, to, true);
      this.lastInput = now;
    }
    const actors = [...this.actors.values(), ...this.pets.values()];
    for (const a of actors) {
      const cycle = Math.floor(now / (a.pet ? 17000 : 14000));
      if (
        !this.view.reducedMotion &&
        now - a.manual > 12000 &&
        a.cycle !== cycle
      ) {
        a.cycle = cycle;
        const goals = [
          ...this.map.spawns,
          ...(a.pet ? [this.map.petSpawn] : []),
          ...this.map.objects
            .filter((o) => o.interactive)
            .map((o) => o.interactionAnchor!),
        ];
        const q =
          goals[
            (cycle + [...a.id].reduce((x, c) => x + c.charCodeAt(0), 0)) %
              goals.length
          ];
        this.move(a, { x: q[0], y: q[1] }, false);
      }
      let remaining = (Math.min(delta, 50) / 1000) * (a.pet ? 28 : 50),
        dx = 0,
        dy = 0;
      while (a.path.length && remaining > 0) {
        const to = a.path[0],
          x = to.x - a.sprite.x,
          y = to.y - a.sprite.y,
          dist = Math.hypot(x, y);
        const reached = dist <= remaining;
        const step = reached ? dist : remaining;
        const next = reached ? to : {
          x: a.sprite.x + (x / dist) * step,
          y: a.sprite.y + (y / dist) * step,
        };
        if (!canStand(next, this.map, a.pet)) {
          a.path = [];
          break;
        }
        dx += next.x - a.sprite.x;
        dy += next.y - a.sprite.y;
        a.sprite.setPosition(next.x, next.y);
        remaining -= step;
        if (reached) a.path.shift();
      }
      if (Math.abs(dx) + Math.abs(dy) > 0.01) {
        a.direction =
          Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? 1 : 2) : dy < 0 ? 3 : 0;
        a.frame = [1, 2, 3, 2][Math.floor(time / 150) % 4];
      } else {
        a.frame = 0;
        if (a.after) {
          const callback = a.after;
          a.after = undefined;
          callback();
        }
      }
      if (a.pet && a.kind !== "dog")
        a.sprite.setTexture("native-cat-" + a.frame);
      else if (a.pet && a.frame === 0 && now - a.manual > 8000)
        a.sprite.setTexture(
          "native-dog-rest",
          a.direction * 2 + (Math.floor(now / 30000) % 2),
        );
      else
        a.sprite.setTexture(
          a.pet ? "native-dog" : a.kind,
          a.direction * 4 + a.frame,
        );
      a.sprite.setDepth(Math.round(a.sprite.y));
      a.label.setPosition(Math.round(a.sprite.x), Math.round(a.sprite.y + 4));
      a.bubble.setPosition(Math.round(a.sprite.x), Math.round(a.sprite.y - 42));
    }
    for (const o of this.objects) {
      const target = actors.some((a) =>
        occludes(o.meta, {
          x: a.sprite.x,
          y: a.sprite.y,
          pet: a.pet,
          kind: a.kind,
        }),
      )
        ? o.meta.occlusion!.alpha
        : 1;
      o.image.setAlpha(
        fade(
          o.image.alpha,
          target,
          Math.min(50, delta) / 1000,
          target < o.image.alpha
            ? o.meta.occlusion!.fadeMs
            : o.meta.occlusion!.restoreMs,
        ),
      );
    }
    const target = this.actors.get(this.view.observedMemberId || this.view.ownMemberId);
    this.cameras.main.setZoom(this.view.closeView ? 2 : 1);
    if(target)this.cameras.main.centerOn(Math.round(target.sprite.x),Math.round(target.sprite.y));
  }
}
export function mountNativeWorld(
  parent: HTMLElement,
  initial: WorldView,
  onEvent: (e: WorldEvent) => void,
): WorldHandle {
  parent.classList.add("native-world");
  const scene = new NativeHome(initial, onEvent),
    game = new Phaser.Game({
      type: Phaser.AUTO,
      parent,
      width: 320,
      height: 256,
      pixelArt: true,
      roundPixels: true,
      antialias: false,
      scene,
      scale: { mode: Phaser.Scale.NONE, zoom: 1 },
      fps: { target: 30 },
      audio: { noAudio: true },
      render: { preserveDrawingBuffer: true },
    });
  const mobileLayout = window.matchMedia("(max-width: 760px)");
  const resize = () => {
    const mobile = mobileLayout.matches;
    const height = mobile ? 300 : 256;
    const width = mobile ? Math.max(1, parent.clientWidth) : 320;
    const zoom = mobile ? 1 : Math.max(1, Math.min(2, Math.floor(parent.clientWidth / width)));
    parent.style.height = height * zoom + "px";
    if (game.isBooted) {
      const size = game.scale.gameSize;
      if (size.width !== width || size.height !== height) game.scale.resize(width, height);
      if (game.scale.zoom !== zoom) game.scale.setZoom(zoom);
    }
  };
  // Layout can shift after the canvas loads. Refresh Phaser's page offset
  // before it translates a tap or click into scene coordinates.
  const refreshInputBounds = (event: Event) => {
    if (event.target === game.canvas) game.scale.updateBounds();
  };
  parent.addEventListener("touchstart", refreshInputBounds, { capture: true, passive: true });
  parent.addEventListener("mousedown", refreshInputBounds, true);
  const observer = new ResizeObserver(resize);
  observer.observe(parent);
  mobileLayout.addEventListener("change", resize);
  game.events.once("ready", resize);
  resize();
  return {
    update: (view) => scene.setView(view),
    destroy: () => {
      observer.disconnect();
      mobileLayout.removeEventListener("change", resize);
      parent.removeEventListener("touchstart", refreshInputBounds, true);
      parent.removeEventListener("mousedown", refreshInputBounds, true);
      game.destroy(true);
      parent.style.height = "";
      parent.classList.remove("native-world");
    },
  };
}
