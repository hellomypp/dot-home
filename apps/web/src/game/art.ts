import type { WorldMember } from './types';

export type Palette = 'warm' | 'dusk';
export type Direction = 'down' | 'up' | 'left' | 'right';
type Draw = CanvasRenderingContext2D;
const ink = '#594c45';
function canvas(width: number, height: number): [HTMLCanvasElement, Draw] {
  const el = document.createElement('canvas'); el.width = width; el.height = height;
  const ctx = el.getContext('2d')!; ctx.imageSmoothingEnabled = false;
  return [el, ctx];
}
function r(c: Draw, x: number, y: number, w: number, h: number, color: string) { c.fillStyle = color; c.fillRect(Math.round(x), Math.round(y), Math.round(w), Math.round(h)); }
function oval(c: Draw, x: number, y: number, w: number, h: number, color: string) {
  for (let row = 0; row < h; row++) {
    const ry = (row - h / 2) / (h / 2), span = Math.sqrt(Math.max(0, 1 - ry * ry)) * w / 2;
    r(c, x + w / 2 - span, y + row, span * 2, 1, color);
  }
}
function outline(c: Draw, x: number, y: number, w: number, h: number, color: string, border = ink) {
  r(c, x + 2, y, w - 4, h, border); r(c, x, y + 2, w, h - 4, border); r(c, x + 2, y + 2, w - 4, h - 4, color);
}
function book(c: Draw, x: number, y: number, color: string, h = 10) { r(c, x, y, 4, h, color); r(c, x + 1, y + 2, 2, 1, '#e5d8b9'); }
function leaves(c: Draw, x: number, y: number, scale = 1) {
  r(c, x + 9 * scale, y + 4 * scale, 2 * scale, 20 * scale, '#68754b');
  for (const [dx, dy, w, h, color] of [[0, 2, 9, 6, '#70845d'], [10, 0, 10, 7, '#839b69'], [2, 9, 10, 7, '#526d50'], [12, 9, 9, 5, '#91a174'], [4, 15, 8, 5, '#7b905c']] as const) {
    oval(c, x + dx * scale, y + dy * scale, w * scale, h * scale, color);
  }
}
function pot(c: Draw, x: number, y: number, scale = 1) {
  leaves(c, x - 2 * scale, y - 22 * scale, scale);
  r(c, x + 1 * scale, y + 2 * scale, 13 * scale, 11 * scale, '#ac7054');
  r(c, x, y, 16 * scale, 4 * scale, '#d09c77'); r(c, x + 2 * scale, y + 3 * scale, 3 * scale, 8 * scale, '#c58d6c');
}
function windowArt(c: Draw, x: number, y: number, w = 86, h = 58, dusk = false) {
  r(c, x - 4, y - 4, w + 8, h + 10, '#ba9c78'); r(c, x - 2, y - 2, w + 4, h + 4, '#fff4dc');
  r(c, x + 2, y + 2, w - 4, h - 4, dusk ? '#a7b4bc' : '#b8d3ca');
  r(c, x + 4, y + h - 21, w - 8, 17, dusk ? '#8a9c90' : '#8ca78a');
  oval(c, x + 9, y + h - 34, 32, 26, '#98af8d'); oval(c, x + w - 40, y + h - 38, 36, 29, '#749575');
  r(c, x + 10, y + 8, 5, 20, '#e0eadc'); r(c, x + 17, y + 7, 2, 12, '#e0eadc');
  r(c, x + w / 2 - 2, y, 4, h, '#f4ead0'); r(c, x, y + h / 2, w, 4, '#f4ead0');
  r(c, x - 6, y + h, w + 12, 5, '#dac5a4'); r(c, x - 6, y + h + 5, w + 12, 3, '#ae8e6d');
  for (const side of [x - 13, x + w - 2]) {
    r(c, side, y - 5, 15, h - 1, '#e0c9ab'); r(c, side + 3, y - 5, 3, h - 2, '#f3e5cd'); r(c, side + 10, y - 5, 2, h - 1, '#c6ae91');
    r(c, side, y + 35, 15, 3, '#a99573');
  }
}

