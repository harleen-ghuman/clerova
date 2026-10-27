import "./App.css";

import { useEffect, useRef, useState, type FormEvent } from "react";

import { DEMO_URL, SIGN_IN_URL, signUpUrl } from "./appLinks";
import { HeroDemo } from "./HeroDemo";

const steps = [
  {
    title: "Forward your email",
    text: "Forward the emails you want Clerova to handle. Your inbox stays yours, and you never share your password.",
  },
  {
    title: "Clerova organizes each request",
    text: "It pulls out the address, type and urgency, suggests the next step and drafts a reply.",
  },
  {
    title: "Approve & send",
    text: "Edit the draft if you want, then click Approve & send. Clerova emails the reply and creates the job if one is needed.",
  },
  {
    title: "Replies come back to the same request",
    text: "When the resident answers, the conversation continues in one place and comes back to Needs review.",
  },
];

const requestTypes = [
  { name: "Maintenance", text: "Leaks, heating, appliances. Urgent requests are flagged and turn into jobs you can track." },
  { name: "Billing", text: "Late fees and payment questions, sorted and drafted for you to review before anything is sent." },
  { name: "Leasing", text: "Renewals, move-ins and showings, with drafts that leave property details for you to confirm." },
  { name: "Complaints and general", text: "Noise, parking and everything else, sorted so nothing sits unread." },
];

const plans = [
  { name: "Starter", plan: "starter" as const, price: 99, founding: 79, requests: "500 requests a month", note: "For small teams and portfolios" },
  { name: "Growth", plan: "growth" as const, price: 189, founding: 149, requests: "1,500 requests a month", note: "For busy inboxes and growing portfolios" },
];

const faqs = [
  {
    q: "Do I have to change my email?",
    a: "No. You keep your current inbox and forward the emails you want Clerova to handle. We show you how right after you sign up.",
  },
  {
    q: "Does Clerova send anything without me?",
    a: "No. Every reply and every job waits for your approval.",
  },
  {
    q: "Who do residents see the reply from?",
    a: "Replies are sent with your company name, and their answers come straight back to the same request.",
  },
  {
    q: "How does the free trial work?",
    a: "Your first 14 days are free. We ask for a card when you start, but you won't be charged until the trial ends. Cancel anytime before then and you pay nothing. If you cancel, you can keep using Clerova until your trial's end date.",
  },
  {
    q: "What counts as a request?",
    a: "One email or message that Clerova organizes for you. Replies in the same conversation don't count as new requests.",
  },
];

