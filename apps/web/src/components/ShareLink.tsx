import { useId, useState } from "react";
import { Copy, Share2, Check } from "lucide-react";
export function ShareLink({
  url,
  label = "초대 링크",
}: {
  url: string;
  label?: string;
}) {
  const id = useId(),
    [copied, setCopied] = useState(false),
    [error, setError] = useState("");
  async function copy() {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setError("");
    } catch {
      setError("링크를 길게 눌러 직접 복사해 주세요.");
    }
  }
  async function share() {
    try {
      await navigator.share({ title: "도트홈에 초대해요", url });
      setError("");
    } catch (e) {
      if ((e as Error).name !== "AbortError")
        setError("공유 창을 열지 못했어요. 링크를 복사해 전달해 주세요.");
    }
  }
  return (
    <div className="share-link">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        readOnly
        value={url}
        onFocus={(e) => e.target.select()}
        aria-describedby={id + "-help"}
      />
      <p id={id + "-help"} className="muted">
        링크를 받은 사람이 로그인한 뒤 직접 수락하면 함께할 수 있어요.
      </p>
      <div className="form-actions">
        {typeof navigator.share === "function" && (
          <button
            type="button"
            className="button secondary"
            onClick={() => void share()}
          >
            <Share2 size={18} />
            공유하기
          </button>
        )}
        <button
          type="button"
          className="button primary"
          onClick={() => void copy()}
        >
          {copied ? <Check size={18} /> : <Copy size={18} />}{" "}
          {copied ? "복사했어요" : "링크 복사"}
        </button>
      </div>
      {copied && (
        <p role="status" className="success-message">
          링크를 복사했어요. 가족이나 친구에게 보내 주세요.
        </p>
      )}
      {error && (
        <p role="alert" className="error-message">
          {error}
        </p>
      )}
    </div>
  );
}