export function makeHouse(palette: Palette): HTMLCanvasElement {
  const [el, c] = canvas(640, 448), dusk = palette === 'dusk';
  r(c, 0, 0, 640, 448, dusk ? '#e6e2de' : '#ede9dc');
  // A cutaway house, with a distinct foundation and hand-laid wooden planks.
  r(c, 29, 41, 588, 378, '#d0c5b0'); r(c, 25, 35, 588, 378, '#aa947c');
  r(c, 32, 41, 576, 366, '#eee2c5'); r(c, 32, 116, 576, 291, '#c49c73');
  c.save();c.beginPath();c.rect(38,122,564,282);c.clip();
  for (let y = 122, row = 0; y < 404; y += 18, row++) {
    const colors = ['#d4b18a', '#d9b68f', '#d3ad83', '#dcbb94'];
    r(c, 38, y, 564, 17, colors[row % 4]); r(c, 38, y + 17, 564, 1, '#bb956d');
    for (let x = 38 + (row % 3) * 33; x < 602; x += 96) {
      r(c, x, y, 1, 17, '#bd9872');
      r(c, x + 10, y + 4, 24 + (row % 3) * 4, 1, '#c5a17b'); r(c, x + 44, y + 11, 33, 1, '#caa781');
      r(c, x + 6, y + 3, 2, 1, '#ad8867');
    }
  }
  c.restore();
  // Subtle sage wallpaper, warm wall panels and a deep skirting board.
  r(c, 32, 41, 576, 78, dusk ? '#c4cdc1' : '#d3d9bf');
  for (let x = 40; x < 604; x += 12) { r(c, x, 44, 1, 68, '#c8ceb5'); for (let y = 50; y < 110; y += 15) { r(c, x + 4, y, 2, 2, '#b9c2a4'); } }
  r(c, 32, 110, 576, 5, '#e9dcc0'); r(c, 32, 115, 576, 7, '#a48362'); r(c, 32, 122, 576, 3, '#b99770');
  r(c, 32, 41, 6, 366, '#e7d6b6'); r(c, 602, 41, 6, 366, '#e7d6b6');
  r(c, 38, 125, 5, 279, '#a78765'); r(c, 596, 125, 6, 279, '#a78765');
  r(c, 32, 401, 576, 6, '#ead9b8'); r(c, 32, 407, 576, 5, '#997c61');
  windowArt(c, 89, 54, 88, 55, dusk); windowArt(c, 381, 54, 84, 55, dusk);
  // Framed print and wall clock.
  outline(c, 206, 56, 32, 39, '#f1e7cf', '#a38562');
  r(c, 211, 61, 22, 27, '#d7c8a6'); oval(c, 216, 65, 11, 11, '#c59169'); r(c, 214, 80, 15, 4, '#819271');
  oval(c, 278, 59, 29, 29, '#a0825d'); oval(c, 281, 62, 23, 23, '#f1e8ce');
  r(c, 292, 65, 2, 10, '#786b52'); r(c, 287, 74, 7, 2, '#786b52'); r(c, 292, 63, 1, 2, '#786b52');
  // Kitchen backsplash.
  r(c, 331, 114, 192, 39, '#dfdcc9');
  for (let y = 115; y < 153; y += 9) for (let x = 332; x < 523; x += 16) { r(c, x, y, 15, 8, (x + y) % 3 ? '#efe9d8' : '#dedfca'); }
  // Woven living-room rug: border, fringe, and a quiet repeated motif.
  r(c, 61, 270, 162, 107, '#c7a685'); r(c, 63, 270, 158, 104, '#e5d8b7'); r(c, 68, 275, 148, 94, '#abb798'); r(c, 73, 280, 138, 84, '#e1dbb9');
  for (let y = 285; y < 359; y += 10) for (let x = 79; x < 207; x += 12) { r(c, x, y, 3, 1, '#c8c8a7'); r(c, x + 1, y - 1, 1, 3, '#c8c8a7'); }
  for (let x = 65; x < 220; x += 5) { r(c, x, 267, 2, 3, '#e9dfc4'); r(c, x, 374, 2, 4, '#e9dfc4'); }
  // Bedroom runner, pet cushion, and entry mat.
  r(c, 488, 244, 105, 133, '#b3b59b'); r(c, 493, 249, 95, 123, '#d4d6bc');
  for (let y = 253; y < 372; y += 5) r(c, 495, y, 91, 1, '#c5cbb0');
  oval(c, 247, 254, 46, 27, '#b89f82'); oval(c, 249, 253, 42, 23, '#d5bd99'); oval(c, 256, 257, 28, 14, '#b9a47e');
  r(c, 314, 379, 65, 21, '#aa997e'); r(c, 317, 382, 59, 14, '#c1b18f');
  for (let x = 320; x < 375; x += 5) r(c, x, 383, 1, 12, '#ab9b7e');
  // Small floor objects remain separate from collision volumes.
  r(c, 274, 289, 12, 4, '#a49c7b'); r(c, 275, 287, 10, 3, '#e2d8b6'); r(c, 278, 288, 5, 1, '#8d7058');
  r(c, 290, 289, 12, 4, '#8ca3a3'); r(c, 291, 287, 10, 3, '#d6e3d6');
  r(c, 571, 382, 15, 10, '#ae835f'); r(c, 578, 382, 2, 10, '#e3c59c'); r(c, 573, 384, 5, 3, '#f4e6c9');
  // Door opening in foreground.
  r(c, 330, 401, 34, 11, '#d9b38a'); r(c, 329, 402, 2, 8, '#b58d65'); r(c, 363, 402, 2, 8, '#b58d65');
  return el;
}