function JobsPreview() {
  const columns = [
    { title: "Open", dot: "dot-review", cards: [["Kitchen sink leak", "12 Oak St, Unit 4B · From Tina Alvarez"], ["Replace bathroom fan", "310 Cedar Ave · From Marcus Lee"]] },
    { title: "In progress", dot: "dot-waiting", cards: [["AC not cooling", "5667 Sparrow St · From Leah Kim"]] },
    { title: "Done", dot: "dot-done", cards: [["Smoke detector battery", "1200 Park Ave · Done Oct 22"]] },
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

const TRUST = [
  "No password sharing",
  "You approve every reply",
  "Never used to train AI",
  "Cancel anytime",
];

function App() {
  const [email, setEmail] = useState("");
  const [showStickyCta, setShowStickyCta] = useState(false);
  const heroRef = useRef<HTMLElement>(null);

  // On phones, keep a Start free trial button in reach after the hero
  useEffect(() => {
    const hero = heroRef.current;

    if (!hero || !("IntersectionObserver" in window)) {
      return;
    }

    const observer = new IntersectionObserver(([entry]) =>
      setShowStickyCta(!entry.isIntersecting)
    );
    observer.observe(hero);

    return () => observer.disconnect();
  }, []);

  function startTrial(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    window.location.assign(signUpUrl(undefined, email));
  }

  return (
    <div className="site">
      <a className="offer-bar" href="#pricing">
        <strong>Founding offer:</strong> Starter for $79/month for as long as you
        stay. Only 20 spots. <span aria-hidden="true">→</span>
      </a>

      <header className="navbar">
        <a className="brand" href="#top" aria-label="Clerova home">
          <span className="brand-mark">C</span>
          <span className="brand-name">Clerova</span>
        </a>

        <nav className="nav-links" aria-label="Main">
          <a href="#how-it-works">How it works</a>
          <a href="#setup">Getting started</a>
          <a href="#pricing">Pricing</a>
          <a href="#faq">FAQ</a>
          <a href={DEMO_URL}>Try the demo</a>
        </nav>

        <div className="nav-actions">
          <a className="nav-sign-in" href={SIGN_IN_URL}>
            Sign in
          </a>
          <a className="button button-small button-dark" href={signUpUrl()}>
            Start free trial
          </a>
        </div>
      </header>

      <main id="top">
        <section className="hero" ref={heroRef}>
          <div className="hero-copy">
            <span className="kicker">For property managers</span>
            <h1>Your requests inbox, sorted and drafted before you open it.</h1>
            <p className="lead">
              Forward your email to Clerova. It organizes every resident
              request, suggests the next step and drafts the reply. You approve
              and it's sent.
            </p>
            <form className="hero-form" onSubmit={startTrial}>
              <label className="visually-hidden" htmlFor="hero-email">
                Your email
              </label>
              <input
                id="hero-email"
                type="email"
                autoComplete="email"
                placeholder="you@company.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              <button type="submit" className="button button-primary">
                Start free trial
              </button>
            </form>
            <p className="note">14 days free. Set up in about five minutes.</p>
            <div className="hero-demo">
              <a className="button button-plain button-small" href={DEMO_URL}>
                Try the demo
              </a>
              <span>No sign-up. Click around a sample workspace.</span>
            </div>
            <ul className="trust">
              {TRUST.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>

          <div className="hero-visual">
            <HeroDemo />
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
            <span className="kicker">Your approval</span>
            <h2>Clerova suggests. You decide.</h2>
            <p>
              Nothing goes to a resident and no job is created until you
              approve it. Edit any draft, or ask for a different suggestion.
            </p>
            <p>
              Clerova doesn't have access to your rent records or leases, so
              it won't make up balances or terms. Add the facts to the draft
              before you send it.
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
            <span className="kicker">Jobs</span>
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
            <span className="kicker">Request types</span>
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
            <span className="kicker">Getting started</span>
            <h2>Keep your inbox. No need to share it.</h2>
            <p>
              Clerova never asks for your email password or access to your
              mailbox. You forward the emails you want it to handle, and you
              can stop at any time.
            </p>
          </div>
          <ul className="checks">
            <li>Works with Gmail, Outlook and any other email</li>
            <li>Forward everything, or only the emails you choose</li>
            <li>Replies go out under your company name</li>
            <li>Set up in about five minutes after you sign up</li>
          </ul>
        </section>

        <section className="section" id="pricing">
          <div className="heading">
            <span className="kicker">Pricing</span>
            <h2>Simple plans. 14 days free.</h2>
            <p>
              Every plan includes all features. Pay yearly and get two months
              free.
            </p>
          </div>
          <div className="pricing">
            {plans.map((plan) => (
              <article key={plan.name} className="price-card">
                <h3>{plan.name}</h3>
                <p className="price-note">{plan.note}</p>
                <p className="price">
                  <strong>${plan.price}</strong> / month
                </p>
                <p className="price-founding">
                  Founding customers: ${plan.founding} / month for as long as you stay
                </p>
                <ul>
                  <li>{plan.requests}</li>
                  <li>Drafted replies and suggested next steps</li>
                  <li>Approve &amp; send from your company name</li>
                  <li>Jobs board for repairs</li>
                </ul>
                <a className="button button-primary" href={signUpUrl(plan.plan)}>
                  Start free trial
                </a>
              </article>
            ))}
          </div>
          <p className="pricing-footnote">
            Founding prices are for our first 20 customers and stay the same for
            as long as you stay. Need more requests? Email hello@clerova-ai.com.
          </p>
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
          <p>Start your 14-day free trial. Cancel anytime before it ends and you won't be charged.</p>
          <div className="cta-actions">
            <a className="button button-light" href={signUpUrl()}>
              Start free trial
            </a>
            <a className="button button-outline-light" href={DEMO_URL}>
              Try the demo
            </a>
          </div>
        </section>
      </main>

      <div className={`sticky-cta ${showStickyCta ? "shown" : ""}`} aria-hidden={!showStickyCta}>
        <span>14 days free</span>
        <a className="button button-primary" href={signUpUrl()} tabIndex={showStickyCta ? 0 : -1}>
          Start free trial
        </a>
      </div>

      <footer className="footer">
        <a className="brand" href="#top">
          <span className="brand-mark">C</span>
          <span className="brand-name">Clerova</span>
        </a>
        <nav className="footer-links" aria-label="Footer">
          <a href="/terms">Terms</a>
          <a href="/privacy">Privacy</a>
          <a href="mailto:hello@clerova-ai.com">hello@clerova-ai.com</a>
        </nav>
        <span>© 2026 Clerova</span>
      </footer>
    </div>
  );
}

export default App;
