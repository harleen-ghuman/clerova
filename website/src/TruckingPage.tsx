import "./App.css";
import "./trucking.css";

import { useEffect, useRef, useState, type FormEvent } from "react";

import { SIGN_IN_URL, TRUCKING_DEMO_URL, signUpUrl } from "./appLinks";

const trialUrl = (email?: string) => signUpUrl(undefined, email, "trucking");

const steps = [
  {
    title: "Forward your rate cons",
    text: "Forward broker emails to your Clerova address, or set your inbox to do it. Your inbox stays yours, and you never share a password.",
  },
  {
    title: "Every rate con is checked",
    text: "Clerova reads the PDF, even a scan, makes the load, and flags what to fix before you sign: a rate that doesn't match, detention rules, missing TONU, a broker FMCSA doesn't know.",
  },
  {
    title: "Broker emails get a drafted reply",
    text: "Status checks, appointment questions and paperwork requests are matched to the load and answered from your latest update. You approve each one.",
  },
  {
    title: "Get paid for every hour and every load",
    text: "Tap Arrived and Left at each stop. Clerova works out detention, writes the claim and builds the invoice packet for your factor or broker.",
  },
];

const features = [
  { name: "Rate con check", text: "Rate, stops, detention, TONU, paperwork deadlines and payment terms, read from the PDF and checked against the broker's email." },
  { name: "Broker check", text: "Each broker's MC is checked against FMCSA's public data, so you know who you're hauling for." },
  { name: "Broker replies", text: "Drafted from your latest update on the load. Nothing goes out until you approve it." },
  { name: "Detention", text: "Free time and the hourly rate come from the rate con. Clerova tells you when detention starts and writes the claim with the times." },
  { name: "Paperwork", text: "BOLs, PODs and receipts from emails and phone photos are filed under the right load automatically." },
  { name: "Invoices", text: "One PDF packet with the invoice, rate con, BOL and POD. Upload it to your factor's website, or email it to the broker or factor." },
];

const faqs = [
  {
    q: "Who is Clerova for?",
    a: "Small carriers, owner-operators with a few trucks, and dispatch services. If rate cons and broker emails eat your evenings, it's for you.",
  },
  {
    q: "Do I have to change my email or load board?",
    a: "No. Keep your email, load boards and factor. You forward broker emails to Clerova, and it works alongside what you use today.",
  },
  {
    q: "Does Clerova send anything without me?",
    a: "No. Every reply to a broker, every detention claim and every invoice waits for you to approve it.",
  },
  {
    q: "Does it work with my factoring company?",
    a: "Yes. Clerova builds the invoice packet your factor asks for and puts your factor on the invoice as who to pay. Upload it on your factor's website, or email it if your factor takes email.",
  },
  {
    q: "Can it read scanned rate cons?",
    a: "Yes. Clerova reads the pages as pictures when a PDF is a scan, and it leaves a value blank rather than guess at something it can't read.",
  },
  {
    q: "What about my drivers?",
    a: "Drivers can email photos of BOLs and PODs to your Clerova address, and Clerova files them under the right load. You can also upload them from your phone.",
  },
  {
    q: "Where does my data go?",
    a: "Emails and files are stored with Supabase, and each company's data is kept separate. Rate cons are read with OpenAI's business API. Neither we nor OpenAI use your data to train AI models.",
  },
];

const truckPlans = [
  {
    name: "Starter",
    note: "For 1 to 8 trucks",
    price: "$25",
    unit: "/ truck a month",
    founding: "Founding carriers: $17.50 a truck, for as long as you stay",
    points: ["Rate con checks and broker replies", "Detention and extra pay", "Invoices and packets for your factor"],
  },
  {
    name: "Fleet",
    note: "For 9 to 49 trucks",
    price: "$199",
    unit: "/ month for up to 20 trucks",
    founding: "Then $12 a truck. Founding carriers: $139, then $8.40 a truck, for as long as you stay",
    points: ["Everything in Starter", "Lower price per truck as you grow"],
  },
];

