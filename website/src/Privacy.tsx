import { LegalPage } from "./LegalPage";

export function Privacy() {
  return (
    <LegalPage title="Privacy Policy" updated="October 26, 2026">
      <div className="legal-summary">
        <p>
          <strong>The short version:</strong> We collect what we need to run
          Clerova: your account details and the emails you choose to forward.
          We use them only to provide the Service. We don't sell personal
          information or show ads, and you can ask us to delete your data
          anytime.
        </p>
      </div>

      <h2>1. Who this applies to</h2>
      <p>
        This policy explains how Clerova ("we", "us") handles personal
        information on clerova-ai.com and in the Clerova app. Our customers are
        businesses, such as property management companies. When a customer
        forwards emails to Clerova, those emails can include information about
        other people, like tenants, owners and vendors. We process that
        information on the customer's behalf and follow the customer's
        instructions.
      </p>

      <h2>2. What we collect</h2>
      <ul>
        <li>
          <strong>Account details:</strong> your name, email address, company
          name, number of units managed, and how you heard about us.
        </li>
        <li>
          <strong>Emails and requests you send to Clerova:</strong> sender
          names and email addresses, subjects, message text, attachment names,
          and the replies you approve and send.
        </li>
        <li>
          <strong>Billing details:</strong> your plan and payment status.
          Stripe collects and stores your card details. We never see your full
          card number.
        </li>
        <li>
          <strong>Usage and technical data:</strong> how many requests you
          process, activity on each request, and basic logs (such as IP address
          and browser type) that keep the Service secure and working.
        </li>
      </ul>

      <h2>3. How we use it</h2>
      <ul>
        <li>to organize your requests, suggest next steps and draft replies;</li>
        <li>to send the replies you approve, and account emails such as usage warnings;</li>
        <li>to run billing, the free trial and your plan's limits;</li>
        <li>to keep the Service secure, fix problems and improve it;</li>
        <li>to answer your questions and meet legal obligations.</li>
      </ul>
      <p>
        We don't sell or rent personal information, and we don't use it for
        advertising.
      </p>

      <h2>4. AI processing</h2>
      <p>
        To organize requests and draft replies, we send the content of each
        request to our AI provider, OpenAI, through its business API. Under
        OpenAI's API terms, this data is not used to train its models by
        default. We don't use your data to train AI models either.
      </p>

      <h2>5. Service providers we use</h2>
      <p>We share information only with providers that help us run Clerova:</p>
      <ul>
        <li><strong>Supabase:</strong> database and sign-in</li>
        <li><strong>Render:</strong> hosting for the app and website</li>
        <li><strong>Postmark:</strong> receiving forwarded emails and sending replies and account emails</li>
        <li><strong>OpenAI:</strong> AI processing of requests</li>
        <li><strong>Stripe:</strong> payments and billing</li>
        <li><strong>Cloudflare:</strong> domain and network services</li>
        <li><strong>Google Workspace:</strong> our support email</li>
      </ul>
      <p>
        These providers may process data in the United States. We may also
        share information if the law requires it, to protect people's safety,
        or as part of a merger or sale of our business, in which case this
        policy will continue to apply.
      </p>

      <h2>6. How long we keep it</h2>
      <p>
        We keep your data while your account is open. If you close your
        account or ask us to delete your data, we delete it within 30 days,
        except records we must keep by law (such as billing records).
      </p>

      <h2>7. Security</h2>
      <p>
        Data is encrypted in transit, access to it is limited, and each
        customer's data is kept separate from other customers'. No system is
        perfectly secure, but we work to protect your information and will
        tell you if a breach affects your data.
      </p>

      <h2>8. Cookies and local storage</h2>
      <p>
        The app uses your browser's storage to keep you signed in and to
        remember how you found us (for example, a link from Instagram). We
        don't use advertising cookies or third-party ad trackers.
      </p>

      <h2>9. Your choices and rights</h2>
      <p>
        You can see and update your account details, and you can ask us to
        access, correct, export or delete your personal information by emailing
        hello@clerova-ai.com. Depending on where you live, such as California
        under the CCPA, you may have more rights. We won't treat you
        differently for using them.
      </p>
      <p>
        If you are a tenant, owner or vendor whose emails were forwarded to
        Clerova by a property manager, please contact that property manager
        first. We will help them respond to your request.
      </p>

      <h2>10. Children</h2>
      <p>Clerova is for businesses and is not meant for anyone under 18.</p>

      <h2>11. Changes to this policy</h2>
      <p>
        We may update this policy. If a change is important, we will email
        account owners before it takes effect. The date at the top shows the
        latest version.
      </p>

      <h2>12. Contact</h2>
      <p>
        Questions or requests about privacy: <a href="mailto:hello@clerova-ai.com">hello@clerova-ai.com</a>.
      </p>
    </LegalPage>
  );
}
