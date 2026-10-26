import "./App.css";

// Sign-up lives in the app; utm_source lets onboarding record where
// the visitor came from.
const SIGN_UP_URL = "https://app.clerova-ai.com/?utm_source=website";

const steps = [
  {
    title: "Forward your inbox",
    text: "Sign up and get your own Clerova address. Forward your requests email to it, or only the emails you choose.",
  },
  {
    title: "Clerova organizes each request",
    text: "It pulls out the address, type and urgency, suggests the next step and drafts a reply.",
  },
  {
    title: "You approve and send",
    text: "Edit the reply if you want, then approve. Clerova emails it and creates the maintenance job if one is needed.",
  },
  {
    title: "Replies come back to the same request",
    text: "When the resident answers, the conversation continues in one place and returns to your review list.",
  },
];

const requestTypes = [
  { name: "Maintenance", text: "Leaks, heating, appliances. Urgent issues are flagged and turn into jobs you can track." },
  { name: "Billing", text: "Late fees and payment questions. Replies promise to check the ledger instead of guessing." },
  { name: "Leasing", text: "Renewals, move-ins and showings, with drafts that leave property details for you to confirm." },
  { name: "Complaints and general", text: "Noise, parking and everything else, sorted so nothing sits unread." },
];

const faqs = [
  {
    q: "Do I have to change my email?",
    a: "No. You keep your current inbox and add a forwarding rule. Gmail and Outlook both support it, and setup takes about five minutes.",
  },
  {
    q: "Does Clerova send anything without me?",
    a: "No. Every reply and every job waits for your approval.",
  },
  {
    q: "Who do residents see the reply from?",
    a: "Replies come from your Clerova address with your company name, and their answers come straight back to the same request.",
  },
  {
    q: "What does it cost?",
    a: "The 14-day trial is free and needs no credit card. It includes 100 AI-processed requests.",
  },
];

function ProductPreview() {
  return (
    <div className="preview" aria-label="Clerova request review screen">
      <div className="preview-list">
        <div className="preview-row active">
          <span className="dot dot-review" />
          <div>
            <strong>Tina Alvarez</strong>
            <span>Kitchen sink leaking</span>
          </div>
          <em className="tag-urgent">Urgent</em>
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

      <div className="preview-detail">
        <div className="preview-title">
          <strong>Kitchen sink leaking</strong>
          <span>12 Oak St, Unit 4B · Maintenance · High priority</span>
        </div>
        <p className="preview-message">
          Hi, water is leaking under the kitchen sink and the floor is getting
          wet. Unit 4B.
        </p>
        <div className="preview-suggestion">
          <span className="kicker">Clerova suggests</span>
          <strong>Create a maintenance job and reply</strong>
          <p>
            Hi Tina, thanks for letting us know. Please turn off the valve under
            the sink if you can. We are arranging a plumber and will confirm a
            time shortly.
          </p>
          <div className="preview-buttons">
            <span className="btn-approve">Approve &amp; send</span>
            <span className="btn-other">Suggest something else</span>
          </div>
        </div>
      </div>
    </div>
  );
}