const TRUST = ["No password sharing", "You approve everything", "Works with your factor", "Cancel anytime"];

/** A load as Clerova shows it: the check, detention and the invoice. */
function LoadPreview() {
  return (
    <div className="load-preview" aria-label="A load in Clerova">
      <div className="load-head">
        <strong>Fresno, CA → Phoenix, AZ</strong>
      </div>
      <p className="load-meta">Load #88213 · Crestline Freight · $2,150</p>

      <div className="load-row">
        <span className="load-pill load-pill-fix">Fix</span>
        <div>
          <strong>The rate con is $100 less than agreed</strong>
          <p>The email says $2,250 all in. Ask for a corrected rate con before you sign.</p>
        </div>
      </div>

      <div className="load-row">
        <span className="load-pill load-pill-note">Detention</span>
        <div>
          <strong>Desert Foods DC: 3h 30m on site</strong>
          <p>1h 30m past 2h free at $50/hour. Claim sent to the broker: $75.</p>
        </div>
      </div>

      <div className="load-row">
        <span className="load-pill load-pill-good">Invoice</span>
        <div>
          <strong>SR-1042 ready for your factor</strong>
          <p>Packet: invoice, rate con, signed POD. Total $2,225.</p>
        </div>
      </div>
    </div>
  );
}

