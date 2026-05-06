# Tech Stack

## Core

- **Language**: TypeScript (strict, noEmit)
- **Framework**: React 19 with JSX (`react-jsx`)
- **Build Tool**: Vite 6 (ESM, bundler module resolution)
- **Styling**: Tailwind CSS 4 via `@tailwindcss/vite` plugin
- **Animations**: Motion (Framer Motion successor)
- **Icons**: Lucide React

## Additional Libraries

- `@google/genai` — Gemini AI SDK (API key injected via env)
- `express` — backend server (available but not actively used in current SPA)
- `dotenv` — environment variable loading

## Environment Variables

- `GEMINI_API_KEY` — required for AI features, exposed to client via Vite `define`
- `APP_URL` — hosting URL (injected at runtime in AI Studio)

## Common Commands

| Command | Purpose |
|---------|---------|
| `npm install` | Install dependencies |
| `npm run dev` | Start dev server on port 3000 |
| `npm run build` | Production build (outputs to `dist/`) |
| `npm run preview` | Preview production build |
| `npm run clean` | Remove `dist/` directory |
| `npm run lint` | Type-check with `tsc --noEmit` |

## Configuration

- `tsconfig.json` — ES2022 target, bundler resolution, path alias `@/*` → project root
- `vite.config.ts` — React plugin, Tailwind plugin, `@` path alias, optional HMR disable via `DISABLE_HMR` env var
- Tailwind theme defined in `src/index.css` using `@theme` directive (v4 syntax)
