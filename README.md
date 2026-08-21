<div align="center">

# FIT // LAB

**A streetwear fit-builder app — build outfits, get AI-scored cohesion, and generate your best fit.**

[![Deploy Backend](https://github.com/mmarshall6402-oss/fitcheck/actions/workflows/deploy.yml/badge.svg)](https://github.com/mmarshall6402-oss/fitcheck/actions/workflows/deploy.yml)
[![Deploy Frontend](https://github.com/mmarshall6402-oss/fitcheck/actions/workflows/deploy-frontend.yml/badge.svg)](https://github.com/mmarshall6402-oss/fitcheck/actions/workflows/deploy-frontend.yml)
[![Build deploy artifacts](https://github.com/mmarshall6402-oss/fitcheck/actions/workflows/build-artifacts.yml/badge.svg)](https://github.com/mmarshall6402-oss/fitcheck/actions/workflows/build-artifacts.yml)
[![License](https://img.shields.io/github/license/mmarshall6402-oss/fitcheck?color=black)](#)
[![Last commit](https://img.shields.io/github/last-commit/mmarshall6402-oss/fitcheck?color=black)](#)

</div>

---

## What is this

FIT // LAB lets you browse a clothing catalog, drop items into a shirt / bottom / shoes
outfit builder, and get a live 0-100 **cohesion score** with rule-based reasons for *why*
an outfit works (or doesn't). Pin a piece as the anchor and hit **Generate best fit** to
have the backend brute-force the best-scoring combination from your catalog.

## Stack

<div align="center">

![Java](https://skillicons.dev/icons?i=java) ![Spring](https://skillicons.dev/icons?i=spring) ![React](https://skillicons.dev/icons?i=react) ![TypeScript](https://skillicons.dev/icons?i=typescript) ![Vite](https://skillicons.dev/icons?i=vite) ![Tailwind](https://skillicons.dev/icons?i=tailwind) ![Python](https://skillicons.dev/icons?i=python) ![FastAPI](https://skillicons.dev/icons?i=fastapi) ![Postgres](https://skillicons.dev/icons?i=postgres) ![AWS](https://skillicons.dev/icons?i=aws) ![GitHub Actions](https://skillicons.dev/icons?i=githubactions)

</div>

| Layer | Tech |
|---|---|
| **Backend** | Spring Boot 3 (Java 21), Spring Security + JWT, JPA/H2 (Postgres-ready) |
| **Frontend** | React 19, TypeScript, Vite, Tailwind CSS v4 |
| **AI / matching** | Anthropic (image tagging, style extraction), Python/FastAPI ML service — CLIP embeddings (`sentence-transformers`), garment segmentation (`transformers`/PyTorch), scikit-learn (logistic regression fit-scoring, KMeans color clustering) |
| **CI/CD** | GitHub Actions (backend deploy, frontend deploy, artifact builds) |

## Architecture

```mermaid
flowchart LR
    subgraph Client
        UI["React + TypeScript UI<br/>(Vite, Tailwind)"]
    end

    subgraph Backend["Spring Boot API :8080"]
        REST["REST Controllers<br/>/items /recommend /outfit"]
        Match["Matcher / OutfitScoringService"]
        Tag["ImageTaggingService<br/>StyleExtractionService"]
        Store["ImageStorageService"]
    end

    subgraph AI["fASHION-AI service"]
        Embed["sentence-transformers<br/>embeddings"]
    end

    DB[(H2 / Postgres)]
    Claude["Anthropic API"]
    Files[("Uploaded item images")]

    UI -- "HTTP / JSON" --> REST
    REST --> Match
    Match --> DB
    REST --> Tag
    Tag -- "vision + tagging" --> Claude
    REST --> Store
    Store --> Files
    REST -. "similarity lookups" .-> Embed
```

## Deployment (AWS)

Every push to `main` triggers CI/CD via GitHub Actions, deploying straight to AWS —
no manual steps.

```mermaid
flowchart TD
    Dev["git push → main"] --> GHA{GitHub Actions}

    GHA -- "Deploy Backend" --> MB["mvn package<br/>(Java 21)"]
    MB --> EB["Elastic Beanstalk<br/>fitlab-backend"]
    EB --> EC2["EC2 instance(s)<br/>Spring Boot :8080"]

    GHA -- "Deploy Frontend<br/>(on frontend/** changes)" --> NB["npm ci && npm run build"]
    NB --> S3["S3 bucket<br/>fitlab-frontend-*"]
    S3 --> CF["CloudFront<br/>cache invalidation"]

    CF --> Users(["End users"])
    EC2 --> Users

    style GHA fill:#2088FF,color:#fff
    style EB fill:#FF9900,color:#000
    style EC2 fill:#FF9900,color:#000
    style S3 fill:#569A31,color:#fff
    style CF fill:#8C4FFF,color:#fff
```

| Component | Service | Trigger |
|---|---|---|
| Backend | AWS Elastic Beanstalk (EC2) | Every push to `main` |
| Frontend | S3 (static hosting) + CloudFront (CDN, invalidated on deploy) | Push to `main` touching `frontend/**` |
| Build artifacts | GitHub Actions artifact storage | Manual (`workflow_dispatch`) |

Workflow definitions live in [`.github/workflows/`](.github/workflows/): [`deploy.yml`](.github/workflows/deploy.yml) (backend), [`deploy-frontend.yml`](.github/workflows/deploy-frontend.yml) (frontend), [`build-artifacts.yml`](.github/workflows/build-artifacts.yml) (on-demand build).

## Repo layout

```
fitcheck/
├── backend/       Spring Boot REST API — catalog, matching, outfit scoring
├── frontend/      React + TypeScript UI
└── fASHION-AI/    Python ML service — CLIP embeddings, garment segmentation, fit-scoring model (FastAPI)
```

- `backend/` — see [`backend/README.md`](backend/README.md) for config, endpoints, and architecture notes.
- `frontend/` — see [`frontend/README.md`](frontend/README.md) for the dev server and UI walkthrough.

## Quick start

```bash
# backend — serves on :8080
cd backend && mvn spring-boot:run

# frontend — serves on :5173, proxies API calls to :8080
cd frontend && npm install && npm run dev
```

Open `http://localhost:5173`. The catalog starts empty — add items via the UI or
`POST /items` / `POST /items/import`.

## Testing

| Suite | Command | Last verified result |
|---|---|---|
| Backend (JUnit, Spring Boot Test) | `cd backend && mvn test` | ✅ **38 passed / 0 failed** across 10 test classes (integration tests for admin, attachments, and app bootstrap; unit tests for matching, item, match, outfit, outfit-scoring, and scoring-config services) |
| fASHION-AI (pytest, FastAPI `TestClient`) | `cd fASHION-AI && pip install -r requirements.txt -r requirements-dev.txt && pytest -q` | `tests/test_api.py`, `test_bank_logic.py`, `test_color.py` — not runnable in this sandboxed session (network policy blocks `huggingface.co`, and `main.py` downloads the CLIP + segmentation model weights at import time); run locally to get real numbers |
| Frontend | `cd frontend && npm run lint` | No test suite yet — `oxlint` only |

## How outfit scoring works

1. Add items to the catalog (name, category, color/vibe tags, optional photo).
2. Drop items into the **shirt / bottom / shoes** slots.
3. Once all three are filled, `GET /outfit/score` combines the three pairwise
   edges (shirt↔bottom, shirt↔shoes, bottom↔shoes) into one cohesion score.
4. Pin an anchor piece and hit **Generate best fit** — `GET /outfit/build`
   brute-forces the best-scoring combination around it.

---

<div align="center">

Built with Spring Boot, React, and a bit of Anthropic-assisted style sense.

</div>