function JobsPreview() {
  const columns = [
    { title: "Open", dot: "dot-review", cards: [["Kitchen sink leak", "12 Oak St, 4B · Due today"], ["Replace bathroom fan", "310 Cedar Ave · Due Fri"]] },
    { title: "In progress", dot: "dot-waiting", cards: [["AC not cooling", "5667 Sparrow St · Thu 10 to 12"]] },
    { title: "Done", dot: "dot-done", cards: [["Smoke detector battery", "1200 Park Ave · Oct 22"]] },
  ];

  return (
    <div className="jobs" aria-label="Clerova jobs board">
      {columns.map((column) => (
        <div className="jobs-column" key={column.title}>
          <div className="jobs-heading">
            <span className={`dot ${column.dot}`} />
            {column.title}
          </div>
          {column.cards.map(([title, meta]) => (
            <div className="jobs-card" key={title}>
              <strong>{title}</strong>
              <span>{meta}</span>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

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
          <a href="#setup">Setup</a>
          <a href="#faq">FAQ</a>
        </nav>

        <a className="button button-small button-dark" href={SIGN_UP_URL}>
          Start free trial
        </a>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-copy">
            <span className="kicker">For property managers</span>
            <h1>Your requests inbox, sorted and drafted before you open it.</h1>
            <p className="lead">
              Forward your email to Clerova. It organizes every resident
              request, suggests the next step and writes the reply. You approve
              and it's sent.
            </p>
            <div className="hero-actions">
              <a className="button button-primary" href={SIGN_UP_URL}>
                Start free trial
              </a>
              <a className="button button-plain" href="#how-it-works">
                See how it works
              </a>
            </div>
            <p className="note">14 days free · No credit card · Keep your current email</p>
          </div>

          <div className="hero-visual">
            <ProductPreview />
          </div>
        </section>

        <section className="section" id="how-it-works">
          <div className="heading">
            <span className="kicker">How it works</span>
            <h2>From a forwarded email to a sent reply</h2>
          </div>
          <ol className="steps">
            {steps.map((step, index) => (
              <li key={step.title}>
                <span className="step-number">{index + 1}</span>
                <h3>{step.title}</h3>
                <p>{step.text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="section split">
          <div>
            <span className="kicker">You stay in control</span>
            <h2>Clerova suggests. You decide.</h2>
            <p>
              Nothing goes to a resident and no job is created until you
              approve it. Edit any draft, or ask for a different suggestion.
            </p>
            <p>
              Clerova doesn't make up answers it doesn't have. If a late fee
              needs checking, the draft says you'll check, not that it's been
              waived.
            </p>
          </div>
          <ul className="checks">
            <li>Every reply waits for your approval</li>
            <li>Editable drafts, written in plain language</li>
            <li>Urgent requests are flagged at the top</li>
            <li>A full history of what happened on each request</li>
          </ul>
        </section>

        <section className="section">
          <div className="heading">
            <span className="kicker">Track the work</span>
            <h2>Approved repairs land on your Jobs board</h2>
            <p>
              Move each job from open to in progress to done. When a job is
              finished, the request closes with it.
            </p>
          </div>
          <JobsPreview />
        </section>

        <section className="section">
          <div className="heading">
            <span className="kicker">Every kind of request</span>
            <h2>One list for everything residents send</h2>
          </div>
          <div className="types">
            {requestTypes.map((type) => (
              <article key={type.name}>
                <h3>{type.name}</h3>
                <p>{type.text}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="section split" id="setup">
          <div>
            <span className="kicker">Setup in five minutes</span>
            <h2>Keep your inbox. Add one forwarding rule.</h2>
            <p>
              Your Clerova address is shown right after sign-up, with
              step-by-step instructions for Gmail and Outlook.
            </p>
          </div>
          <div className="setup-card">
            <div className="setup-row">
              <span>Gmail</span>
              <p>Settings → Forwarding and POP/IMAP → Add a forwarding address</p>
            </div>
            <div className="setup-row">
              <span>Outlook</span>
              <p>Settings → Mail → Forwarding → Enable forwarding</p>
            </div>
            <div className="setup-row">
              <span>Only some emails?</span>
              <p>Create a filter, for example "to: maintenance@" or "subject: repair", and forward only those.</p>
            </div>
            <code>maple-property-k3j9x2@in.clerova-ai.com</code>
          </div>
        </section>

        <section className="section" id="faq">
          <div className="heading">
            <span className="kicker">FAQ</span>
            <h2>Questions</h2>
          </div>
          <div className="faq">
            {faqs.map((item) => (
              <details key={item.q}>
                <summary>{item.q}</summary>
                <p>{item.a}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="cta">
          <h2>See your inbox organized today.</h2>
          <p>Start a 14-day free trial. No credit card required.</p>
          <a className="button button-light" href={SIGN_UP_URL}>
            Start free trial
          </a>
        </section>
      </main>

      <footer className="footer">
        <a className="brand" href="#top">
          <span className="brand-mark">C</span>
          <span className="brand-name">Clerova</span>
        </a>
        <a href="mailto:hello@clerova-ai.com">hello@clerova-ai.com</a>
        <span>© 2026 Clerova</span>
      </footer>
    </div>
  );
}

export default App;
