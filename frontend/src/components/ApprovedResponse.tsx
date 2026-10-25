import { useState } from "react";

interface ApprovedResponseProps {
  response: string;
}

export function ApprovedResponse({ response }: ApprovedResponseProps) {
  const [copied, setCopied] = useState(false);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(response);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="details-section">
      <div className="approved-response-heading">
        <h3>Approved response</h3>

        <button
          type="button"
          className="secondary-action-button"
          onClick={handleCopy}
        >
          {copied ? "Copied" : "Copy"}
        </button>
      </div>

      <p className="drafted-response">{response}</p>
    </div>
  );
}
