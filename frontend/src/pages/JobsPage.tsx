import { useEffect, useRef, useState } from "react";

import { errorMessage } from "../api/client";
import { updateMaintenanceRequest } from "../api/MaintenanceRequests";
import type {
  MaintenanceRequest,
  MaintenanceRequestStatus,
} from "../types/MaintenanceRequest";
import type { WorkItem } from "../types/WorkItem";
import { ConfirmInline } from "../components/ConfirmInline";
import { hasNewReply, hasUrgentReply, requestSender, shortTime } from "../utils/requestStatus";

const COLUMNS: {
  title: string;
  dot: string;
  statuses: MaintenanceRequestStatus[];
}[] = [
  { title: "Open", dot: "review", statuses: ["OPEN"] },
  { title: "In progress", dot: "waiting", statuses: ["IN_PROGRESS"] },
  { title: "Closed", dot: "done", statuses: ["COMPLETED", "CANCELLED"] },
];

/** The job's summary as a title: "AC runs but doesn't cool." reads as a sentence. */
function jobTitle(job: MaintenanceRequest): string {
  return job.description?.trim().replace(/\.$/, "") || "Maintenance job";
}

/** The start of a long message, cut at a word. */
function clip(text: string, max: number): string {
  const trimmed = text.trim();

  if (trimmed.length <= max) {
    return trimmed;
  }

  return `${trimmed.slice(0, max).replace(/\s+\S*$/, "")}...`;
}

// Keep the Closed column short
const DONE_LIMIT = 20;

interface JobsPageProps {
  jobs: MaintenanceRequest[];
  requests: WorkItem[];
  loading: boolean;
  error: string | null;
  /** Job to point out, e.g. when opened from its request. */
  highlightWorkItemId: string | null;
  onJobUpdated: (job: MaintenanceRequest) => void;
  /** A finished job closed its request. */
  onRequestClosed: () => void;
  onOpenRequest: (workItemId: string) => void;
}