export function TruckingPage() {
  const [email, setEmail] = useState("");
  const [showStickyCta, setShowStickyCta] = useState(false);
  const heroRef = useRef<HTMLElement>(null);

  useEffect(() => {
    const hero = heroRef.current;

    if (!hero || !("IntersectionObserver" in window)) {
      return;
    }

    const observer = new IntersectionObserver(([entry]) => setShowStickyCta(!entry.isIntersecting));
    observer.observe(hero);

    return () => observer.disconnect();
  }, []);

  function startTrial(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    window.location.assign(trialUrl(email));
  }

  return (
    <div className="site">
      <header className="navbar">
        <a className="brand" href="/trucking">
          <span className="brand-mark" aria-hidden="true">C</span>
          <span className="brand-name">Clerova</span>
        </a>

        <nav className="nav-links" aria-label="Main">
          <a href="#how-it-works">How it works</a>
          <a href="#features">What it does</a>
          <a href="#pricing">Pricing</a>
          <a href="#faq">FAQ</a>
          <a href={TRUCKING_DEMO_URL}>Try the demo</a>
        </nav>

        <div className="nav-actions">
          <a className="nav-sign-in" href={SIGN_IN_URL}>
            Sign in
          </a>
          <a className="button button-small button-dark" href={trialUrl()}>
            Start free trial
          </a>
        </div>
      </header>

      <main id="top">
        <section className="hero" ref={heroRef}>
          <div className="hero-copy">
            <span className="kicker">For carriers and dispatchers</span>
            <h1>Your trucking back office, done before you open your email.</h1>
            <p className="lead">
              Forward your broker emails to Clerova. It checks every rate con, drafts your replies to brokers,
              tracks detention and builds the invoice packet for your factor. You approve everything.
            </p>
            <form className="hero-form" onSubmit={startTrial}>
              <label className="visually-hidden" htmlFor="hero-email">
                Your email
              </label>
              <input
                id="hero-email"
                type="email"
                autoComplete="email"
                placeholder="you@yourcompany.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              <button type="submit" className="button button-primary">
                Start free trial
              </button>
            </form>
            <p className="note">14 days free. Set up in about five minutes.</p>
            <div className="hero-demo">
              <a className="button button-plain button-small" href={TRUCKING_DEMO_URL}>
                Try the demo
              </a>
              <span>No sign-up. Click around sample loads.</span>
            </div>
            <ul className="trust">
              {TRUST.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>

          <div className="hero-visual">
            <LoadPreview />
          </div>
        </section>

        <section className="section" id="how-it-works">
          <div className="heading">
            <span className="kicker">How it works</span>
            <h2>From a rate con in your inbox to a paid invoice</h2>
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

        <section className="section" id="features">
          <div className="heading">
            <span className="kicker">What it does</span>
            <h2>The paperwork side of every load, in one place</h2>
          </div>
          <div className="types types-3">
            {features.map((feature) => (
              <article key={feature.name}>
                <h3>{feature.name}</h3>
                <p>{feature.text}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="section split">
          <div>
            <span className="kicker">Your approval</span>
            <h2>Clerova drafts. You decide.</h2>
            <p>
              Nothing goes to a broker or a factor until you approve it. Edit any draft before it goes out.
              Clerova only uses what's on the rate con and your latest update, so it won't make up an ETA.
            </p>
          </div>
          <ul className="checks">
            <li>Every email and invoice waits for your approval</li>
            <li>Rate cons checked before you sign</li>
            <li>Detention claimed with the times as proof</li>
            <li>A full record of every email on each load</li>
          </ul>
        </section>

        <section className="section" id="pricing">
          <div className="heading">
            <span className="kicker">Pricing</span>
            <h2>Priced by the trucks you run. 14 days free.</h2>
            <p>Every plan includes all features. Pay yearly and get two months free.</p>
          </div>
          <div className="pricing">
            {truckPlans.map((plan) => (
              <article key={plan.name} className="price-card">
                <h3>{plan.name}</h3>
                <p className="price-note">{plan.note}</p>
                <p className="price">
                  <strong>{plan.price}</strong> {plan.unit}
                </p>
                <p className="price-founding">{plan.founding}</p>
                <ul>
                  {plan.points.map((point) => (
                    <li key={point}>{point}</li>
                  ))}
                </ul>
                <a className="button button-primary" href={trialUrl()}>
                  Start free trial
                </a>
              </article>
            ))}
          </div>
          <p className="pricing-footnote">
            You enter your trucks when you start, and Clerova picks the plan that costs less. Change your trucks
            anytime in Settings. Founding prices are for our first 20 customers and stay the same for as long as
            you stay. Running a dispatch service or 50 trucks or more? Email hello@clerova-ai.com for a price.
          </p>
        </section>

        <section className="section" id="faq">
          <div className="heading">
            <span className="kicker">FAQ</span>
            <h2>Questions from carriers</h2>
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
          <h2>Spend less time on paperwork and more time moving freight.</h2>
          <p>Start your 14-day free trial. Cancel anytime before it ends and you won't be charged.</p>
          <div className="cta-actions">
            <a className="button button-light" href={trialUrl()}>
              Start free trial
            </a>
            <a className="button button-outline-light" href={TRUCKING_DEMO_URL}>
              Try the demo
            </a>
          </div>
          <p className="cta-contact">
            Questions first? <a href="mailto:hello@clerova-ai.com">Email hello@clerova-ai.com</a>
          </p>
        </section>
      </main>

      <aside className={`sticky-cta ${showStickyCta ? "shown" : ""}`} aria-label="Free trial" aria-hidden={!showStickyCta}>
        <span>14 days free</span>
        <a className="button button-primary" href={trialUrl()} tabIndex={showStickyCta ? 0 : -1}>
          Start free trial
        </a>
      </aside>

      <footer className="footer">
        <a className="brand" href="/trucking">
          <span className="brand-mark" aria-hidden="true">C</span>
          <span className="brand-name">Clerova</span>
        </a>
        <nav className="footer-links" aria-label="Footer">
          <a href="/">Clerova for property managers</a>
          <a href="/terms">Terms</a>
          <a href="/privacy">Privacy</a>
          <a href="mailto:hello@clerova-ai.com">Contact: hello@clerova-ai.com</a>
        </nav>
        <span>© 2026 Clerova</span>
      </footer>
    </div>
  );
}
