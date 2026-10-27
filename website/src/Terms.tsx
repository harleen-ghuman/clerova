import { LegalPage } from "./LegalPage";

export function Terms() {
  return (
    <LegalPage title="Terms of Service" updated="October 26, 2026">
      <div className="legal-summary">
        <p>
          <strong>The short version:</strong> Clerova organizes the emails you
          forward and drafts replies. You review and approve everything before
          it is sent. Your data stays yours. Plans renew automatically until
          you cancel, and cancelling during your free trial costs nothing.
        </p>
      </div>

      <h2>1. Who we are</h2>
      <p>
        Clerova ("Clerova", "we", "us") provides the software at
        clerova-ai.com and app.clerova-ai.com (the "Service"). These terms are
        an agreement between us and the business or person that creates a
        Clerova account ("you"). By creating an account or using the Service,
        you agree to these terms. If you use Clerova for a company, you confirm
        you can accept these terms for it.
      </p>

      <h2>2. What Clerova does</h2>
      <p>
        You forward emails to your Clerova address or add requests yourself.
        Clerova uses artificial intelligence (AI) to read each request, pull
        out details such as the address, type and priority, suggest a next
        step and draft a reply. Nothing is sent and no job is created until a
        person on your account approves it.
      </p>

      <h2>3. Your account</h2>
      <ul>
        <li>You must give accurate information and keep your login details safe.</li>
        <li>You are responsible for what happens under your account.</li>
        <li>You must be at least 18 years old and use Clerova for business purposes.</li>
        <li>Tell us right away at hello@clerova-ai.com if you think your account has been misused.</li>
      </ul>

      <h2>4. Free trial, billing and cancellation</h2>
      <ul>
        <li>
          New accounts get a 14-day free trial. We ask for a payment card when
          the trial starts. You are not charged during the trial.
        </li>
        <li>
          If you don't cancel before the trial ends, your plan starts and your
          card is charged for the first billing period. Plans renew
          automatically each month or year until you cancel.
        </li>
        <li>
          You can cancel anytime in the app, in Settings under Plan and billing. If you cancel,
          you keep access until the end of your trial or the period you paid
          for. We don't refund partial periods unless the law requires it.
        </li>
        <li>
          Each plan includes a number of requests per month. If you reach it,
          new emails are saved but not organized until the next month or until
          you upgrade. If you upgrade during a billing period, the difference
          for that period is added to your next bill.
        </li>
        <li>
          Founding prices stay the same for as long as your subscription stays
          active. We may change other prices with at least 30 days' notice by
          email. The new price applies from your next renewal.
        </li>
        <li>
          Payments are handled by Stripe. Prices don't include taxes, which we
          add where required.
        </li>
      </ul>

      <h2>5. Your data</h2>
      <p>
        You own the emails, requests, replies and other content you put into
        Clerova ("Your Data"). You give us permission to store and process Your
        Data only to provide and improve the Service for you, as described in
        our <a href="/privacy">Privacy Policy</a>. We don't sell Your Data and
        don't use it to advertise to anyone.
      </p>
      <p>
        You confirm that you have the right to forward the emails you send to
        Clerova and to share the information in them with us, including
        information about your tenants, owners and vendors.
      </p>

      <h2>6. AI suggestions</h2>
      <p>
        AI can be wrong or incomplete. Clerova does not have access to your
        rent records, leases or other systems unless you add that information
        yourself. Suggestions and drafts are a starting point. You are
        responsible for reviewing them and for what you approve and send. Don't
        rely on Clerova for legal, financial or emergency decisions. For
        emergencies such as fires, floods or gas leaks, follow your usual
        emergency procedures.
      </p>

      <h2>7. Acceptable use</h2>
      <p>You agree not to:</p>
      <ul>
        <li>use Clerova to send spam or unlawful, harassing or discriminatory messages;</li>
        <li>break fair housing, privacy, consumer protection or other laws;</li>
        <li>try to access other customers' data or break, overload or reverse engineer the Service;</li>
        <li>resell the Service without our written permission.</li>
      </ul>
      <p>We may suspend accounts that break these rules or put the Service or others at risk.</p>

      <h2>8. Availability and changes</h2>
      <p>
        We work to keep Clerova available and secure, but we don't promise it
        will always be available or error-free. We may improve or change
        features over time. If we remove something important you pay for, we
        will tell you in advance.
      </p>

      <h2>9. Ending your account</h2>
      <p>
        You can stop using Clerova anytime. We may end or suspend your account
        if you seriously break these terms or don't pay. After your account
        ends, you can ask us at hello@clerova-ai.com to export or delete Your
        Data. We delete it within 30 days of your request, except where we must
        keep records by law.
      </p>

      <h2>10. Disclaimers and limits of liability</h2>
      <p>
        The Service is provided "as is" and "as available". To the extent the
        law allows, we disclaim all implied warranties, including
        merchantability and fitness for a particular purpose.
      </p>
      <p>
        To the extent the law allows, we are not liable for indirect,
        incidental, special or consequential damages, or for lost profits,
        revenue or data. Our total liability for any claim relating to the
        Service is limited to the amount you paid us in the 12 months before
        the claim.
      </p>

      <h2>11. Changes to these terms</h2>
      <p>
        We may update these terms. If a change is important, we will email the
        account owner at least 14 days before it takes effect. Continuing to
        use Clerova after that means you accept the new terms.
      </p>

      <h2>12. Governing law</h2>
      <p>
        These terms are governed by the laws of the State of California, USA,
        without regard to its conflict of law rules. Disputes will be handled
        in the state or federal courts located in California.
      </p>

      <h2>13. Contact</h2>
      <p>
        Questions about these terms: <a href="mailto:hello@clerova-ai.com">hello@clerova-ai.com</a>.
      </p>
    </LegalPage>
  );
}