export function JobsPage({
  jobs,
  requests,
  loading,
  error,
  highlightWorkItemId,
  onJobUpdated,
  onRequestClosed,
  onOpenRequest,
}: JobsPageProps) {
  const [busyId, setBusyId] = useState<string | null>(null);
  // Notes save on their own, so a save never blocks the job's buttons
  const [savingNotesId, setSavingNotesId] = useState<string | null>(null);
  const [savedNotesId, setSavedNotesId] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  // The job opened to show what the resident wrote, with its notes
  const [openId, setOpenId] = useState<string | null>(null);
  // A job whose Cancel (or Mark done with an unanswered reply) waits for a yes
  const [confirming, setConfirming] = useState<{ jobId: string; status: MaintenanceRequestStatus } | null>(null);
  // Notes typed but not saved yet, by job
  const [notes, setNotes] = useState<Record<string, string>>({});
  // The same, readable right after typing (before a re-render), and saves
  // in flight, so a button clicked after typing waits for the save
  const latestNotes = useRef<Record<string, string>>({});
  const notesSave = useRef<Record<string, Promise<MaintenanceRequest | null>>>({});

  useEffect(() => {
    latestNotes.current = notes;
  }, [notes]);

  const unsaved = (job: MaintenanceRequest) =>
    notes[job.id] !== undefined && notes[job.id].trim() !== (job.notes ?? "").trim();
  const hasUnsaved = jobs.some(unsaved);

  // Closing the tab with unsaved notes asks first
  useEffect(() => {
    if (!hasUnsaved) {
      return;
    }

    function onBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault();
    }

    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [hasUnsaved]);

  const requestsById = new Map(requests.map((item) => [item.id, item]));

  // Bring the linked job into view once, when the board first shows it
  useEffect(() => {
    if (!loading && highlightWorkItemId) {
      document
        .querySelector(`[data-work-item="${highlightWorkItemId}"]`)
        ?.scrollIntoView({ block: "center" });
    }
  }, [loading, highlightWorkItemId]);

  function setError(jobId: string, message: string | null) {
    setErrors((current) => {
      const next = { ...current };

      if (message) {
        next[jobId] = message;
      } else {
        delete next[jobId];
      }

      return next;
    });
  }

  /** Drops the typed notes once saved, unless more was typed meanwhile. */
  function clearDraft(jobId: string, sent: string) {
    setNotes((current) => {
      if (current[jobId] !== sent) {
        return current;
      }

      const next = { ...current };
      delete next[jobId];
      return next;
    });
  }

  async function changeStatus(job: MaintenanceRequest, status: MaintenanceRequestStatus) {
    setConfirming(null);
    setBusyId(job.id);
    setError(job.id, null);

    // Clicking a button right after typing also saves the notes (blur);
    // let that finish first so the two don't race
    const pending = notesSave.current[job.id];
    const saved = pending ? await pending : null;
    const current = saved ?? job;

    // Unsaved notes go with the change, so they're never lost
    const draft = latestNotes.current[job.id];
    const send =
      draft !== undefined && draft.trim() !== (current.notes ?? "").trim() ? draft : undefined;

    try {
      const updated = await updateMaintenanceRequest(
        job.id,
        send === undefined ? { status } : { status, notes: send }
      );
      onJobUpdated(updated);

      if (draft !== undefined) {
        clearDraft(job.id, draft);
      }

      if (updated.status === "COMPLETED" || updated.status === "CANCELLED") {
        onRequestClosed();
      }
    } catch (err) {
      setError(job.id, errorMessage(err, "Couldn't update this job. Please try again."));
    } finally {
      setBusyId(null);
    }
  }

  function saveNotes(job: MaintenanceRequest): Promise<MaintenanceRequest | null> {
    const draft = latestNotes.current[job.id];
    const pending = notesSave.current[job.id];

    if (pending) {
      return pending;
    }

    if (draft === undefined || draft.trim() === (job.notes ?? "").trim()) {
      return Promise.resolve(null);
    }

    setSavingNotesId(job.id);
    setSavedNotesId(null);
    setError(job.id, null);

    const save = updateMaintenanceRequest(job.id, { notes: draft })
      .then((updated) => {
        onJobUpdated(updated);
        clearDraft(job.id, draft);
        setSavedNotesId(job.id);
        return updated;
      })
      .catch((err) => {
        setError(job.id, errorMessage(err, "Couldn't save the notes. Please try again."));
        return null;
      })
      .finally(() => {
        delete notesSave.current[job.id];
        setSavingNotesId(null);
      });

    notesSave.current[job.id] = save;
    return save;
  }

  const openCount = jobs.filter(
    (job) => job.status === "OPEN" || job.status === "IN_PROGRESS"
  ).length;

  return (
    <div className="jobs-page">
      <header className="rq-header">
        <div>
          <h1>Jobs</h1>
          <p className="jobs-intro">
            Repairs you approved from requests. {openCount} still open.
          </p>
        </div>
      </header>

      {loading && <p className="rq-empty">Loading jobs...</p>}
      {error && <p className="action-error rq-empty">{error}</p>}

      {!loading && !error && jobs.length === 0 && (
        <p className="rq-empty">
          No jobs yet. When you approve a repair on a request, it shows up
          here so you can track it to done.
        </p>
      )}

      {!loading && jobs.length > 0 && (
        <div className="jobs-board">
          {COLUMNS.map((column) => {
            // Open jobs keep their place (saving notes doesn't move them);
            // done jobs show the latest finished first
            const sortKey = (job: MaintenanceRequest) =>
              column.dot === "done" ? job.updatedAt : job.createdAt;
            const cards = jobs
              .filter((job) => column.statuses.includes(job.status))
              .sort((a, b) => sortKey(b).localeCompare(sortKey(a)))
              .slice(0, column.dot === "done" ? DONE_LIMIT : undefined);

            return (
              <section className="jobs-column" key={column.title}>
                <h2>
                  <span className={`rq-dot rq-dot-${column.dot}`} aria-hidden="true" />
                  {column.title}
                  <span>{cards.length}</span>
                </h2>

                {cards.length === 0 && <p className="jobs-none">No jobs here.</p>}

                {cards.map((job) => {
                  const request = requestsById.get(job.workItemId);
                  const busy = busyId === job.id;
                  const finished =
                    job.status === "COMPLETED" || job.status === "CANCELLED";
                  const note = notes[job.id] ?? job.notes ?? "";
                  const newReply = Boolean(request && hasNewReply(request));
                  const urgentReply = Boolean(request && hasUrgentReply(request));
                  const dirty = unsaved(job);
                  const savingNotes = savingNotesId === job.id;

                  return (
                    <article
                      key={job.id}
                      data-work-item={job.workItemId}
                      className={`jobs-card ${
                        highlightWorkItemId === job.workItemId ? "highlight" : ""
                      }`}
                    >
                      <div className="jobs-card-top">
                        <button
                          type="button"
                          className="jobs-title"
                          aria-expanded={openId === job.id}
                          onClick={() => setOpenId((current) => (current === job.id ? null : job.id))}
                        >
                          {jobTitle(job)}
                        </button>
                        {job.priority === "HIGH" && !finished && (
                          <span className="rq-urgent">Urgent</span>
                        )}
                        {urgentReply ? (
                          <span className="rq-urgent">Urgent reply</span>
                        ) : (
                          newReply && <span className="rq-new-reply">New reply</span>
                        )}
                      </div>

                      {job.address && <p className="jobs-address">{job.address}</p>}

                      <p className="jobs-meta">
                        {job.status === "CANCELLED"
                          ? `Cancelled ${shortTime(job.updatedAt)}`
                          : finished
                            ? `Done ${shortTime(job.updatedAt)}`
                            : `Created ${shortTime(job.createdAt)}`}
                      </p>

                      {openId === job.id && (
                        <div className="jobs-details">
                          {request ? (
                            <>
                              <p className="jobs-details-label">
                                What {requestSender(request)} wrote
                                {request.subject ? ` · ${request.subject}` : ""}
                              </p>
                              <p className="jobs-details-message">
                                {clip(request.originalMessage, 600)}
                              </p>
                            </>
                          ) : (
                            <p className="jobs-details-message">
                              Open the request to see the full conversation.
                            </p>
                          )}
                        </div>
                      )}

                      <div className="jobs-links">
                        <button
                          type="button"
                          className="jobs-link"
                          onClick={() => setOpenId((current) => (current === job.id ? null : job.id))}
                          aria-expanded={openId === job.id}
                        >
                          {openId === job.id ? "Hide details" : "Show details"}
                        </button>
                        <button
                          type="button"
                          className="jobs-link"
                          onClick={() => onOpenRequest(job.workItemId)}
                        >
                          {request
                            ? newReply
                              ? `Read the ${urgentReply ? "urgent" : "new"} reply from ${requestSender(request)}`
                              : `Open the request from ${requestSender(request)}`
                            : "Open the request"}
                        </button>
                      </div>

                      {/* Opening the details opens the notes too, ready to type */}
                      <details
                        className="jobs-notes"
                        key={openId === job.id ? "open" : "closed"}
                        open={openId === job.id || undefined}
                      >
                        <summary>
                          {job.notes ? (
                            <>
                              Notes
                              {/* A glance at the notes without opening them */}
                              <span className="jobs-notes-preview">{job.notes}</span>
                            </>
                          ) : finished ? (
                            "Notes"
                          ) : (
                            "Add notes"
                          )}
                        </summary>
                        {finished ? (
                          <p>{job.notes || "No notes."}</p>
                        ) : (
                          <>
                            <textarea
                              rows={3}
                              maxLength={5000}
                              value={note}
                              disabled={busy}
                              aria-label="Job notes"
                              placeholder="Vendor, access details, progress..."
                              onChange={(event) => {
                                const value = event.target.value;
                                latestNotes.current = { ...latestNotes.current, [job.id]: value };
                                setSavedNotesId((current) => (current === job.id ? null : current));
                                setNotes((current) => ({ ...current, [job.id]: value }));
                              }}
                              onBlur={() => void saveNotes(job)}
                            />
                            <div className="jobs-notes-actions">
                              <button
                                type="button"
                                className="rq-button rq-button-small"
                                disabled={busy || savingNotes || !dirty}
                                onClick={() => void saveNotes(job)}
                              >
                                {savingNotes ? "Saving..." : "Save notes"}
                              </button>
                              <span className="jobs-notes-status" role="status">
                                {savingNotes
                                  ? ""
                                  : dirty
                                    ? "Not saved yet"
                                    : savedNotesId === job.id
                                      ? "Saved"
                                      : ""}
                              </span>
                            </div>
                            <p className="jobs-notes-hint">
                              Only your team sees notes. They save when you click away.
                            </p>
                          </>
                        )}
                      </details>

                      {!finished && confirming?.jobId === job.id && (
                        <ConfirmInline
                          message={
                            confirming.status === "CANCELLED"
                              ? `Cancel this job? Its request closes too, and nothing is sent to ${request ? requestSender(request) : "the sender"}.${newReply ? " They wrote again and haven't had an answer." : ""}`
                              : `${request ? requestSender(request) : "The sender"} wrote again and hasn't had an answer. Mark the job done anyway?`
                          }
                          confirmLabel={confirming.status === "CANCELLED" ? "Yes, cancel job" : "Yes, mark done"}
                          keepLabel={confirming.status === "CANCELLED" ? "Keep job" : "Not yet"}
                          busy={busy}
                          onConfirm={() => void changeStatus(job, confirming.status)}
                          onKeep={() => setConfirming(null)}
                        />
                      )}

                      {!finished && confirming?.jobId !== job.id && (
                        <div className="jobs-actions">
                          {job.status === "OPEN" && (
                            <button
                              type="button"
                              className="rq-button rq-button-small"
                              disabled={busy}
                              onClick={() => void changeStatus(job, "IN_PROGRESS")}
                            >
                              Start
                            </button>
                          )}
                          {job.status === "IN_PROGRESS" && (
                            <button
                              type="button"
                              className="rq-button rq-button-small rq-button-primary"
                              disabled={busy}
                              onClick={() =>
                                newReply
                                  ? setConfirming({ jobId: job.id, status: "COMPLETED" })
                                  : void changeStatus(job, "COMPLETED")
                              }
                            >
                              Mark done
                            </button>
                          )}
                          <button
                            type="button"
                            className="rq-button rq-button-small rq-button-quiet"
                            disabled={busy}
                            onClick={() => setConfirming({ jobId: job.id, status: "CANCELLED" })}
                          >
                            Cancel job
                          </button>
                        </div>
                      )}

                      {errors[job.id] && (
                        <p className="action-error jobs-card-error" role="alert">
                          {errors[job.id]}
                        </p>
                      )}
                    </article>
                  );
                })}
              </section>
            );
          })}
        </div>
      )}
    </div>
  );
}
