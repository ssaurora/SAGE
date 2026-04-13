# AGENTS.md

## Purpose

This repository, `SAGE`, is the **real governed analysis execution system**.

It is the source of truth for:

- workflow semantics
- task-state authority
- waiting/resume behavior
- manifest freezing
- orchestration
- audit and traceability
- execution/runtime facts
- contract and catalog governance

Codex may also work on the related frontend repository, `sage-web`.
When doing cross-repo work, this repository must remain the semantic authority.

---

## Repository Role

`SAGE` owns:

- backend workflow state
- task transitions
- orchestration and dispatch
- waiting / resume semantics
- manifest and frozen contract meaning
- result, artifact, and run facts
- audit truth
- catalog and contract governance meaning
- backend-facing DTOs and read models
- stable task APIs
- real runtime evidence

`SAGE` does **not** own:

- final productized UX
- user-facing visual hierarchy
- frontend page composition
- presentation-level status copy
- frontend interaction shell details

Those belong primarily to `sage-web`.

---

## Core Working Principle

This repository must preserve a strict layered ownership model.

The backend is not a passive data dump for frontend invention.
The backend remains the workflow authority.

Frontend work in `sage-web` may motivate:

- better read models
- cleaner DTOs
- better summary projections
- more stable governance-facing APIs

But frontend needs must not casually rewrite backend semantics.

---

## Semantic Authority Rules

### 1. Backend owns workflow meaning

Do not allow workflow semantics to drift into the frontend.

`SAGE` remains the authority for:

- when a task enters waiting
- what `WAITING_USER` means
- what `/resume` means
- what is frozen vs mutable
- what constitutes success/failure/cancel
- what promotion, artifact, audit, and runtime evidence mean

### 2. Waiting/resume is a real governed path

Do not weaken or bypass the real repair/resume semantics.

The waiting path must remain governed:

- incomplete inputs produce structured waiting
- `/resume` re-enters formal validation/dispatch
- waiting context and repair semantics remain queryable and explicit

Do not convert this into a cosmetic frontend-only interaction.

### 3. Catalog and contract remain governance concepts

Catalog and contract are not decorative metadata.
They are governance boundaries.

Do not treat them as incidental fields added only for frontend display.
Preserve their stable meaning in:

- task detail
- manifest
- result
- audit
- contract/catalog projections

### 4. Task is the governance atom

This repository remains **task-first**.

Task is the unit of:

- workflow authority
- waiting / resume
- validation
- execution
- manifest freeze
- runs
- artifacts
- audit
- result traceability

Do not reframe core workflow authority around scene-level UI convenience.

### 5. Scene-facing read models are encouraged

Although workflow authority remains task-first, this repository should expose stable **scene-facing projections** for product consumption.

Acceptable additions include:

- scene summary projection
- scene detail projection
- session projection
- scene input discovery projection
- scene results projection

These are read models and coordination surfaces.
They do **not** replace task authority.

---

## Cross-Repo Alignment Rules

### 1. `sage-web` is scene-first product shell

When aligning with `sage-web`, assume:

- frontend is **scene-first**
- backend is **task-first**
- projections bridge the two

Do not force the frontend to reconstruct scene semantics from scattered task APIs.
Do not let scene pages in `sage-web` bind long-term to raw `/tasks/{taskId}` detail DTOs as their primary product contract.

### 2. Prefer additive backend support over frontend invention

If the frontend needs stable summary or aggregation, prefer adding a proper backend read model rather than forcing `sage-web` to infer it from multiple raw task objects.

Preferred additions include:

- `GET /scenes`
- `GET /scenes/{sceneId}`
- `GET /scenes/{sceneId}/session`
- scene input summary
- scene results summary

### 3. Do not change semantics for cosmetic reasons

Do not rename or reshape core backend concepts merely because a temporary frontend draft prefers another wording.

Change backend semantics only when:

- the current model is genuinely unclear or unstable
- alignment would otherwise remain brittle long-term
- the change improves the system, not just the UI draft

### 4. Scene creation concerns are not create-scene UX concerns

The following are internal orchestration concerns, not scene-creation UX concerns:

- model selection
- capability selection
- asset discovery
- missing-data identification
- orchestration expansion
- governed task formation

