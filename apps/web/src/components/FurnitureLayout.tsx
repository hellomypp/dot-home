import { ArrowDown, ArrowLeft, ArrowRight, ArrowUp } from "lucide-react";
import { catalogue, mapFor, mapForDesign, tankPoint, type Point, type SpaceDesign } from "../game/spaces";

const names: Record<string, string> = {
  plant: "왼쪽 화분",
  plant_b: "오른쪽 화분",
  bookshelf: "책장",
  sofa: "소파",
  lamp: "스탠드",
  "coffee-table": "낮은 탁자",
  dining_table: "식탁",
  sideboard: "수납장",
};
const directions = [
  { x: 0, y: -1, name: "위로", Icon: ArrowUp },
  { x: -1, y: 0, name: "왼쪽으로", Icon: ArrowLeft },
  { x: 1, y: 0, name: "오른쪽으로", Icon: ArrowRight },
  { x: 0, y: 1, name: "아래로", Icon: ArrowDown },
];

function fits(design: SpaceDesign, id: string) {
  const map = mapForDesign(design);
  const collision = map.objects.find(object => object.id === id)?.collision;
  if (!collision) return false;
  const [x, y, width, height] = collision;
  const [left, top, floorWidth, floorHeight] = map.walkBounds;
  if (x < left || y < top || x + width > left + floorWidth || y + height > top + floorHeight) return false;
  const overlaps = ([bx, by, bw, bh]: number[]) =>
    x < bx + bw && x + width > bx && y < by + bh && y + height > by;
  return !map.objects.some(object => object.id !== id && object.collision && overlaps(object.collision)) &&
    !map.tankSlots.some(slot => {
      const tank = tankPoint(map, slot);
      return tank && overlaps([tank.x - 14, tank.y - 20, 28, 20]);
    });
}

export function FurnitureLayout({ design, selected, onSelect, onChange, disabled }: {
  design: SpaceDesign;
  selected: string;
  onSelect: (id: string) => void;
  onChange: (draft: SpaceDesign) => void;
  disabled: boolean;
}) {
  const options = mapFor(design.presetId).objects.filter(object => catalogue.movable.includes(object.id));
  const original = options.find(object => object.id === selected) ?? options[0];
  const id = original.id;
  const current = design.positions?.[id] ?? original;

  function candidate(position: Point) {
    const positions = { ...design.positions };
    if (position.x === original.x && position.y === original.y) delete positions[id];
    else positions[id] = position;
    return { ...design, positions };
  }

  return (
    <fieldset className="furniture-layout">
      <legend>가구 배치</legend>
      <label>
        옮길 가구
        <select value={id} onChange={event => onSelect(event.target.value)} disabled={disabled}>
          {options.map(object => <option key={object.id} value={object.id}>{names[object.id]}</option>)}
        </select>
      </label>
      <p>미리보기에서 선택한 가구에 테두리가 표시돼요. 화살표로 한 칸씩 옮겨 보세요.</p>
      <div className="furniture-directions" aria-label="가구 이동">
        {directions.map(({ x, y, name, Icon }) => {
          const target = candidate({ x: current.x + x * catalogue.layoutGrid, y: current.y + y * catalogue.layoutGrid });
          return <button className="button secondary" type="button" key={name}
            aria-label={name + " 이동"} disabled={disabled || !fits(target, id)} onClick={() => onChange(target)}>
            <Icon size={20} aria-hidden="true" />
          </button>;
        })}
        <button className="button secondary" type="button" disabled={disabled || !design.positions?.[id]}
          onClick={() => onChange(candidate(original))}>기본 자리</button>
      </div>
    </fieldset>
  );
}
