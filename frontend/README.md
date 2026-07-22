# fitlab-frontend

React + TypeScript UI for FIT//LAB, the streetwear fit-builder app. Talks to
`fitlab-backend` (see `../backend`).

## Run

```
npm install
npm run dev
```

Serves on `http://localhost:5173`. The dev server proxies `/items`,
`/recommend`, `/outfit`, and `/uploads` to the backend at `http://localhost:8080`
(override with `VITE_BACKEND_URL`), so no CORS config is needed in dev.

## What's here

- **Catalog** (left panel): browse items by category, add new ones (name,
  category, color/vibe tags, optional photo upload), delete existing ones.
  Clicking a card drops it into the matching outfit slot.
- **Outfit builder** (right panel): three slots (shirt/bottom/shoes). Once all
  three are filled, a live cohesion score and rule-based reasons appear
  (`GET /outfit/score`). Pin one piece as the "anchor" and hit **Generate best
  fit** to have the backend brute-force the best-scoring combination around it
  (`GET /outfit/build`). Empty slots show top picks ranked against the current
  anchor (`GET /recommend`).

## Stack

Vite, React 19, TypeScript, Tailwind CSS v4. No component library or state
management beyond `useState`/`useEffect` - the app is small enough that they'd
be pure overhead.
