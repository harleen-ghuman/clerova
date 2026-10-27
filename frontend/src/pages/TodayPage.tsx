import { useEffect, useState } from "react";

import { getWeekSummary, type WeekSummary } from "../api/summary";
import { GetStarted } from "../components/GetStarted";
import type { MaintenanceRequest } from "../types/MaintenanceRequest";
import type { WorkItem } from "../types/WorkItem";
import {
  hasNewReply,
  requestBucket,
  requestSender,
  requestTitle,
  shortTime,
  type RequestBucket,
} from "../utils/requestStatus";

const DAY_MS = 24 * 60 * 60 * 1000;
// Customers who haven't answered in this many days are worth a nudge
const WAITING_DAYS = 3;

const HIDE_KEY = "clerova.getStartedHidden";

function readHidden(): boolean {
  try {
    return window.localStorage.getItem(HIDE_KEY) === "1";
  } catch {
    return false;
  }
}

interface TodayPageProps {
  organizationName: string;
  /** Just finished checkout; greet them. */
  welcome: boolean;
  forwardingAddress: string | null;
  /** Any request ever, open or closed. */
  hasAnyRequest: boolean;
  onOpenEmailSetup: () => void;
  /** Open requests; closed ones are ignored here. */
  requests: WorkItem[];
  jobs: MaintenanceRequest[];
  refreshKey: number;
  onOpenRequests: (filter: RequestBucket) => void;
  onOpenRequest: (workItemId: string) => void;
  onOpenJobs: () => void;
}

// The time-saved ring fills toward one working week
const WORK_WEEK_MINUTES = 40 * 60;

/** Time saved as a ring showing its share of a 40-hour work week. */
function TimeSavedRing({ minutes }: { minutes: number }) {
  const size = 112;
  const stroke = 10;
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  const share = Math.min(minutes / WORK_WEEK_MINUTES, 1);
  const percent = Math.round((minutes / WORK_WEEK_MINUTES) * 100);
  const description =
    percent >= 100
      ? "More than a full 40\u2011hour work week"
      : `About ${Math.max(percent, minutes > 0 ? 1 : 0)}% of a 40\u2011hour work week`;

  return (
    <div className="today-ring">
      <svg
        width={size}
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        role="img"
        aria-label={`${formatMinutes(minutes)} saved. ${description}.`}
      >
        <title>{`${formatMinutes(minutes)} saved · ${description}`}</title>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--border)"
          strokeWidth={stroke}
        />
        {share > 0 && (
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke="var(--success)"
            strokeWidth={stroke}
            strokeLinecap="round"
            strokeDasharray={`${circumference * share} ${circumference}`}
            transform={`rotate(-90 ${size / 2} ${size / 2})`}
          />
        )}
        <text
          x="50%"
          y="50%"
          dominantBaseline="central"
          textAnchor="middle"
          className="today-ring-value"
        >
          {formatMinutes(minutes)}
        </text>
      </svg>
      <div>
        <strong>Time saved</strong>
        <p>{description}</p>
      </div>
    </div>
  );
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
  welcome,
  forwardingAddress,
  hasAnyRequest,
  onOpenEmailSetup,
  requests,
  jobs,
  refreshKey,
  onOpenRequests,
  onOpenRequest,
  onOpenJobs,
}: TodayPageProps) {
  const [summary, setSummary] = useState<WeekSummary | null>(null);
  const [summaryFailed, setSummaryFailed] = useState(false);
  const [checklistHidden, setChecklistHidden] = useState(readHidden);
  // Fixed per refresh so "waiting 3+ days" doesn't change between renders
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let cancelled = false;

    getWeekSummary()
      .then((result) => {
        if (!cancelled) {
          setSummary(result);
          setSummaryFailed(false);
          setNow(Date.now());
        }
      })
      .catch((err) => {
        console.error("Failed to load the summary:", err);

        if (!cancelled) {
          setSummaryFailed(true);
        }
      });

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
  const newReplies = open
    .filter(hasNewReply)
    .sort((a, b) => (b.unreadReplyAt ?? "").localeCompare(a.unreadReplyAt ?? ""));
  const openJobs = jobs.filter(
    (job) => job.status === "OPEN" || job.status === "IN_PROGRESS"
  );

  const allClear =
    review.length === 0 &&
    urgent.length === 0 &&
    waitingLong.length === 0 &&
    newReplies.length === 0;

  return (
    <div className="today page-padded">
      <header className="page-header">
        <h1>Today</h1>
        <p>{organizationName}</p>
      </header>

      {(() => {
        // All-time totals, so established workspaces never see it again
        const hasRequest = hasAnyRequest || (summary?.allTimeRequests ?? 0) > 0;
        const hasReply = (summary?.allTimeApprovals ?? 0) > 0;
        // Without stats, an account with requests is treated as set up
        const finished = hasRequest && (hasReply || summaryFailed);

        return (
          // Without the summary, the request lists decide
          (summary !== null || summaryFailed) &&
          !checklistHidden &&
          !finished && (
            <GetStarted
              welcome={welcome}
              forwardingAddress={forwardingAddress}
              hasRequest={hasRequest}
              hasReply={hasReply}
              onOpenEmailSetup={onOpenEmailSetup}
              onOpenRequests={() => onOpenRequests("review")}
              onHide={() => {
                setChecklistHidden(true);
                try {
                  window.localStorage.setItem(HIDE_KEY, "1");
                } catch {
                  // Hidden for this visit only
                }
              }}
            />
          )
        );
      })()}

      {(hasAnyRequest || (summary?.allTimeRequests ?? 0) > 0) && (
      <>
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

        {newReplies.length > 0 && (
          <div className="today-list">
            <h3>New replies during a job</h3>
            {newReplies.slice(0, 5).map((item) => (
              <button
                key={item.id}
                type="button"
                className="today-row"
                onClick={() => onOpenRequest(item.id)}
              >
                <span className="rq-new-reply">New reply</span>
                <strong>{requestTitle(item)}</strong>
                <span>{requestSender(item)}</span>
                <time>{shortTime(item.unreadReplyAt ?? item.updatedAt)}</time>
              </button>
            ))}
          </div>
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
            <div className="today-week-grid">
              <TimeSavedRing minutes={summary.minutesSaved} />
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
              </dl>
            </div>
            <p className="today-footnote">
              Time saved is an estimate of {summary.minutesPerRequest} minutes
              per request to read, sort and draft a reply by hand.
            </p>
          </>
        ) : (
          <p className="today-footnote">
            {summaryFailed ? "Couldn't load your stats. They'll refresh shortly." : "Loading..."}
          </p>
        )}
      </section>
      </>
      )}
    </div>
  );
}