Do not move those responsibilities into create-scene semantics.

---

## Backend Change Rules

### Allowed

Codex may change backend code when needed to:

- add or improve read models
- expose stable scene-facing projections
- reduce duplicated projection assembly
- improve DTO clarity
- reduce frontend-backend semantic drift
- support real product pages in a stable way

### Not allowed casually

Do not casually change:

- task-state meaning
- waiting/resume meaning
- manifest freeze semantics
- audit semantics
- artifact/result identity
- contract/catalog semantics
- task-first workflow ownership

These are system-level truths, not temporary frontend implementation details.

---

## API and DTO Discipline

### 1. Favor explicit read models

If a frontend page needs a stable shape, create or refine an explicit DTO/projection rather than expecting the frontend to reverse-engineer internal service fields.

### 2. Preserve semantic naming

When exposing DTOs or projections, prefer names and fields that clearly reflect real system meaning.

### 3. Avoid UI-only DTO pollution

Do not add arbitrary presentation-only fields unless they reflect a stable backend read concern.

Bad pattern:

- adding arbitrary color/status text fields only because a UI card wants them

Better pattern:

- expose stable state/fact fields
- expose recommended-action summaries when they are real read-model concerns
- let the frontend map them into presentation

### 4. Support scene-level product surfaces with summary projections

The frontend will need productized scene-first pages.
Where necessary, add summary/read models for those pages rather than forcing full-detail fetches plus frontend recomposition.

Recommended shared projection DTO families include:

- `SceneSummaryDTO`
- `SceneDetailDTO`
- `SessionProjectionDTO`
- `SceneInputProjectionDTO`
- `SceneResultSummaryDTO`

---

## Architecture Discipline

### 1. Keep workflow authority centralized

Do not let workflow meaning leak into:

- ad hoc controllers
- duplicated service-local condition trees
- frontend-driven assumptions

### 2. Keep read models explicit

If different product surfaces need different views, expose explicit read models.

### 3. Favor thinner orchestration boundaries

Continue the existing direction of:

- moving repeated projection assembly out of orchestration shells
- keeping query/read semantics stable
- reducing field-stacking duplication

### 4. Preserve traceability

Do not regress on:

- runs
- artifacts
- result bundles
- audit records
- runtime evidence
- waiting/resume trace

These are core differentiators of the system.

---

## Required Check Before Finalizing Any Change

Before finalizing a meaningful change in this repo, check:

1. Does this preserve backend workflow authority?
2. Does this preserve task-first governance?
3. Am I improving a stable read concern, or just reacting to a temporary UI idea?
4. Will this reduce semantic drift with `sage-web`?
5. Is this an additive read-model/DTO improvement or a risky semantic rewrite?
6. Will frontend still be able to present this as a scene-first product surface without inventing meaning?
7. Have I preserved traceability, waiting/resume rigor, and frozen-object semantics?

---

## Anti-Patterns To Avoid

Avoid all of the following:

- making backend semantics match temporary UI wording
- weakening waiting/resume rigor to simplify frontend implementation
- turning catalog/contract into cosmetic metadata
- exposing only scattered internals and expecting frontend to reconstruct stable scene views
- duplicating similar projections across services with slightly different meanings
- allowing frontend convenience to silently redefine core workflow meaning
- treating the backend as a passive store while frontend becomes the true workflow engine
- converting task authority into scene authority just because the product shell is scene-first

---

## Recommended Change Strategy

When a feature spans both `SAGE` and `sage-web`, prefer this order:

1. identify the real system meaning in `SAGE`
2. determine whether an explicit scene-facing read model is needed
3. implement or refine backend projection/DTO/API support
4. update frontend contracts/adapters/types in `sage-web`
5. let `sage-web` productize the surface

Do not invert this sequence by allowing frontend speculation to drive backend semantics.

---

## Summary

In `SAGE`, Codex should behave like a **workflow-and-read-model engineer**:

- protect backend authority
- preserve governed semantics
- keep task-first workflow ownership
- expose scene-facing projections where needed
- improve read models when necessary
- support product surfaces without becoming UI-driven
- reduce semantic drift across repos
- keep waiting/resume, traceability, catalog, and contract boundaries strong
