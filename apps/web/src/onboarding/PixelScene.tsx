import { useEffect, useRef } from "react";
import { SpacePreview } from "../components/SpacePreview";
import { Portrait } from "../components/Portrait";
import { makeGarden } from "../game/art";
import { assetBase, nativeCharacter } from "../game/spaces";

// The story illustration remains code-native; its character uses the same sheet as the game.
function StoryGarden({
  appearance,
  showAvatar = true,
}: {
  appearance?: string;
  showAvatar?: boolean;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const c = ref.current?.getContext("2d");
    if (!c) return;
    let alive = true;
    c.imageSmoothingEnabled = false;
    c.clearRect(0, 0, 360, 310);
    c.drawImage(makeGarden(), 0, 0);
    if (showAvatar) {
      const person = new Image();
      person.onload = () => {
        if (alive) c.drawImage(person, 0, 0, 24, 40, 152, 220, 48, 80);
      };
      person.src =
        assetBase + "assets/characters/" + nativeCharacter(appearance) + ".png";
      const dog = new Image();
      dog.onload = () => {
        if (alive) c.drawImage(dog, 0, 0, 20, 16, 216, 270, 40, 32);
      };
      dog.src = assetBase + "assets/pets/small_dog_walk.png";
    }
    return () => {
      alive = false;
    };
  }, [appearance, showAvatar]);
  return (
    <canvas
      className="pixel-scene garden"
      ref={ref}
      width={360}
      height={310}
      role="img"
      aria-label="구름과 나무 사이, 작은 집 앞의 캐릭터와 반려동물 일러스트"
    />
  );
}
export function PixelScene(props: {
  kind: "garden" | "cottage" | "room";
  appearance?: string;
  showAvatar?: boolean;
  presetId?: string;
}) {
  if (props.kind === "garden") return <StoryGarden {...props} />;
  return (
    <div style={{ position: "relative" }}>
      <SpacePreview
        design={{
          presetId: props.presetId || "garden_home",
          version: 0,
          variants: {},
        }}
      />
      {props.kind === "room" && props.showAvatar !== false && (
        <div style={{ position: "absolute", left: "50%", bottom: 30 }}>
          <Portrait appearance={props.appearance} />
        </div>
      )}
    </div>
  );
}
