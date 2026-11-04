import { Check, Fish, X } from "lucide-react";
import { catalogue, mapFor, type SpaceDesign } from "../game/spaces";
import { SpacePreview } from "./SpacePreview";

export function TankPlacement({ design, occupied, selected, onSelect, busy }: {
  design?: SpaceDesign;
  occupied: ReadonlySet<number>;
  selected: number | null;
  onSelect: (slot: number) => void;
  busy: boolean;
}) {
  const map = mapFor(design?.presetId);
  const { columns, rows } = catalogue.tankGrid;
  const [, , width, height] = map.previewCrop;
  return (
    <fieldset className="tank-placement">
      <legend>수조 놓기</legend>
      <p>집 그림에서 빈 칸을 눌러 수조 자리를 골라 주세요.</p>
      <div className="tank-board" style={{ aspectRatio: `${width} / ${height}` }}>
        <SpacePreview design={design} />
        <div className="tank-grid" style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}>
          {Array.from({ length: columns * rows }, (_, slot) => {
            const used = occupied.has(slot) && map.tankSlots.includes(slot);
            const available = map.tankSlots.includes(slot) && !used;
            const row = Math.floor(slot / columns) + 1;
            const column = slot % columns + 1;
            if (!available) return (
              <span className={used ? "tank-used" : "tank-blocked"} key={slot} aria-hidden="true">
                {used ? <Fish size={16} /> : row >= 3 && row <= 5 ? <X size={12} /> : null}
              </span>
            );
            return (
              <button key={slot} type="button" aria-label={`수조 위치 ${row}행 ${column}열`}
                aria-pressed={slot === selected} disabled={busy} onClick={() => onSelect(slot)}>
                {slot === selected ? <Fish size={20} aria-hidden="true" /> : <Check size={15} aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      </div>
      <div className="tank-legend">
        <span><Check size={14} aria-hidden="true" /> 놓을 수 있음</span>
        <span><X size={14} aria-hidden="true" /> 놓을 수 없음</span>
        <span><Fish size={14} aria-hidden="true" /> 다른 수조</span>
      </div>
      {!map.tankSlots.some((slot) => !occupied.has(slot)) && <p role="status">빈 수조 자리가 없어요.</p>}
      {selected !== null && <p role="status" className="tank-selection">
        {occupied.has(selected) ? "이미 수조가 놓인 칸이에요. 다른 자리를 골라 주세요." : "수조 자리를 골랐어요."}
      </p>}
    </fieldset>
  );
}
