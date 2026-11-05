import { useEffect, useState, type FormEvent } from "react";
import { getCarrier, saveCarrier } from "../api/loads";
import { errorMessage } from "../api/client";
import type { CarrierDetails } from "../types/Load";

type Form = Omit<CarrierDetails, "missing" | "nextInvoiceNumber"> & { nextInvoiceNumber: string };

const EMPTY: Form = {
  legalName: "",
  mcNumber: "",
  dotNumber: "",
  address: "",
  phone: "",
  email: "",
  invoicePrefix: "",
  nextInvoiceNumber: "1001",
  usesFactoring: false,
  factorName: "",
  factorAddress: "",
  factorEmail: "",
};

function toForm(details: CarrierDetails): Form {
  return {
    legalName: details.legalName ?? "",
    mcNumber: details.mcNumber ?? "",
    dotNumber: details.dotNumber ?? "",
    address: details.address ?? "",
    phone: details.phone ?? "",
    email: details.email ?? "",
    invoicePrefix: details.invoicePrefix ?? "",
    nextInvoiceNumber: String(details.nextInvoiceNumber),
    usesFactoring: details.usesFactoring,
    factorName: details.factorName ?? "",
    factorAddress: details.factorAddress ?? "",
    factorEmail: details.factorEmail ?? "",
  };
}

/** The carrier's details and factoring, printed on every invoice. */
export function CarrierSection({ focus = false }: { focus?: boolean }) {
  const [form, setForm] = useState<Form>(EMPTY);
  const [loaded, setLoaded] = useState<Form | null>(null);
  const [missing, setMissing] = useState<string[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let current = true;

    getCarrier()
      .then((details) => {
        if (current) {
          setForm(toForm(details));
          setLoaded(toForm(details));
          setMissing(details.missing);
        }
      })
      .catch((err) => {
        if (current) {
          setLoadError(errorMessage(err, "Couldn't load your carrier details. Refresh to try again."));
        }
      });

    return () => {
      current = false;
    };
  }, []);

  useEffect(() => {
    if (focus && loaded) {
      document.getElementById("carrier-details")?.scrollIntoView({ block: "start" });
    }
  }, [focus, loaded]);

  function set<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((current) => ({ ...current, [key]: value }));
    setSaved(false);
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = Number(form.nextInvoiceNumber.trim());

    if (!Number.isInteger(next) || next < 1) {
      setError("Enter the next invoice number, like 1001.");
      return;
    }

    setSaving(true);
    setError(null);
    setSaved(false);

    try {
      const details = await saveCarrier({
        ...form,
        nextInvoiceNumber: next,
      });
      setForm(toForm(details));
      setLoaded(toForm(details));
      setMissing(details.missing);
      setSaved(true);
    } catch (err) {
      setError(errorMessage(err, "Couldn't save your carrier details. Please try again."));
    } finally {
      setSaving(false);
    }
  }

  const changed = loaded !== null && JSON.stringify(form) !== JSON.stringify(loaded);
  const prefix = (form.invoicePrefix ?? "").trim();
  const sample = `${prefix}${form.nextInvoiceNumber.trim() || "1001"}`;

  return (
    <section className="settings-section" id="carrier-details" aria-labelledby="carrier-details-title">
      <h2 id="carrier-details-title">Carrier details</h2>
      <p>Printed on every invoice. Enter them once.</p>

      {loadError ? (
        <p className="action-error" role="alert">
          {loadError}
        </p>
      ) : !loaded ? (
        <p className="tk-none">Loading...</p>
      ) : (
        <form className="auth-form settings-form" onSubmit={save}>
          {missing.length > 0 && (
            <p className="tk-callout">Invoices need {missing.join(", ")}.</p>
          )}

          <label>
            Company legal name
            <input
              autoComplete="organization"
              maxLength={255}
              value={form.legalName ?? ""}
              onChange={(event) => set("legalName", event.target.value)}
              disabled={saving}
            />
          </label>
          <div className="settings-row">
            <label>
              MC number
              <input
                inputMode="numeric"
                maxLength={20}
                value={form.mcNumber ?? ""}
                onChange={(event) => set("mcNumber", event.target.value)}
                disabled={saving}
              />
            </label>
            <label>
              DOT number
              <input
                inputMode="numeric"
                maxLength={20}
                value={form.dotNumber ?? ""}
                onChange={(event) => set("dotNumber", event.target.value)}
                disabled={saving}
              />
            </label>
          </div>
          <label>
            Mailing address
            <textarea
              rows={3}
              autoComplete="street-address"
              maxLength={1000}
              value={form.address ?? ""}
              onChange={(event) => set("address", event.target.value)}
              disabled={saving}
            />
          </label>
          <div className="settings-row">
            <label>
              Phone
              <input
                type="tel"
                autoComplete="tel"
                maxLength={50}
                value={form.phone ?? ""}
                onChange={(event) => set("phone", event.target.value)}
                disabled={saving}
              />
            </label>
            <label>
              Billing email
              <input
                type="email"
                autoComplete="email"
                maxLength={320}
                value={form.email ?? ""}
                onChange={(event) => set("email", event.target.value)}
                disabled={saving}
              />
            </label>
          </div>
          <span className="auth-hint">Replies to your invoices go to the billing email.</span>

          <h3 className="settings-subhead">Invoice numbers</h3>
          <div className="settings-row">
            <label>
              Prefix
              <input
                maxLength={20}
                placeholder="AQ-"
                value={form.invoicePrefix ?? ""}
                onChange={(event) => set("invoicePrefix", event.target.value)}
                disabled={saving}
              />
            </label>
            <label>
              Next number
              <input
                inputMode="numeric"
                maxLength={8}
                value={form.nextInvoiceNumber}
                onChange={(event) => set("nextInvoiceNumber", event.target.value.replace(/\D/g, ""))}
                disabled={saving}
              />
            </label>
          </div>
          <span className="auth-hint">Your next invoice will be {sample}.</span>

          <h3 className="settings-subhead">Factoring</h3>
          <label className="settings-check">
            <input
              type="checkbox"
              checked={form.usesFactoring}
              onChange={(event) => set("usesFactoring", event.target.checked)}
              disabled={saving}
            />
            I use a factoring company
          </label>

          {form.usesFactoring && (
            <>
              <span className="auth-hint">
                Invoices tell the broker to pay your factoring company, and the packet is ready to upload to its
                website.
              </span>
              <label>
                Factoring company
                <input
                  maxLength={255}
                  value={form.factorName ?? ""}
                  onChange={(event) => set("factorName", event.target.value)}
                  disabled={saving}
                />
              </label>
              <label>
                Where brokers pay it
                <textarea
                  rows={3}
                  maxLength={1000}
                  placeholder="The remit-to address from your notice of assignment"
                  value={form.factorAddress ?? ""}
                  onChange={(event) => set("factorAddress", event.target.value)}
                  disabled={saving}
                />
              </label>
              <label>
                Email for paperwork (optional)
                <input
                  type="email"
                  maxLength={320}
                  value={form.factorEmail ?? ""}
                  onChange={(event) => set("factorEmail", event.target.value)}
                  disabled={saving}
                />
                <span className="auth-hint">If your factor takes invoices by email, Clerova can send them there.</span>
              </label>
            </>
          )}

          {error && (
            <p className="action-error" role="alert">
              {error}
            </p>
          )}

          <div className="settings-actions">
            <button type="submit" className="primary-button" disabled={saving || !changed}>
              {saving ? "Saving..." : "Save"}
            </button>
            {saved && <span className="request-success">Saved.</span>}
          </div>
        </form>
      )}
    </section>
  );
}