export function makeFurniture(kind: string): HTMLCanvasElement {
  const dimensions: Record<string, [number, number]> = { sofa: [140, 83], fridge: [52, 88], kitchen: [192, 71], table: [92, 88], bed: [86, 136], plant: [44, 72], shelf: [42, 88], cabinet: [71, 57], lamp: [30, 68] };
  const [w, h] = dimensions[kind] ?? [64, 64], [el, c] = canvas(w, h);
  oval(c, 2, h - 10, w - 4, 10, '#9b896b66');
  if (kind === 'sofa') {
    r(c, 13, 72, 7, 7, '#735b47'); r(c, 120, 72, 7, 7, '#735b47');
    outline(c, 7, 12, 126, 55, '#6f8062', '#52664f');
    outline(c, 13, 14, 55, 32, '#9aab83', '#617457'); outline(c, 70, 14, 55, 32, '#94a57f', '#617457');
    r(c, 16, 17, 49, 2, '#b5bf98'); r(c, 73, 17, 49, 2, '#b5bf98');
    outline(c, 10, 47, 120, 22, '#859772', '#617457'); r(c, 14, 49, 112, 3, '#a4b18a'); r(c, 69, 50, 2, 15, '#657b59');
    outline(c, 1, 32, 15, 40, '#7e9269', '#52664f'); outline(c, 124, 32, 15, 40, '#7e9269', '#52664f');
    r(c, 3, 34, 11, 4, '#a9b58b'); r(c, 126, 34, 11, 4, '#a9b58b');
    outline(c, 23, 30, 25, 22, '#dfc79b', '#b8a27b'); r(c, 27, 33, 17, 2, '#efe0b6'); r(c, 34, 38, 3, 5, '#d2b785');
    outline(c, 94, 31, 22, 21, '#bc8e70', '#9f735b'); r(c, 98, 34, 14, 2, '#d2a484');
    r(c, 77, 47, 18, 25, '#d8cfac'); for (let x = 79; x < 94; x += 4) r(c, x, 48, 1, 26, '#b8b38f');
  } else if (kind === 'fridge') {
    r(c, 8, 80, 5, 4, '#756958'); r(c, 39, 80, 5, 4, '#756958');
    outline(c, 3, 6, 46, 75, '#e0dfc9', '#8d927f'); r(c, 7, 9, 36, 69, '#f0edd8'); r(c, 7, 9, 3, 67, '#f8f4e2'); r(c, 44, 10, 3, 67, '#c4c8b1');
    r(c, 5, 31, 41, 3, '#a8ad98'); r(c, 11, 20, 3, 9, '#8a9686'); r(c, 11, 38, 3, 15, '#8a9686');
    r(c, 29, 41, 10, 13, '#e6d399'); r(c, 32, 41, 3, 2, '#a9b398'); r(c, 31, 46, 6, 1, '#b5a473'); r(c, 31, 49, 4, 1, '#b5a473');
    oval(c, 32, 17, 5, 5, '#bc876d'); pot(c, 16, 3, .7);
  } else if (kind === 'kitchen') {
    r(c, 5, 60, 181, 6, '#725e49'); r(c, 3, 24, 186, 36, '#ab9670');
    for (let x = 6; x < 187; x += 45) {
      outline(c, x, 28, 41, 29, '#c7b58c', '#9e875e'); r(c, x + 3, 31, 35, 2, '#dfcfa9'); r(c, x + 31, 37, 3, 3, '#716e53');
    }
    r(c, 1, 17, 190, 9, '#e8dfc4'); r(c, 1, 24, 190, 3, '#a48d69'); r(c, 3, 16, 186, 3, '#fcf0d8');
    outline(c, 26, 15, 42, 9, '#a4b5aa', '#899b92'); r(c, 30, 17, 34, 4, '#c6d1bb'); r(c, 48, 8, 3, 10, '#8b9a8e'); r(c, 49, 7, 8, 3, '#8b9a8e');
    r(c, 104, 14, 45, 10, '#656b5f'); oval(c, 109, 16, 13, 6, '#b8baa5'); oval(c, 129, 16, 13, 6, '#b8baa5');
    r(c, 111, 8, 18, 10, '#bb8a65'); r(c, 110, 8, 20, 3, '#785e4b'); r(c, 119, 5, 3, 3, '#785e4b'); r(c, 129, 11, 5, 3, '#785e4b');
    r(c, 159, 6, 15, 13, '#e5d1a5'); r(c, 160, 4, 13, 3, '#b18b64'); r(c, 165, 10, 6, 5, '#c7ac7c');
    r(c, 174, 12, 10, 7, '#afa58b'); r(c, 177, 4, 2, 9, '#7f7157'); r(c, 181, 7, 2, 7, '#7f7157');
  } else if (kind === 'table') {
    for (const [x, y] of [[9, 34], [72, 34], [9, 63], [72, 63]]) { outline(c, x, y, 17, 22, '#c7aa7d', '#876e50'); r(c, x + 2, y + 3, 13, 3, '#e1c594'); }
    r(c, 20, 55, 5, 28, '#8e6a4c'); r(c, 69, 55, 5, 28, '#8e6a4c');
    outline(c, 6, 24, 80, 39, '#b4875d', '#866046'); r(c, 8, 22, 76, 34, '#e0bd87'); r(c, 10, 24, 72, 2, '#efd19e');
    for (let y = 31; y < 56; y += 8) r(c, 10, y, 72, 1, '#d0a975');
    r(c, 34, 23, 23, 34, '#d2d7b3'); r(c, 36, 23, 2, 34, '#b0bd97'); r(c, 52, 23, 2, 34, '#b0bd97');
    oval(c, 18, 30, 15, 9, '#f7efda'); oval(c, 20, 32, 11, 5, '#d6d8b9'); oval(c, 62, 40, 15, 9, '#f7efda');
    pot(c, 41, 28, .55); r(c, 26, 43, 7, 7, '#faf1d9'); r(c, 32, 44, 3, 4, '#d3c7a5'); r(c, 27, 43, 5, 2, '#826a52');
  } else if (kind === 'bed') {
    outline(c, 3, 9, 80, 117, '#a57d5b', '#785d47'); r(c, 8, 14, 70, 20, '#c79d70'); r(c, 10, 17, 66, 2, '#deb689');
    r(c, 8, 28, 70, 89, '#eee6cd'); r(c, 9, 29, 68, 4, '#fff5df');
    outline(c, 13, 34, 28, 20, '#f9eed5', '#c8c4a9'); outline(c, 44, 34, 28, 20, '#f9eed5', '#c8c4a9');
    r(c, 8, 57, 70, 58, '#9aa998'); r(c, 10, 58, 66, 10, '#c4cebb'); r(c, 10, 70, 66, 41, '#a6b2a0');
    for (let y = 75; y < 111; y += 10) { r(c, 11, y, 64, 2, '#8f9f8d'); for (let x = 15; x < 75; x += 12) r(c, x, y - 3, 2, 7, '#bfc8b0'); }
    r(c, 4, 116, 78, 9, '#bd9469'); r(c, 7, 125, 6, 7, '#785d47'); r(c, 73, 125, 6, 7, '#785d47');
  } else if (kind === 'plant') {
    pot(c, 8, 47, 1.7); leaves(c, 6, 8, 1.2); leaves(c, 17, 22, 1);
  } else if (kind === 'shelf') {
    outline(c, 2, 9, 38, 72, '#8b6d4e', '#6f5946'); r(c, 6, 12, 30, 65, '#ae8961');
    for (const y of [33, 55, 76]) { r(c, 4, y, 34, 4, '#d4b17e'); r(c, 6, y + 4, 30, 2, '#725a44'); }
    book(c, 8, 17, '#ac6f57', 16); book(c, 13, 21, '#859477', 12); book(c, 18, 19, '#d3b783', 14); book(c, 23, 17, '#718b89', 16); book(c, 28, 22, '#d7cfaa', 11);
    outline(c, 8, 43, 13, 11, '#dbc8a3', '#9a805b'); r(c, 12, 46, 5, 2, '#92764f'); pot(c, 25, 47, .6);
    book(c, 9, 62, '#b89973', 14); book(c, 15, 64, '#9b5f51', 12); book(c, 21, 62, '#7e947e', 14); r(c, 27, 68, 7, 7, '#ded5b5');
    pot(c, 12, 7, .7);
  } else if (kind === 'cabinet') {
    outline(c, 3, 17, 65, 32, '#b59166', '#7e654b'); r(c, 2, 15, 67, 5, '#d9bb89'); r(c, 6, 21, 27, 23, '#c8a574'); r(c, 36, 21, 27, 23, '#c8a574'); r(c, 28, 30, 2, 3, '#7b6249'); r(c, 39, 30, 2, 3, '#7b6249');
    r(c, 7, 49, 4, 5, '#7e654b'); r(c, 59, 49, 4, 5, '#7e654b');
    outline(c, 22, 0, 29, 17, '#6f7667', '#57594f'); r(c, 26, 3, 21, 10, '#b6c5ae'); r(c, 31, 13, 9, 3, '#57594f');
    r(c, 8, 9, 10, 5, '#eee2bf'); r(c, 8, 9, 10, 1, '#a58262'); pot(c, 55, 12, .55);
  } else if (kind === 'lamp') {
    oval(c, 5, 59, 20, 6, '#9b805d'); r(c, 14, 19, 3, 43, '#8d7455'); r(c, 6, 5, 19, 19, '#d7c69e'); r(c, 3, 19, 25, 5, '#c4af81'); r(c, 9, 5, 3, 14, '#eee0b9');
  }
  return el;
}

