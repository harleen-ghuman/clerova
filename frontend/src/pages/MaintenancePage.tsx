import { useState } from "react";

import { updateMaintenanceRequest } from "../api/MaintenanceRequests";
import { errorMessage } from "../api/client";

import type { MaintenanceRequest } from "../types/MaintenanceRequest";

interface MaintenancePageProps {
  maintenanceRequests: MaintenanceRequest[];
  loading: boolean;
  error: string | null;
  selectedWorkItemId: string | null;
  onClearSelection: () => void;
  onRequestUpdated: (request: MaintenanceRequest) => void;
  onWorkItemClosed: () => Promise<void>;
}

export function MaintenancePage({
  maintenanceRequests,
  loading,
  error: loadError,
  selectedWorkItemId,
  onClearSelection,
  onRequestUpdated,
  onWorkItemClosed,
}: MaintenancePageProps) {
  const [updateError, setUpdateError] = useState<string | null>(null);

  // Unsaved note edits, keyed by request id. Falls back to the saved notes.
  const [notes, setNotes] = useState<Record<string, string>>({});

  const [updatingId, setUpdatingId] = useState<string | null>(null);

  const [maintenanceView, setMaintenanceView] = useState<
    "active" | "history"
  >("active");

  const [actionMessages, setActionMessages] = useState<
    Record<string, string>
  >({});

  const error = loadError;

  function notesFor(request: MaintenanceRequest) {
    return notes[request.id] ?? request.notes ?? "";
  }

  async function handleUpdate(
    request: MaintenanceRequest,
    status?: MaintenanceRequest["status"]
  ) {
    try {
      setUpdatingId(request.id);
      setUpdateError(null);

      const updated = await updateMaintenanceRequest(
        request.id,
        {
          status,
          notes: notesFor(request),
        }
      );

      onRequestUpdated(updated);

      setNotes((current) => ({
        ...current,
        [updated.id]: updated.notes ?? "",
      }));

      let message = "Maintenance request updated.";

      if (updated.status === "IN_PROGRESS") {
        message = "Work started successfully.";
      }

      if (updated.status === "COMPLETED") {
        message =
          "Maintenance request completed. The work item was removed from the active queue.";
      }

      if (updated.status === "CANCELLED") {
        message =
          "Maintenance request cancelled. The work item was removed from the active queue.";
      }

      setActionMessages((current) => ({
        ...current,
        [updated.id]: message,
      }));

      if (
        updated.status === "COMPLETED" ||
        updated.status === "CANCELLED"
      ) {
        await onWorkItemClosed();
      }
    } catch (err) {
      console.error(
        "Failed to update maintenance request:",
        err
      );

      setUpdateError(
        errorMessage(err, "Unable to update maintenance request.")
      );
    } finally {
      setUpdatingId(null);
    }
  }

  const activeMaintenanceRequests =
    maintenanceRequests.filter(
      (request) =>
        request.status === "OPEN" ||
        request.status === "IN_PROGRESS"
    );

  const maintenanceHistory =
    maintenanceRequests.filter(
      (request) =>
        request.status === "COMPLETED" ||
        request.status === "CANCELLED"
    );

  const visibleMaintenanceRequests =
    selectedWorkItemId
      ? maintenanceRequests.filter(
          (request) =>
            request.workItemId === selectedWorkItemId
        )
      : maintenanceView === "active"
        ? activeMaintenanceRequests
        : maintenanceHistory;


  return (
    <section className="dashboard-section">
    {!selectedWorkItemId && (
      <div className="maintenance-tabs">
        <button
          type="button"
          className={`maintenance-tab ${
            maintenanceView === "active" ? "active" : ""
          }`}
          onClick={() => setMaintenanceView("active")}
        >
          Active
          <span>{activeMaintenanceRequests.length}</span>
        </button>

        <button
          type="button"
          className={`maintenance-tab ${
            maintenanceView === "history" ? "active" : ""
          }`}
          onClick={() => setMaintenanceView("history")}
        >
          History
          <span>{maintenanceHistory.length}</span>
        </button>
      </div>
    )}
      {selectedWorkItemId && (
        <button
          type="button"
          className="maintenance-back-button"
          onClick={onClearSelection}
        >
          ← Back to all maintenance
        </button>
      )}

      {loading && (
        <p>Loading maintenance requests...</p>
      )}

      {error && (
        <p className="error-message">{error}</p>
      )}

      {updateError && (
        <p className="action-error">{updateError}</p>
      )}

      {!loading &&
        !error &&
        visibleMaintenanceRequests.length === 0 && (
          <div className="empty-view">
            <div className="review-empty-icon">✓</div>

                  <h2>
                    {maintenanceView === "active"
                      ? "No active maintenance requests"
                      : "No maintenance history"}
                  </h2>

            <p>
                   {maintenanceView === "active"
                     ? "New approved maintenance actions will appear here."
                     : "Completed and cancelled maintenance requests will appear here."}
            </p>
          </div>
        )}

      {!loading &&
        !error &&
        visibleMaintenanceRequests.length > 0 && (
          <div className="maintenance-list">
            {visibleMaintenanceRequests.map((request) => {
              const isUpdating =
                updatingId === request.id;

              const isFinished =
                request.status === "COMPLETED" ||
                request.status === "CANCELLED";

              return (
                <article
                  id={`maintenance-${request.workItemId}`}
                  className={`maintenance-card maintenance-${request.status
                    .toLowerCase()
                    .replaceAll("_", "-")} ${
                    selectedWorkItemId === request.workItemId
                      ? "maintenance-card-selected"
                      : ""
                  }`}
                  key={request.id}
                >
                  <div className="maintenance-card-header">
                    <div className="maintenance-card-title">
                      <h3>
                        {request.address ||
                          "Address not provided"}
                      </h3>

                      <p className="maintenance-description">
                        {request.description}
                      </p>
                    </div>

                    <div className="maintenance-card-badges">
                      <span
                        className={`priority-badge priority-${request.priority.toLowerCase()}`}
                      >
                        {request.priority}
                      </span>

                      <span
                        className={`status-badge status-${request.status
                          .toLowerCase()
                          .replaceAll("_", "-")}`}
                      >
                        {request.status.replaceAll("_", " ")}
                      </span>
                    </div>
                  </div>

                  {request.status === "IN_PROGRESS" && (
                    <div className="maintenance-work-area">
                      <label htmlFor={`notes-${request.id}`}>
                        Human Notes
                      </label>

                      <textarea
                        id={`notes-${request.id}`}
                        value={notesFor(request)}
                        onChange={(event) =>
                          setNotes((current) => ({
                            ...current,
                            [request.id]: event.target.value,
                          }))
                        }
                        placeholder="Vendor, access, repair progress, or other operational notes..."
                        rows={3}
                        disabled={isUpdating}
                      />

                      <div className="maintenance-work-actions">
                        <button
                          type="button"
                          className="secondary-action-button"
                          disabled={isUpdating}
                          onClick={() =>
                            handleUpdate(request)
                          }
                        >
                          {isUpdating
                            ? "Saving..."
                            : "Save Notes"}
                        </button>

                        <div className="maintenance-terminal-actions">
                          <button
                            type="button"
                            className="approve-button"
                            disabled={isUpdating}
                            onClick={() =>
                              handleUpdate(
                                request,
                                "COMPLETED"
                              )
                            }
                          >
                            Complete
                          </button>

                          <button
                            type="button"
                            className="reject-button"
                            disabled={isUpdating}
                            onClick={() =>
                              handleUpdate(
                                request,
                                "CANCELLED"
                              )
                            }
                          >
                            Cancel
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {isFinished && request.notes && (
                    <div className="maintenance-final-note">
                      <span className="details-label">
                        Final Notes
                      </span>

                      <p>{request.notes}</p>
                    </div>
                  )}

                  {actionMessages[request.id] && (
                    <div className="maintenance-success">
                      {actionMessages[request.id]}
                    </div>
                  )}

                  <div className="maintenance-card-footer">
                    <span>
                      Updated{" "}
                      {new Date(
                        request.updatedAt
                      ).toLocaleString()}
                    </span>

                    {request.status === "OPEN" && (
                      <div className="maintenance-open-actions">
                        <button
                          type="button"
                          className="primary-button"
                          disabled={isUpdating}
                          onClick={() =>
                            handleUpdate(
                              request,
                              "IN_PROGRESS"
                            )
                          }
                        >
                          {isUpdating
                            ? "Starting..."
                            : "Start Work"}
                        </button>

                        <button
                          type="button"
                          className="text-danger-button"
                          disabled={isUpdating}
                          onClick={() =>
                            handleUpdate(
                              request,
                              "CANCELLED"
                            )
                          }
                        >
                          Cancel
                        </button>
                      </div>
                    )}

                    {isFinished && (
                      <span className="ai-created-label">
                        {request.status === "COMPLETED"
                          ? "Completed"
                          : "Cancelled"}
                      </span>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        )}
    </section>
  );
}