export const expressionFields = {
  thought: { label: "한마디", maxLength: 60, defaultHours: 2 },
  food: { label: "먹고 싶은 음식", maxLength: 30, defaultHours: 6 },
  mood: { label: "기분", defaultHours: 4, options: {
    CALM: "편안해요", HAPPY: "기뻐요", EXCITED: "신나요", LONELY: "외로워요",
    HURT: "속상해요", IRRITATED: "짜증나요", WORRIED: "걱정돼요", TIRED: "지쳤어요",
  } },
  physical: { label: "몸 상태", defaultHours: 4, options: {
    OK: "괜찮아요", SLEEPY: "졸려요", FATIGUED: "피곤해요", UNWELL: "몸이 불편해요", RESTING: "쉬고 있어요",
  } },
  availability: { label: "연락 선호", defaultHours: 4, options: {
    TALK: "대화할 수 있어요", ENCOURAGE_ONLY: "응원만 받고 싶어요", QUIET: "조용히 있고 싶어요",
  } },
} satisfies Record<string, { label: string; defaultHours: number; maxLength?: number; options?: Record<string, string> }>;

export type ExpressionField = keyof typeof expressionFields;
export const expressionKeys = Object.keys(expressionFields) as ExpressionField[];
export interface ExpressionValue {
  value: string | null;
  updatedAt: string | null;
  expiresAt: string | null;
  version: number;
}
export interface ExpressionState {
  expressions: Record<ExpressionField, ExpressionValue>;
  revision: number;
  serverTime: string;
}
export function expressionLabel(field: ExpressionField, value: string) {
  const config = expressionFields[field];
  if (!("options" in config)) return value;
  return (config.options as Record<string, string>)[value] ?? value;
}
