import { useId, useState, type FormEvent } from "react";
import { errorMessage } from "../api/client";
import { createLoad, type NewLoad } from "../api/loads";
import type { InboundMessage } from "../types/Email";
import type { LoadDetail } from "../types/Load";
import { getHomeCurrency } from "./format";

interface StopRow {
  kind: "PICKUP" | "DELIVERY";
  place: string;
  appointment: string;
}

const MAX_STOPS = 10;

/** "Rate Confirmation 88213" or "Load #TX-4471" to the load number; nothing when it isn't clear. */
function loadNumberFrom(subject: string | null): string {
  const match = (subject ?? "").match(/(?:load|rate\s*con(?:firmation)?|order|pro|#)\s*(?:no\.?|number)?\s*#?\s*:?\s*([A-Z0-9][A-Z0-9-]{3,})/i);
  return match && /\d/.test(match[1]) ? match[1] : "";
}

/** The broker from an email, unless it's the dispatcher's own forward. */
function brokerFrom(message?: InboundMessage) {
  if (!message || /^\s*(fwd?|fw):/i.test(message.subject ?? "")) {
    return { name: "", email: "" };
  }

  return { name: message.fromName ?? "", email: message.fromEmail ?? "" };
}

/** A load booked by phone, text or a portal, or from an email Clerova couldn't read. */
export function NewLoadForm({
  fromEmail,
  onCreated,
  onCancel,
}: {
  fromEmail?: InboundMessage;
  onCreated: (load: LoadDetail) => void;
  onCancel: () => void;
}) {
  const id = useId();
  const broker = brokerFrom(fromEmail);
  const [loadNumber, setLoadNumber] = useState(loadNumberFrom(fromEmail?.subject ?? null));
  const [brokerName, setBrokerName] = useState(broker.name);
  const [brokerEmail, setBrokerEmail] = useState(broker.email);
  const [brokerMc, setBrokerMc] = useState("");
  const [rate, setRate] = useState("");
  const [equipment, setEquipment] = useState("");
  const canadian = getHomeCurrency() === "CAD";
  const [currency, setCurrency] = useState<"CAD" | "USD">(canadian ? "CAD" : "USD");
  const [stops, setStops] = useState<StopRow[]>([
    { kind: "PICKUP", place: "", appointment: "" },
    { kind: "DELIVERY", place: "", appointment: "" },
  ]);
  const [detentionFreeHours, setDetentionFreeHours] = useState("");
  const [detentionRatePerHour, setDetentionRatePerHour] = useState("");
  const [tonu, setTonu] = useState("");
  const [paymentTerms, setPaymentTerms] = useState("");
  const [invoiceEmail, setInvoiceEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function changeStop(index: number, change: Partial<StopRow>) {
    setStops((current) => current.map((stop, i) => (i === index ? { ...stop, ...change } : stop)));
  }

  async function save(event: FormEvent) {
    event.preventDefault();

    if (!brokerName.trim()) {
      setError("Add the broker's name.");
      return;
    }

    if (!stops.some((stop) => stop.kind === "PICKUP" && stop.place.trim()) || !stops.some((stop) => stop.kind === "DELIVERY" && stop.place.trim())) {
      setError("Add where the load picks up and where it delivers.");
      return;
    }

    const request: NewLoad = {
      loadNumber: loadNumber.trim() || null,
      brokerName: brokerName.trim(),
      brokerEmail: brokerEmail.trim() || null,
      brokerMc: brokerMc.trim() || null,
      rate: rate.trim() || null,
      equipment: equipment.trim() || null,
      stops: stops
        .filter((stop) => stop.place.trim())
        .map((stop) => ({ kind: stop.kind, place: stop.place.trim(), appointment: stop.appointment.trim() || null })),
      detentionFreeHours: detentionFreeHours.trim() || null,
      detentionRatePerHour: detentionRatePerHour.trim() || null,
      tonu: tonu.trim() || null,
      paymentTerms: paymentTerms.trim() || null,
      invoiceEmail: invoiceEmail.trim() || null,
      fromEmailId: fromEmail?.id ?? null,
      currency,
    };

    setBusy(true);
    setError(null);

    try {
      onCreated(await createLoad(request));
    } catch (err) {
      setError(errorMessage(err, "Couldn't make the load. Please try again."));
      setBusy(false);
    }
  }

  return (
    <form
      className="tk-panel tk-new-load auth-form"
      onSubmit={(event) => void save(event)}
      onInput={() => setError(null)}
      aria-labelledby={`${id}-title`}
      noValidate
    >
      <h2 id={`${id}-title`}>{fromEmail ? "Make a new load from this email" : "Add a load"}</h2>
      <p className="tk-sub">
        {fromEmail
          ? "Type in what the rate con says. This email and its files go on the new load."
          : "For a load booked by phone, text or a broker's website. Only the broker, pickup and delivery are needed now."}
      </p>

      <div className="tk-new-load-grid">
        <label>
          Broker name
          <input value={brokerName} onChange={(event) => setBrokerName(event.target.value)} maxLength={255} required autoComplete="off" />
        </label>
        <label>
          Load number <span className="tk-optional">(optional)</span>
          <input value={loadNumber} onChange={(event) => setLoadNumber(event.target.value)} maxLength={100} autoComplete="off" />
        </label>
        <label>
          Broker email <span className="tk-optional">(optional)</span>
          <input type="email" value={brokerEmail} onChange={(event) => setBrokerEmail(event.target.value)} maxLength={320} autoComplete="off" />
        </label>
        <label>
          Broker MC <span className="tk-optional">(optional)</span>
          <input value={brokerMc} onChange={(event) => setBrokerMc(event.target.value)} maxLength={20} inputMode="numeric" autoComplete="off" />
        </label>
        <label>
          Rate <span className="tk-optional">(optional)</span>
          <input value={rate} onChange={(event) => setRate(event.target.value)} maxLength={20} inputMode="decimal" placeholder="2150" autoComplete="off" />
        </label>
        {canadian && (
          <label>
            Paid in
            <select value={currency} onChange={(event) => setCurrency(event.target.value as "CAD" | "USD")}>
              <option value="CAD">CAD</option>
              <option value="USD">USD</option>
            </select>
          </label>
        )}
        <label>
          Equipment <span className="tk-optional">(optional)</span>
          <input value={equipment} onChange={(event) => setEquipment(event.target.value)} maxLength={100} placeholder="53' Dry Van" autoComplete="off" />
        </label>
      </div>

      <fieldset className="tk-new-stops">
        <legend>Stops</legend>
        {stops.map((stop, index) => (
          <div key={index} className="tk-new-stop">
            <label>
              <span className="visually-hidden">Stop {index + 1} is a</span>
              <select value={stop.kind} onChange={(event) => changeStop(index, { kind: event.target.value as StopRow["kind"] })}>
                <option value="PICKUP">Pickup</option>
                <option value="DELIVERY">Delivery</option>
              </select>
            </label>
            <label className="tk-new-stop-place">
              <span className="visually-hidden">Stop {index + 1} address</span>
              <input
                value={stop.place}
                onChange={(event) => changeStop(index, { place: event.target.value })}
                placeholder={stop.kind === "PICKUP" ? "Pickup: name, town, state" : "Delivery: name, town, state"}
                maxLength={500}
                autoComplete="off"
              />
            </label>
            <label>
              <span className="visually-hidden">Stop {index + 1} date and time</span>
              <input
                value={stop.appointment}
                onChange={(event) => changeStop(index, { appointment: event.target.value })}
                placeholder="Date and time"
                maxLength={200}
                autoComplete="off"
              />
            </label>
            {stops.length > 2 && (
              <button
                type="button"
                className="tk-link tk-link-small"
                onClick={() => setStops((current) => current.filter((_, i) => i !== index))}
              >
                Remove
              </button>
            )}
          </div>
        ))}
        {stops.length < MAX_STOPS && (
          <button
            type="button"
            className="tk-link tk-link-small"
            onClick={() => setStops((current) => [...current, { kind: "DELIVERY", place: "", appointment: "" }])}
          >
            + Add a stop
          </button>
        )}
      </fieldset>

      <details className="tk-new-terms">
        <summary>Detention, TONU and payment terms (optional)</summary>
        <div className="tk-new-load-grid">
          <label>
            Free time, hours
            <input value={detentionFreeHours} onChange={(event) => setDetentionFreeHours(event.target.value)} inputMode="decimal" placeholder="2" maxLength={6} />
          </label>
          <label>
            Detention, per hour
            <input value={detentionRatePerHour} onChange={(event) => setDetentionRatePerHour(event.target.value)} inputMode="decimal" placeholder="50" maxLength={12} />
          </label>
          <label>
            TONU
            <input value={tonu} onChange={(event) => setTonu(event.target.value)} inputMode="decimal" placeholder="150" maxLength={12} />
          </label>
          <label>
            Payment terms
            <input value={paymentTerms} onChange={(event) => setPaymentTerms(event.target.value)} placeholder="Net 30" maxLength={500} />
          </label>
          <label>
            Invoices go to
            <input type="email" value={invoiceEmail} onChange={(event) => setInvoiceEmail(event.target.value)} placeholder="billing@broker.com" maxLength={320} />
          </label>
        </div>
      </details>

      {error && (
        <p className="action-error" role="alert">
          {error}
        </p>
      )}

      <div className="tk-actions">
        <button type="submit" className="rq-button rq-button-small rq-button-primary" disabled={busy}>
          {busy ? "Making the load..." : "Make the load"}
        </button>
        <button type="button" className="rq-button rq-button-small rq-button-quiet" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
    </form>
  );
}
