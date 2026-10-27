import { useState } from "react";

interface GetStartedProps {
  /** Just finished checkout. */
  welcome: boolean;
  forwardingAddress: string | null;
  hasRequest: boolean;
  hasReply: boolean;
  onOpenEmailSetup: () => void;
  onOpenRequests: () => void;
  onHide: () => void;
}

const TEST_SUBJECT = "Kitchen sink is leaking";
const TEST_BODY =
  "Hi, water is leaking under the kitchen sink at 12 Oak St, Unit 4B. The floor is getting wet. Can someone come today?";

/** First-run checklist on Today: forward email, send a test, approve a reply. */
export function GetStarted({
  welcome,
  forwardingAddress,
  hasRequest,
  hasReply,
  onOpenEmailSetup,
  onOpenRequests,
  onHide,
}: GetStartedProps) {
  const [copied, setCopied] = useState<"idle" | "done" | "failed">("idle");

  async function copy(address: string) {
    try {
      await navigator.clipboard.writeText(address);
      setCopied("done");
      window.setTimeout(() => setCopied("idle"), 2000);
    } catch {
      // Some browsers block the clipboard; the address is selectable
      setCopied("failed");
    }
  }

  const testLink = forwardingAddress
    ? `mailto:${forwardingAddress}?subject=${encodeURIComponent(TEST_SUBJECT)}&body=${encodeURIComponent(TEST_BODY)}`
    : null;

  return (
    <section className="get-started" aria-labelledby="get-started-title">
      <div className="get-started-head">
        <div>
          <h2 id="get-started-title">
            {welcome ? "You're all set. Let's get your first request in." : "Get started"}
          </h2>
          <p>Takes about five minutes. Nothing is sent without your approval.</p>
        </div>
        <button type="button" className="rq-button rq-button-small rq-button-quiet" onClick={onHide}>
          Hide
        </button>
      </div>

      <ol className="get-started-steps">
        {forwardingAddress ? (
          <>
            <li>
              <span className="get-started-num">1</span>
              <div>
                <strong>Copy your Clerova address</strong>
                <div className="get-started-address">
                  <code>{forwardingAddress}</code>
                  <button
                    type="button"
                    className="rq-button rq-button-small"
                    onClick={() => copy(forwardingAddress)}
                  >
                    {copied === "done" ? "Copied" : copied === "failed" ? "Select it to copy" : "Copy"}
                  </button>
                </div>
              </div>
            </li>
            <li>
              <span className="get-started-num">2</span>
              <div>
                <strong>Forward your requests email to it</strong>
                <p>In Gmail or Outlook. You keep your inbox as it is.</p>
                <button type="button" className="rq-button rq-button-small" onClick={onOpenEmailSetup}>
                  Show me how
                </button>
              </div>
            </li>
            <li className={hasRequest ? "done" : ""}>
              <span className="get-started-num">{hasRequest ? "✓" : "3"}</span>
              <div>
                <strong>Send a test email</strong>
                <p>
                  {hasRequest
                    ? "Your first request arrived."
                    : "Opens your email app with a sample request. It shows up in Requests within a minute."}
                </p>
                {!hasRequest && testLink && (
                  <a className="rq-button rq-button-small rq-button-primary" href={testLink}>
                    Send a test email
                  </a>
                )}
              </div>
            </li>
          </>
        ) : (
          <li className={hasRequest ? "done" : ""}>
            <span className="get-started-num">{hasRequest ? "✓" : "1"}</span>
            <div>
              <strong>Add your first request</strong>
              <p>Paste a request you received with + New request on the Requests page.</p>
              {!hasRequest && (
                <button type="button" className="rq-button rq-button-small rq-button-primary" onClick={onOpenRequests}>
                  Go to Requests
                </button>
              )}
            </div>
          </li>
        )}
        <li className={hasReply ? "done" : ""}>
          <span className="get-started-num">{hasReply ? "✓" : forwardingAddress ? "4" : "2"}</span>
          <div>
            <strong>Approve your first reply</strong>
            <p>
              {hasReply
                ? "Done. You're using Clerova."
                : "Open the request, check Clerova's draft and click Approve & send."}
            </p>
            {hasRequest && !hasReply && (
              <button type="button" className="rq-button rq-button-small rq-button-primary" onClick={onOpenRequests}>
                Review it now
              </button>
            )}
          </div>
        </li>
      </ol>
    </section>
  );
}
