import "./App.css";

const REQUEST_DEMO_URL = "https://tally.so/r/jajWzE";

function App() {
  return (
    <div className="site">
      <header className="navbar">
        <a className="brand" href="#top" aria-label="Clerova home">
          <span className="brand-mark">C</span>
          <span className="brand-name">Clerova</span>
        </a>

        <nav className="nav-links">
          <a href="#how-it-works">How it works</a>
          <a href="#use-cases">Use cases</a>
          <a href="#human-control">Human control</a>
        </nav>

        <a
          className="button button-small button-outline"
          href={REQUEST_DEMO_URL}
          target="_blank"
          rel="noreferrer"
        >
          Request a Live Demo
        </a>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-copy">
            <div className="eyebrow">
              AI-ASSISTED PROPERTY OPERATIONS
            </div>

            <h1>
              Turn resident requests
              <span> into action — faster.</span>
            </h1>

            <p className="hero-description">
              Clerova helps property management teams classify incoming
              requests, prioritize urgent issues, recommend the next step,
              and track maintenance from request to completion.
            </p>

            <div className="hero-actions">
              <a
                className="button button-primary"
                href={REQUEST_DEMO_URL}
                target="_blank"
                rel="noreferrer"
              >
                Request a Live Demo
                <span aria-hidden="true">→</span>
              </a>
            </div>

            <p className="hero-note">
              AI assists. Your team stays in control.
            </p>
          </div>

          <div className="hero-product">
            <div className="browser-frame">
              <div className="browser-bar">
                <div className="browser-dots">
                  <span />
                  <span />
                  <span />
                </div>
                <div className="browser-address">
                  app.clerova-ai.com
                </div>
              </div>

              <img
                src="/screenshots/ai-review.png"
                alt="Clerova AI review interface"
              />
            </div>

            <div className="floating-card floating-card-left">
              <span className="floating-label">Priority</span>
              <strong>High</strong>
              <span>Urgent issue identified</span>
            </div>

            <div className="floating-card floating-card-right">
              <span className="floating-label">AI Action</span>
              <strong>Ready for review</strong>
              <span>Human approval required</span>
            </div>
          </div>
        </section>

        <section className="problem-section">
          <div className="section-heading">
            <span className="section-kicker">THE PROBLEM</span>
            <h2>
              Resident requests shouldn't require
              <span> manual triage.</span>
            </h2>
            <p>
              Maintenance issues, billing questions, leasing requests, and
              resident inquiries arrive throughout the day. Someone has to
              understand each request, decide how urgent it is, and determine
              what happens next.
            </p>
          </div>

          <div className="problem-grid">
            <article className="problem-card">
              <span className="card-number">01</span>
              <h3>Requests arrive</h3>
              <p>
                Resident messages come in with different issues, urgency,
                addresses, and context.
              </p>
            </article>

            <article className="problem-card">
              <span className="card-number">02</span>
              <h3>Clerova structures them</h3>
              <p>
                AI extracts the category, priority, property address, and
                operational summary.
              </p>
            </article>

            <article className="problem-card">
              <span className="card-number">03</span>
              <h3>Your team decides</h3>
              <p>
                Clerova recommends what happens next while keeping
                consequential actions behind human review.
              </p>
            </article>
          </div>
        </section>

        <section className="workflow-section" id="how-it-works">
          <div className="section-heading centered">
            <span className="section-kicker">HOW IT WORKS</span>
            <h2>
              From resident message to
              <span> resolved work.</span>
            </h2>
            <p>
              Clerova combines AI reasoning with a structured operational
              workflow instead of leaving requests buried in messages.
            </p>
          </div>

          <div className="workflow">
            <div className="workflow-step">
              <span>1</span>
              <strong>Request arrives</strong>
              <p>
                A resident reports a maintenance issue, billing question,
                leasing request, or other concern.
              </p>
            </div>

            <div className="workflow-line" />

            <div className="workflow-step">
              <span>2</span>
              <strong>AI understands it</strong>
              <p>
                Clerova identifies the category, priority, address, and
                concise operational summary.
              </p>
            </div>

            <div className="workflow-line" />

            <div className="workflow-step">
              <span>3</span>
              <strong>Next action recommended</strong>
              <p>
                The system generates an action, reasoning, and a suggested
                resident response.
              </p>
            </div>

            <div className="workflow-line" />

            <div className="workflow-step">
              <span>4</span>
              <strong>Your team reviews</strong>
              <p>
                Approve, reject, or regenerate the recommendation before
                consequential work moves forward.
              </p>
            </div>
          </div>
        </section>

        <section className="product-section">
          <div className="product-copy">
            <span className="section-kicker">A REAL OPERATIONS WORKFLOW</span>
            <h2>
              More than another
              <span> AI inbox.</span>
            </h2>

            <p>
              Clerova turns unstructured resident messages into persistent,
              trackable operational work.
            </p>

            <div className="feature-list">
              <div className="feature-row">
                <span className="feature-check">✓</span>
                <div>
                  <strong>Smart intake</strong>
                  <p>
                    Automatically identify request type, priority, address,
                    and operational context.
                  </p>
                </div>
              </div>

              <div className="feature-row">
                <span className="feature-check">✓</span>
                <div>
                  <strong>Action recommendations</strong>
                  <p>
                    Generate a recommended next step, reasoning, and resident
                    response.
                  </p>
                </div>
              </div>

              <div className="feature-row">
                <span className="feature-check">✓</span>
                <div>
                  <strong>Track work through completion</strong>
                  <p>
                    Approved maintenance actions become operational requests
                    with status and notes.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="product-image">
            <img
              src="/screenshots/dashboard.png"
              alt="Clerova operations dashboard"
            />
          </div>
        </section>

        <section className="use-cases-section" id="use-cases">
          <div className="section-heading centered">
            <span className="section-kicker">BUILT FOR PROPERTY OPERATIONS</span>
            <h2>
              One workflow for the requests
              <span> your team handles every day.</span>
            </h2>
          </div>

          <div className="use-case-grid">
            <article className="use-case-card">
              <div className="use-case-icon">M</div>
              <h3>Maintenance</h3>
              <p>
                Identify urgent issues, recommend maintenance actions, and
                track approved work through completion.
              </p>
              <span className="use-case-tag">MAINTENANCE</span>
            </article>

            <article className="use-case-card">
              <div className="use-case-icon">B</div>
              <h3>Billing</h3>
              <p>
                Classify billing questions and draft responses while requiring
                unavailable account information to be verified.
              </p>
              <span className="use-case-tag">BILLING</span>
            </article>

            <article className="use-case-card">
              <div className="use-case-icon">L</div>
              <h3>Leasing</h3>
              <p>
                Handle move-in and leasing questions without inventing
                property-specific information.
              </p>
              <span className="use-case-tag">LEASING</span>
            </article>

            <article className="use-case-card">
              <div className="use-case-icon">R</div>
              <h3>Resident Requests</h3>
              <p>
                Organize general inquiries and complaints into structured work
                instead of leaving them buried in messages.
              </p>
              <span className="use-case-tag">GENERAL</span>
            </article>
          </div>
        </section>

        <section className="control-section" id="human-control">
          <div className="control-visual">
            <div className="control-card">
              <span className="control-label">CLEROVA RECOMMENDS</span>
              <strong>Create maintenance request</strong>
              <p>
                Active water leak presents an ongoing property-damage risk and
                requires prompt maintenance attention.
              </p>

              <div className="decision-buttons">
                <span className="approve">Approve</span>
                <span className="reject">Reject</span>
              </div>
            </div>
          </div>

          <div className="control-copy">
            <span className="section-kicker">HUMAN-IN-THE-LOOP</span>
            <h2>
              AI recommends.
              <span> You decide.</span>
            </h2>

            <p>
              Property operations involve real residents, real properties,
              and real consequences. Clerova separates AI recommendations
              from execution.
            </p>

            <p>
              Your team can review, approve, reject, or regenerate a proposed
              action before consequential work moves forward.
            </p>

            <div className="control-flow">
              <span>AI recommendation</span>
              <b>→</b>
              <span>Human review</span>
              <b>→</b>
              <span>Approved work</span>
            </div>
          </div>
        </section>

        <section className="maintenance-section">
          <div className="section-heading centered">
            <span className="section-kicker">TRACK THE OUTCOME</span>
            <h2>
              Keep operational work
              <span> moving.</span>
            </h2>
            <p>
              Approved maintenance actions become trackable requests that move
              from open through completion.
            </p>
          </div>

          <div className="maintenance-image">
            <img
              src="/screenshots/maintenance.png"
              alt="Clerova maintenance operations"
            />
          </div>
        </section>

        <section className="cta-section">
          <div>
            <span className="section-kicker light">SEE CLEROVA IN ACTION</span>
            <h2>Turn the next resident request into organized work.</h2>
            <p>
              Explore the working demo or tell us how your team currently
              handles incoming property requests.
            </p>
          </div>

          <div className="cta-actions">
            <a
              className="button button-light"
              href={REQUEST_DEMO_URL}
              target="_blank"
              rel="noreferrer"
            >
              Request a Live Demo
              <span>→</span>
            </a>
          </div>
        </section>
      </main>

      <footer className="footer">
        <a className="brand footer-brand" href="#top">
          <span className="brand-mark">C</span>
          <span className="brand-name">Clerova</span>
        </a>

        <p>AI-assisted property operations with human oversight.</p>

        <span>© 2026 Clerova</span>
      </footer>
    </div>
  );
}

export default App;