export function makeAvatar(member: WorldMember, direction: Direction, frame: number): HTMLCanvasElement {
  const [el, c] = canvas(32, 48);
  const skin = member.appearance?.skin || '#e4b590', hair = member.appearance?.hair || '#655044', shirt = member.appearance?.shirt || '#c18465';
  const step = frame === 1 ? -2 : frame === 3 ? 2 : 0, bob = frame % 2;
  const side = direction === 'left' || direction === 'right';
  oval(c, 5, 42, 22, 5, '#74694f44');
  r(c, 10, 35 + bob, 5, 7 - step / 2, '#626b66'); r(c, 18, 35 + bob, 5, 7 + step / 2, '#626b66');
  r(c, 8, 41 - step / 2, 8, 4, '#514d45'); r(c, 18, 41 + step / 2, 8, 4, '#514d45'); r(c, 10, 41 - step / 2, 4, 1, '#b0a68a');
  r(c, 9, 23 + bob, 15, 14, '#816955'); r(c, 10, 23 + bob, 13, 12, shirt); r(c, 11, 24 + bob, 2, 10, '#ffffff20');
  r(c, 6, 25 + bob - step / 2, 4, 9, shirt); r(c, 23, 25 + bob + step / 2, 4, 9, shirt);
  r(c, 6, 33 + bob - step / 2, 4, 4, skin); r(c, 23, 33 + bob + step / 2, 4, 4, skin);
  r(c, 13, 20 + bob, 7, 5, skin);
  // The face has a 2px outline, two-tone hair and one-pixel features.
  r(c, 8, 5 + bob, 17, 19, hair); r(c, 6, 8 + bob, 21, 13, hair); r(c, 10, 3 + bob, 13, 3, hair);
  if (direction !== 'up') {
    r(c, 9, 11 + bob, 15, 11, skin); r(c, 11, 21 + bob, 11, 3, skin); r(c, 7, 14 + bob, 2, 5, skin); r(c, 24, 14 + bob, 2, 5, skin);
    r(c, 9, 9 + bob, 8, 5, hair); r(c, 17, 9 + bob, 7, 2, hair); r(c, 8, 10 + bob, 3, 6, hair);
    if (side) {
      const eyeX = direction === 'left' ? 11 : 22; r(c, eyeX, 16 + bob, 2, 2, '#4d453d'); r(c, eyeX - (direction === 'left' ? 2 : -1), 19 + bob, 3, 1, '#c98c76');
    } else {
      r(c, 12, 16 + bob, 2, 2, '#4d453d'); r(c, 20, 16 + bob, 2, 2, '#4d453d'); r(c, 10, 19 + bob, 3, 1, '#cc8e78'); r(c, 22, 19 + bob, 2, 1, '#cc8e78'); r(c, 16, 21 + bob, 3, 1, '#ad7563');
    }
  } else { r(c, 8, 8 + bob, 17, 11, '#ffffff0c'); r(c, 12, 20 + bob, 9, 3, hair); }
  r(c, 10, 5 + bob, 10, 2, '#ffffff16');
  return el;
}

