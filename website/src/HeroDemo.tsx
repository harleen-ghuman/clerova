import { useEffect, useState } from "react";

// The hero demo plays the Clerova flow on a loop: an email arrives, gets
// organized, a reply is drafted, and it's approved and sent.
const STAGES = [
  "An email arrives",
  "Clerova sorts it",
  "Drafts a reply",
  "You approve & send",
] as const;

// How long each stage stays on screen
const STAGE_MS = [1800, 1800, 2600, 2800];

function prefersReducedMotion(): boolean {
  try {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  } catch {
    return false;
  }
}

export function HeroDemo() {
  // Without motion, show the drafted reply waiting for approval
  const [still] = useState(prefersReducedMotion);
  const [stage, setStage] = useState(still ? 2 : 0);

  useEffect(() => {
    if (still) {
      return;
    }

    const timer = window.setTimeout(
      () => setStage((current) => (current + 1) % STAGES.length),
      STAGE_MS[stage]
    );

    return () => window.clearTimeout(timer);
  }, [stage, still]);

  const sorted = stage >= 1;
  const drafted = stage >= 2;
  const sent = stage >= 3;

  return (
    <figure className="demo" aria-label="How Clerova handles a request">
      <ol className="demo-stages" aria-hidden="true">
        {STAGES.map((label, index) => (
          <li key={label} className={index === stage ? "active" : index < stage ? "past" : ""}>
            <span>{index + 1}</span>
            {label}
          </li>
        ))}
      </ol>

      <div className="preview">
        <div className="preview-list">
          <div className={`preview-row active ${stage === 0 ? "arriving" : ""}`}>
            <span className={`dot ${sent ? "dot-done" : "dot-review"}`} />
            <div>
              <strong>Tina Alvarez</strong>
              <span>Kitchen sink leaking</span>
            </div>
            {sorted && !sent && <em className="tag-urgent">Urgent</em>}
            {stage === 0 && <em className="tag-new">New</em>}
          </div>
          <div className="preview-row">
            <span className="dot dot-review" />
            <div>
              <strong>Marcus Lee</strong>
              <span>Question about late fee</span>
            </div>
          </div>
          <div className="preview-row">
            <span className="dot dot-waiting" />
            <div>
              <strong>Dan Ortiz</strong>
              <span>Noise from upstairs unit</span>
            </div>
          </div>
          <div className="preview-row">
            <span className="dot dot-done" />
            <div>
              <strong>Sam Rivera</strong>
              <span>Guest parking</span>
            </div>
          </div>
        </div>

        <div className={`preview-detail ${drafted ? "drafted" : ""}`}>
          <div className="preview-title">
            <strong>Kitchen sink leaking</strong>
            <span className={`preview-meta ${sorted ? "shown" : ""}`}>
              {sorted ? "12 Oak St, Unit 4B · Maintenance · High priority" : "tina.alvarez@gmail.com"}
            </span>
          </div>
          <p className="preview-message">
            Hi, water is leaking under the kitchen sink and the floor is getting
            wet. Unit 4B.
          </p>

          {drafted && !sent && (
            <div className="preview-suggestion appear">
              <span className="kicker">Clerova suggests</span>
              <strong>Create a maintenance job and reply</strong>
              <p>
                Hi Tina, thanks for letting us know. If you can, turn off the
                valve under the sink. We've logged a repair and will follow up
                to arrange a time.
              </p>
              <div className="preview-buttons">
                <span className="btn-approve">Approve &amp; send</span>
                <span className="btn-other">Suggest something else</span>
              </div>
            </div>
          )}

          {sent && (
            <div className="preview-sent appear">
              <strong>✓ Reply sent and job created</strong>
              <span>Tina got your reply. The repair is on your Jobs board.</span>
            </div>
          )}

          {!drafted && (
            <div className="preview-thinking" aria-hidden="true">
              <span />
              <span />
              <span />
            </div>
          )}
        </div>
      </div>
    </figure>
  );
}
