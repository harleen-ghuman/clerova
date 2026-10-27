import { useEffect, useState } from "react";

import { errorMessage } from "../api/client";
import { updateMaintenanceRequest } from "../api/MaintenanceRequests";
import type {
  MaintenanceRequest,
  MaintenanceRequestStatus,
} from "../types/MaintenanceRequest";
import type { WorkItem } from "../types/WorkItem";
import { requestSender, shortTime } from "../utils/requestStatus";

const COLUMNS: {
  title: string;
  dot: string;
  statuses: MaintenanceRequestStatus[];
}[] = [
  { title: "Open", dot: "review", statuses: ["OPEN"] },
  { title: "In progress", dot: "waiting", statuses: ["IN_PROGRESS"] },
  { title: "Done", dot: "done", statuses: ["COMPLETED", "CANCELLED"] },
];

// Keep the Done column short
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
  const [jobError, setJobError] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});

  const requestsById = new Map(requests.map((item) => [item.id, item]));

  // Bring the linked job into view once, when the board first shows it
  useEffect(() => {
    if (!loading && highlightWorkItemId) {
      document
        .querySelector(`[data-work-item="${highlightWorkItemId}"]`)
        ?.scrollIntoView({ block: "center" });
    }
  }, [loading, highlightWorkItemId]);

  async function update(
    job: MaintenanceRequest,
    change: { status?: MaintenanceRequestStatus; notes?: string }
  ) {
    try {
      setBusyId(job.id);
      setJobError(null);

      const updated = await updateMaintenanceRequest(job.id, change);
      onJobUpdated(updated);

      if (updated.status === "COMPLETED" || updated.status === "CANCELLED") {
        onRequestClosed();
      }
    } catch (err) {
      setJobError(errorMessage(err, "Couldn't update this job. Please try again."));
    } finally {
      setBusyId(null);
    }
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
      {jobError && <p className="action-error jobs-error">{jobError}</p>}

      {!loading && !error && jobs.length === 0 && (
        <p className="rq-empty">
          No jobs yet. When you approve a repair on a request, it shows up
          here so you can track it to done.
        </p>
      )}

      {!loading && jobs.length > 0 && (
        <div className="jobs-board">
          {COLUMNS.map((column) => {
            const cards = jobs
              .filter((job) => column.statuses.includes(job.status))
              .sort(
                (a, b) =>
                  new Date(b.updatedAt).getTime() -
                  new Date(a.updatedAt).getTime()
              )
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

                  return (
                    <article
                      key={job.id}
                      data-work-item={job.workItemId}
                      className={`jobs-card ${
                        highlightWorkItemId === job.workItemId ? "highlight" : ""
                      }`}
                    >
                      <div className="jobs-card-top">
                        <strong>{job.description || "Maintenance job"}</strong>
                        {job.priority === "HIGH" && !finished && (
                          <span className="rq-urgent">Urgent</span>
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

                      <button
                        type="button"
                        className="jobs-link"
                        onClick={() => onOpenRequest(job.workItemId)}
                      >
                        {request
                          ? `From request: ${requestSender(request)}`
                          : "View request"}
                      </button>

                      <details className="jobs-notes">
                        <summary>{job.notes ? "Notes" : "Add notes"}</summary>
                        {finished ? (
                          <p>{job.notes || "No notes."}</p>
                        ) : (
                          <>
                            <textarea
                              rows={3}
                              maxLength={5000}
                              value={note}
                              disabled={busy}
                              placeholder="Vendor, access, progress..."
                              onChange={(event) =>
                                setNotes((current) => ({
                                  ...current,
                                  [job.id]: event.target.value,
                                }))
                              }
                            />
                            <button
                              type="button"
                              className="rq-button rq-button-small"
                              disabled={busy || note === (job.notes ?? "")}
                              onClick={() => update(job, { notes: note })}
                            >
                              Save notes
                            </button>
                          </>
                        )}
                      </details>

                      {!finished && (
                        <div className="jobs-actions">
                          {job.status === "OPEN" && (
                            <button
                              type="button"
                              className="rq-button rq-button-small"
                              disabled={busy}
                              onClick={() => update(job, { status: "IN_PROGRESS" })}
                            >
                              Start
                            </button>
                          )}
                          {job.status === "IN_PROGRESS" && (
                            <button
                              type="button"
                              className="rq-button rq-button-small rq-button-primary"
                              disabled={busy}
                              onClick={() => update(job, { status: "COMPLETED" })}
                            >
                              Mark done
                            </button>
                          )}
                          <button
                            type="button"
                            className="rq-button rq-button-small rq-button-quiet"
                            disabled={busy}
                            onClick={() => {
                              if (
                                window.confirm(
                                  "Cancel this job? Its request will be closed too."
                                )
                              ) {
                                void update(job, { status: "CANCELLED" });
                              }
                            }}
                          >
                            Cancel job
                          </button>
                        </div>
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
