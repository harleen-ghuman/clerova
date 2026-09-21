import type { FormEvent } from "react";

import { StatCard } from "../components/StatCard";
import type { MaintenanceRequest } from "../types/MaintenanceRequest";

interface DashboardPageProps {
  totalWorkItems: number;
  newWorkItems: number;
  pendingActionCount: number;

  maintenanceRequests: MaintenanceRequest[];

  newRequestMessage: string;
  creatingRequest: boolean;
  createRequestError: string | null;
  createRequestSuccess: string | null;

  onNewRequestMessageChange: (value: string) => void;
  onCreateRequest: (
    event: FormEvent<HTMLFormElement>
  ) => void;

  onViewWorkItems: () => void;
  onViewMaintenance: () => void;
}

export function DashboardPage({
  totalWorkItems,
  newWorkItems,
  pendingActionCount,
  maintenanceRequests,
  newRequestMessage,
  creatingRequest,
  createRequestError,
  createRequestSuccess,
  onNewRequestMessageChange,
  onCreateRequest,
  onViewWorkItems,
  onViewMaintenance,
}: DashboardPageProps) {
  return (
    <>
      <section className="stats-grid">
        <StatCard
          label="Total Work Items"
          value={totalWorkItems}
          helperText="Incoming requests"
        />

        <StatCard
          label="New Requests"
          value={newWorkItems}
          helperText="Waiting for review"
        />

        <StatCard
          label="Pending Approval"
          value={pendingActionCount}
          helperText="Needs human review"
        />
      </section>

      <section className="dashboard-overview">
        <div className="overview-card">
          <span className="eyebrow">
            Work Queue
          </span>

          <strong>{newWorkItems}</strong>

          <p>
            {newWorkItems === 1
              ? "New request waiting for review."
              : "New requests waiting for review."}
          </p>

          <button
            type="button"
            onClick={onViewWorkItems}
          >
            Review Work Items
          </button>
        </div>

        <div className="overview-card">
          <span className="eyebrow">
            Maintenance
          </span>

          <strong>
            {maintenanceRequests.length}
          </strong>

          <p>
            Maintenance requests have been created from
            approved actions.
          </p>

          <button
            type="button"
            onClick={onViewMaintenance}
          >
            View Maintenance
          </button>
        </div>
      </section>

      <section className="dashboard-section new-request-section">
        <div className="section-heading">
          <div>
            <span className="eyebrow">
              AI Intake
            </span>

            <h2>Process New Request</h2>

            <p>
              Enter an incoming tenant or property message.
              Clerova will classify and prioritize it
              automatically.
            </p>
          </div>
        </div>

        <form
          className="new-request-form"
          onSubmit={onCreateRequest}
        >
          <label htmlFor="new-request-message">
            Incoming message
          </label>

          <textarea
            id="new-request-message"
            value={newRequestMessage}
            onChange={(event) =>
              onNewRequestMessageChange(
                event.target.value
              )
            }
            placeholder="Example: Water is leaking through the kitchen ceiling at 850 Maple Avenue and it's getting worse."
            rows={5}
            disabled={creatingRequest}
          />

          <div className="new-request-actions">
            <span className="request-helper">
              AI will extract the address, category,
              priority and summary.
            </span>

            <button
              className="primary-button"
              type="submit"
              disabled={
                creatingRequest ||
                newRequestMessage.trim().length === 0
              }
            >
              {creatingRequest
                ? "Processing with AI..."
                : "Process Request"}
            </button>
          </div>

          {createRequestError && (
            <p className="action-error">
              {createRequestError}
            </p>
          )}

          {createRequestSuccess && (
            <p className="request-success">
              {createRequestSuccess}
            </p>
          )}
        </form>
      </section>
    </>
  );
}