export function makeCat(frame: number, color = '#c69462'): HTMLCanvasElement {
  const [el, c] = canvas(34, 28), bob = frame % 2;
  oval(c, 4, 22, 25, 4, '#74694f44');
  r(c, 25, 11, 6, 4, color); r(c, 29, 6 + bob, 3, 8, color); r(c, 30, 4 + bob, 2, 4, color);
  outline(c, 11, 12 + bob, 17, 10, color, '#8f6d4e'); r(c, 13, 21, 4, 3 - bob, '#956c48'); r(c, 23, 21, 4, 3 + bob, '#956c48');
  r(c, 4, 7 + bob, 15, 14, color); r(c, 3, 11 + bob, 17, 8, color); r(c, 4, 4 + bob, 4, 7, color); r(c, 15, 4 + bob, 4, 7, color); r(c, 5, 6 + bob, 2, 4, '#d7a28a'); r(c, 16, 6 + bob, 2, 4, '#d7a28a');
  r(c, 6, 13 + bob, 2, 2, '#514b3f'); r(c, 15, 13 + bob, 2, 2, '#514b3f'); r(c, 10, 17 + bob, 3, 1, '#9b685c');
  r(c, 10, 8 + bob, 2, 3, '#956c48'); r(c, 20, 13 + bob, 2, 4, '#956c48'); r(c, 24, 13 + bob, 2, 4, '#956c48');
  return el;
}

