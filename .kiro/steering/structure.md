# Project Structure

```
/
├── index.html          # HTML entry point (Vite SPA)
├── metadata.json       # App metadata (name, description, permissions)
├── package.json        # Dependencies and scripts
├── tsconfig.json       # TypeScript configuration
├── vite.config.ts      # Vite build configuration
├── .env.example        # Environment variable template
├── src/
│   ├── main.tsx        # React app bootstrap (createRoot)
│   ├── App.tsx         # Main application component (all UI lives here)
│   └── index.css       # Global styles, Tailwind imports, theme tokens
└── .kiro/
    └── steering/       # AI assistant steering rules
```

## Architecture Notes

- Single-page application with all components co-located in `App.tsx`
- No routing library — tab-based navigation managed via React state
- No component library — custom components using Tailwind utility classes
- Design tokens (colors, spacing, radii) defined as Tailwind theme variables in `index.css`
- Material Design 3 inspired color naming (`primary`, `on-primary`, `surface-container`, etc.)

## Conventions

- Components are function components defined in the same file as the App
- State management is local (useState/useRef) — no external state library
- Types and interfaces are defined inline at the top of `App.tsx`
- Font: Public Sans (imported via Google Fonts in CSS)
