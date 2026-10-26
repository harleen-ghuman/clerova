import { useEffect, useState } from "react";

import { getWeekSummary, type WeekSummary } from "../api/summary";
import type { MaintenanceRequest } from "../types/MaintenanceRequest";
import type { WorkItem } from "../types/WorkItem";
import {
  requestBucket,
  requestSender,
  requestTitle,
  shortTime,
  type RequestBucket,
} from "../utils/requestStatus";

const DAY_MS = 24 * 60 * 60 * 1000;
// Customers who haven't answered in this many days are worth a nudge
const WAITING_DAYS = 3;

interface TodayPageProps {
  organizationName: string;
  /** Open requests; closed ones are ignored here. */
  requests: WorkItem[];
  jobs: MaintenanceRequest[];
  refreshKey: number;
  onOpenRequests: (filter: RequestBucket) => void;
  onOpenRequest: (workItemId: string) => void;
  onOpenJobs: () => void;
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) {
    return `${minutes} min`;
  }

  const hours = minutes / 60;
  return hours < 24 ? `${hours.toFixed(hours < 10 ? 1 : 0)} h` : `${Math.round(hours / 24)} days`;
}

export function TodayPage({
  organizationName,
  requests,
  jobs,
  refreshKey,
  onOpenRequests,
  onOpenRequest,
  onOpenJobs,
}: TodayPageProps) {
  const [summary, setSummary] = useState<WeekSummary | null>(null);
  // Fixed per refresh so "waiting 3+ days" doesn't change between renders
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let cancelled = false;

    getWeekSummary()
      .then((result) => {
        if (!cancelled) {
          setSummary(result);
          setNow(Date.now());
        }
      })
      .catch((err) => console.error("Failed to load the summary:", err));

    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  const open = requests.filter((item) => requestBucket(item) !== "done");
  const review = open.filter((item) => requestBucket(item) === "review");
  const urgent = open.filter(
    (item) => item.priority === "HIGH" && requestBucket(item) !== "waiting"
  );
  const waitingLong = open.filter(
    (item) =>
      requestBucket(item) === "waiting" &&
      now - new Date(item.updatedAt).getTime() > WAITING_DAYS * DAY_MS
  );
  const openJobs = jobs.filter(
    (job) => job.status === "OPEN" || job.status === "IN_PROGRESS"
  );

  const allClear =
    review.length === 0 && urgent.length === 0 && waitingLong.length === 0;

  return (
    <div className="today page-padded">
      <header className="page-header">
        <h1>Today</h1>
        <p>{organizationName}</p>
      </header>

      <section aria-labelledby="needs-you">
        <h2 id="needs-you" className="today-heading">Needs you now</h2>

        <div className="today-tiles">
          <button type="button" className="today-tile" onClick={() => onOpenRequests("review")}>
            <span>Needs review</span>
            <strong>{review.length}</strong>
          </button>
          <button type="button" className="today-tile" onClick={() => onOpenRequests("waiting")}>
            <span>Waiting on customer</span>
            <strong>{open.filter((item) => requestBucket(item) === "waiting").length}</strong>
          </button>
          <button type="button" className="today-tile" onClick={onOpenJobs}>
            <span>Open jobs</span>
            <strong>{openJobs.length}</strong>
          </button>
        </div>

        {allClear && (
          <p className="today-clear">You're all caught up. New emails will show up here.</p>
        )}

        {urgent.length > 0 && (
          <div className="today-list">
            <h3>Urgent</h3>
            {urgent.slice(0, 5).map((item) => (
              <button
                key={item.id}
                type="button"
                className="today-row"
                onClick={() => onOpenRequest(item.id)}
              >
                <span className="rq-urgent">Urgent</span>
                <strong>{requestTitle(item)}</strong>
                <span>{requestSender(item)}</span>
                <time>{shortTime(item.createdAt)}</time>
              </button>
            ))}
          </div>
        )}

        {waitingLong.length > 0 && (
          <div className="today-list">
            <h3>No answer in {WAITING_DAYS}+ days</h3>
            {waitingLong.slice(0, 5).map((item) => (
              <button
                key={item.id}
                type="button"
                className="today-row"
                onClick={() => onOpenRequest(item.id)}
              >
                <span className="rq-dot rq-dot-waiting" aria-hidden="true" />
                <strong>{requestTitle(item)}</strong>
                <span>{requestSender(item)}</span>
                <time>asked {shortTime(item.updatedAt)}</time>
              </button>
            ))}
          </div>
        )}
      </section>

      <section aria-labelledby="this-week" className="today-week">
        <h2 id="this-week" className="today-heading">Last 7 days</h2>

        {summary ? (
          <>
            <dl className="today-stats">
              <div>
                <dt>Requests organized</dt>
                <dd>{summary.requestsOrganized}</dd>
              </div>
              <div>
                <dt>Replies sent</dt>
                <dd>{summary.repliesSent}</dd>
              </div>
              <div>
                <dt>Typical time to reply</dt>
                <dd>
                  {summary.medianReplyMinutes === null
                    ? "–"
                    : formatMinutes(summary.medianReplyMinutes)}
                </dd>
              </div>
              <div>
                <dt>Jobs completed</dt>
                <dd>{summary.jobsCompleted}</dd>
              </div>
              <div className="today-saved">
                <dt>Time saved</dt>
                <dd>{formatMinutes(summary.minutesSaved)}</dd>
              </div>
            </dl>
            <p className="today-footnote">
              Time saved is an estimate of {summary.minutesPerRequest} minutes
              per request to read, sort and draft a reply by hand.
            </p>
          </>
        ) : (
          <p className="today-footnote">Loading...</p>
        )}
      </section>
    </div>
  );
}