export function makeOtherPlace(place: string, palette: Palette): HTMLCanvasElement {
  const el = makeHouse(palette), c = el.getContext('2d')!;
  // Other places deliberately reuse a small base kit until home art is approved.
  r(c, 45, 130, 550, 269, place === 'outside' ? '#b5c299' : '#d7c5a4');
  if (place === 'outside') {
    for (let y = 140; y < 398; y += 17) for (let x = 53; x < 590; x += 19) { r(c, x + y % 7, y, 2, 3, '#99ad7d'); r(c, x + 5, y + 5, 2, 1, '#d4d6a6'); }
    r(c, 290, 130, 65, 269, '#d8ccb0'); for (let y = 134; y < 399; y += 15) r(c, 292, y, 61, 1, '#c0b494');
  } else {
    for (let x = 45; x < 596; x += 24) r(c, x, 130, 1, 269, '#c9b695');
    for (let y = 130; y < 400; y += 24) r(c, 45, y, 550, 1, '#c9b695');
  }
  return el;
}

export function makeDog(frame: number, color = '#b68c66'): HTMLCanvasElement {
  const [el,c]=canvas(34,28),bob=frame%2;
  oval(c,3,23,28,4,'#74694f44');
  outline(c,11,12+bob,17,10,color,'#806047');
  r(c,13,21,4,4-bob,'#806047');r(c,23,21,4,3+bob,'#806047');
  r(c,26,9+bob,6,4,color);r(c,30,6+bob,3,5,color);
  outline(c,3,5+bob,17,15,color,'#806047');
  r(c,2,7+bob,4,12,'#70513d');r(c,18,7+bob,4,12,'#70513d');
  r(c,7,15+bob,10,5,'#e0c6a3');r(c,10,14+bob,4,3,'#51463c');
  r(c,7,10+bob,2,2,'#3f3933');r(c,15,10+bob,2,2,'#3f3933');
  r(c,8,20+bob,10,2,'#8e9c77');
  return el;
}
