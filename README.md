# Clerova

**AI-assisted operations workflow for property management teams**

Clerova is a full-stack application that turns incoming resident and property messages into structured operational work.

Instead of requiring staff to manually read, classify, prioritize, and route every request, Clerova uses AI to extract key information, recommend the next action, and place consequential actions behind human approval.

The current prototype focuses on property management operations and includes an end-to-end maintenance workflow.

## What Clerova Does

A property manager can submit an incoming message such as:

> Water is leaking rapidly from under the kitchen sink at 742 Evergreen Terrace. The cabinet and floor are getting wet.

Clerova can:

1. Extract the property address
2. Classify the request
3. Assign a priority
4. Generate an operational summary
5. Recommend the next action
6. Require human approval before execution
7. Create and track a maintenance request
8. Record operational notes and progress
9. Synchronize completion with the original work item

## Workflow

```text
Incoming Request
       |
       v
AI Classification
       |
       v
Structured Work Item
       |
       v
AI Proposed Action
       |
       v
Human Approval
       |
       +--------------------------+
       |                          |
       v                          v
Maintenance Request        Other Workflow
       |
       v
OPEN -> IN_PROGRESS -> COMPLETED
       |
       v
Parent Work Item COMPLETED
```

## Key Features

- AI-powered request classification and summarization
- Address, category, and priority extraction
- AI-generated operational recommendations
- Human-in-the-loop approval workflow
- Maintenance request creation from approved actions
- Maintenance lifecycle and operational notes
- Work-item and maintenance-status synchronization
- Rejection and proposal regeneration flow
- Dashboard and approval queue
- PostgreSQL persistence
- REST API backend
- Environment-based deployment configuration
- Health endpoint and centralized API error handling

## Human-in-the-Loop AI

Clerova intentionally separates AI recommendations from execution.

```text
AI recommendation
        |
        v
Human review
        |
        v
Approved execution
```

For example, the model may recommend creating a high-priority maintenance request, but the maintenance request is not created until a user approves the proposed action.

This provides a foundation for expanding AI-assisted automation while retaining human oversight over operational decisions.

## Architecture

```text
+-----------------------------+
|       React Frontend        |
|      TypeScript + Vite      |
+--------------+--------------+
               |
               | REST API
               v
+-----------------------------+
|     Spring Boot Backend     |
|           Java 21           |
+-----------------------------+
| Work Item Service           |
| AI Service                  |
| Approval Workflow           |
| Action Execution Service    |
| Maintenance Service         |
+----------+---------+--------+
           |         |
           v         v
    +------------+  +------------+
    | PostgreSQL |  | OpenAI API |
    +------------+  +------------+
```

## Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- PostgreSQL
- Maven
- OpenAI Java SDK

### Frontend

- React
- TypeScript
- Vite
- CSS

### Development & Testing

- Docker Compose
- PostgreSQL 17
- H2 in-memory database for backend tests
- JUnit / Spring Boot Test
- Git / GitHub

## Core Domain Model

### Work Item

Represents the structured version of an incoming resident or property request.

A work item contains:

- Original message
- AI-generated summary
- Address
- Category
- Priority
- Workflow status

### Proposed Action

Represents Clerova's recommended next step.

Current action types include:

- `CREATE_MAINTENANCE_REQUEST`
- `RESPOND_TO_CUSTOMER`
- `REQUEST_MORE_INFORMATION`
- `ESCALATE_TO_HUMAN`
- `NO_ACTION`

Proposed actions begin in `PENDING_APPROVAL` and can be explicitly approved or rejected.

### Maintenance Request

An approved maintenance recommendation can create a maintenance request linked to both the originating work item and proposed action.

Maintenance requests progress through:

```text
OPEN -> IN_PROGRESS -> COMPLETED
```

or can be cancelled.

Completing or cancelling a maintenance request also updates the parent work item's lifecycle.

## Example End-to-End Flow

### Incoming message

```text
Water is leaking rapidly from under the kitchen sink at
742 Evergreen Terrace. The cabinet and floor are getting wet.
```

### AI extraction

```text
Address: 742 Evergreen Terrace
Category: MAINTENANCE
Priority: HIGH
Status: NEW
```

### AI recommendation

```text
CREATE_MAINTENANCE_REQUEST
```

### After human approval

```text
Proposed Action: APPROVED
Work Item: IN_PROGRESS
Maintenance Request: OPEN
```

### After maintenance completion

```text
Maintenance Request: COMPLETED
Work Item: COMPLETED
```

## Screenshots

### Operations Dashboard

Monitor incoming requests, approval queues, and maintenance operations from a single dashboard.

![Clerova Operations Dashboard](docs/screenshots/dashboard.png)

### AI Recommendation & Human Review

Clerova analyzes incoming requests, recommends the next operational action, explains its reasoning, and requires human approval before execution.

![Clerova AI Review Workflow](docs/screenshots/ai-review.png)

### Maintenance Workflow

Approved maintenance actions become operational maintenance requests that can be tracked from open through completion.

![Clerova Maintenance Workflow](docs/screenshots/maintenance.png)

## Running Locally

### Prerequisites

- Java 21
- Node.js and npm
- Docker
- OpenAI API key

### 1. Clone the repository

```bash
git clone git@github.com:harleen-ghuman/clerova.git
cd clerova
```

### 2. Start PostgreSQL

```bash
docker compose up -d
```

The local development database is configured by `compose.yaml`.

### 3. Configure OpenAI

Set your API key:

```bash
export OPENAI_API_KEY="your-api-key"
```

Do not commit API keys or other secrets to the repository.

### 4. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend:

```text
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/api/health
```

### 5. Start the frontend

In another terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:5173
```

## Environment Configuration

### Backend

```text
OPENAI_API_KEY
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
CORS_ALLOWED_ORIGIN
```

### Frontend

```text
VITE_API_BASE_URL
```

Local development defaults are provided where appropriate.

## Testing

Backend:

```bash
cd backend
./mvnw test
```

Frontend production build:

```bash
cd frontend
npm run build
```

## API Overview

Core API routes include:

```text
/api/health
/api/work-items
/api/work-items/{id}
/api/work-items/{id}/proposed-actions
/api/work-items/proposed-actions/{id}/approve
/api/work-items/proposed-actions/{id}/reject
/api/maintenance-requests
/api/maintenance-requests/{id}
```

## Project Status

Clerova is currently a working full-stack prototype demonstrating an AI-assisted operational workflow with human oversight.

### Implemented

- AI request extraction
- Work-item persistence
- AI action recommendations
- Human approval and rejection
- Maintenance request execution
- Maintenance lifecycle management
- Dashboard and operational UI
- PostgreSQL persistence
- Backend tests
- Environment-based deployment configuration

### Planned

- Authentication and authorization
- Multi-tenant organization support
- Resident communication integrations
- Vendor assignment and dispatch
- Audit/event history
- Additional operational workflows
- Production database migrations
- Expanded automated test coverage

## Live Demo

**Coming soon.**

## Why I Built This

Clerova explores how LLMs can be incorporated into operational software rather than used only as standalone chat interfaces.

The project combines AI reasoning with traditional backend workflows, persistent state, deterministic business logic, human approval, and operational execution.

I designed and implemented Clerova as a full-stack project spanning AI integration, backend architecture, persistence, REST API design, workflow orchestration, and frontend product development.

---

Built by **Harleen Ghuman**