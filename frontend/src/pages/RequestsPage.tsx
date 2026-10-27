import { useState, type FormEvent } from "react";

import { errorMessage } from "../api/client";
import { RequestDetail } from "../components/RequestDetail";
import type { WorkItem } from "../types/WorkItem";
import {
  BUCKET_LABELS,
  hasNewReply,
  requestBucket,
  requestSender,
  requestStatusLabel,
  requestTitle,
  shortTime,
  type RequestBucket,
} from "../utils/requestStatus";

export type RequestFilter = RequestBucket | "all";

const FILTERS: RequestFilter[] = ["review", "waiting", "progress", "done", "all"];

interface RequestsPageProps {
  /** Open requests followed by recently closed ones. */
  requests: WorkItem[];
  loading: boolean;
  error: string | null;
  selected: WorkItem | null;
  filter: RequestFilter;
  onFilterChange: (filter: RequestFilter) => void;
  emailSendingEnabled: boolean;
  onSelect: (workItem: WorkItem | null) => void;
  onCreate: (message: string) => Promise<WorkItem>;
  onChanged: () => void;
  onOpenJobs: (workItemId: string) => void;
}

export function RequestsPage({
  requests,
  loading,
  error,
  selected,
  filter,
  onFilterChange: setFilter,
  emailSendingEnabled,
  onSelect,
  onCreate,
  onChanged,
  onOpenJobs,
}: RequestsPageProps) {
  const [search, setSearch] = useState("");
  const [composing, setComposing] = useState(false);
  const [draft, setDraft] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const query = search.trim().toLowerCase();
  const matching = query
    ? requests.filter((item) =>
        [
          item.subject,
          item.summary,
          item.address,
          item.senderName,
          item.senderEmail,
          item.originalMessage,
        ].some((value) => value?.toLowerCase().includes(query))
      )
    : requests;

  // A new email during a job needs a person too, so it shows in review
  function inFilter(item: WorkItem, key: RequestFilter) {
    return (
      key === "all" ||
      requestBucket(item) === key ||
      (key === "review" && hasNewReply(item))
    );
  }

  const visible = matching.filter((item) => inFilter(item, filter));

  function countFor(key: RequestFilter) {
    return matching.filter((item) => inFilter(item, key)).length;
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const message = draft.trim();

    if (!message) {
      setCreateError("Paste or type the request first.");
      return;
    }

    try {
      setCreating(true);
      setCreateError(null);

      const created = await onCreate(message);

      setDraft("");
      setComposing(false);
      setFilter("all");
      onSelect(created);
    } catch (err) {
      setCreateError(errorMessage(err, "Couldn't add the request. Please try again."));
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className={`rq-page ${selected ? "rq-has-selection" : ""}`}>
      <header className="rq-header">
        <h1>Requests</h1>

        <label className="rq-search">
          <svg
            aria-hidden="true"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          >
            <circle cx="11" cy="11" r="7" />
            <path d="M20 20l-3.5-3.5" />
          </svg>
          <span className="visually-hidden">Search requests</span>
          <input
            type="search"
            placeholder="Search by name, address or subject"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>

        <button
          type="button"
          className="rq-button"
          onClick={() => setComposing((value) => !value)}
          aria-expanded={composing}
        >
          + New request
        </button>

        <nav className="rq-filters" aria-label="Filter requests">
          {FILTERS.map((key) => (
            <button
              key={key}
              type="button"
              className={`rq-chip ${filter === key ? "active" : ""}`}
              aria-pressed={filter === key}
              onClick={() => setFilter(key)}
            >
              {key === "all" ? "All" : BUCKET_LABELS[key]}
              <span>{countFor(key)}</span>
            </button>
          ))}
        </nav>
      </header>

      {composing && (
        <form className="rq-compose" onSubmit={handleCreate}>
          <label htmlFor="new-request">
            Paste a request you received by phone, text or another inbox
          </label>
          <textarea
            id="new-request"
            rows={4}
            value={draft}
            disabled={creating}
            placeholder="Example: Water is leaking under the kitchen sink at 12 Oak St, Unit 4B."
            onChange={(event) => setDraft(event.target.value)}
          />
          {createError && <p className="action-error">{createError}</p>}
          <div className="rq-compose-actions">
            <button
              type="submit"
              className="rq-button rq-button-primary"
              disabled={creating}
            >
              {creating ? "Organizing..." : "Create request"}
            </button>
            <button
              type="button"
              className="rq-button"
              onClick={() => setComposing(false)}
              disabled={creating}
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      <div className="rq-body">
        <section className="rq-list" aria-label="Request list">
          {loading && <p className="rq-empty">Loading requests...</p>}
          {error && <p className="action-error rq-empty">{error}</p>}

          {!loading && !error && visible.length === 0 && (
            <p className="rq-empty">
              {filter === "review" && !query
                ? "Nothing to review. You're all caught up."
                : "No requests here."}
            </p>
          )}

          {visible.map((item) => {
            const bucket = requestBucket(item);

            return (
              <button
                key={item.id}
                type="button"
                className={`rq-row ${selected?.id === item.id ? "active" : ""}`}
                onClick={() => onSelect(item)}
              >
                <span className="rq-row-top">
                  <span className={`rq-dot rq-dot-${bucket}`} aria-hidden="true" />
                  <strong>{requestSender(item)}</strong>
                  {item.priority === "HIGH" && bucket !== "done" && (
                    <span className="rq-urgent">Urgent</span>
                  )}
                  {hasNewReply(item) && (
                    <span className="rq-new-reply">New reply</span>
                  )}
                  <time>{shortTime(item.createdAt)}</time>
                </span>
                <span className="rq-row-title">{requestTitle(item)}</span>
                <span className="rq-row-preview">{item.summary}</span>
                <span className="rq-row-meta">
                  {requestStatusLabel(item)} ·{" "}
                  {item.category.charAt(0) + item.category.slice(1).toLowerCase()}
                </span>
              </button>
            );
          })}
        </section>

        {selected ? (
          <RequestDetail
            key={selected.id}
            workItem={selected}
            emailSendingEnabled={emailSendingEnabled}
            onChanged={onChanged}
            onOpenJobs={onOpenJobs}
            onBack={() => onSelect(null)}
          />
        ) : (
          <section className="rq-detail rq-detail-empty">
            <p>Select a request to see the conversation and Clerova's suggestion.</p>
          </section>
        )}
      </div>
    </div>
  );
}
