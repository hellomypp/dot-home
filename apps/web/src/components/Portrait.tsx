import { assetBase, nativeCharacter } from "../game/spaces";
export function Portrait({
  small = false,
  appearance,
}: {
  small?: boolean;
  appearance?: string;
  color?: string;
}) {
  return (
    <span className={`portrait ${small ? "small" : ""}`} aria-hidden="true">
      <span
        className="native-portrait"
        style={{
          backgroundImage: `url(${assetBase}assets/characters/${nativeCharacter(appearance)}.png)`,
        }}
      />
    </span>
  );
}
