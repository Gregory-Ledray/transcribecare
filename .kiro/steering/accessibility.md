# Accessibility Requirements

TranscribeCare's primary users are elderly and visually impaired patients. Accessibility is not optional — it is a core product requirement.

## Color Contrast

- All text must meet WCAG 2.1 AA minimum contrast ratio of 4.5:1 against its background
- Large text (18px+ bold or 24px+ regular) must meet 3:1 minimum
- Interactive elements (buttons, links) must have 3:1 contrast against adjacent colors
- The app uses a high-contrast color scheme by default, not as an opt-in

## Text and Typography

- Base font size: 16px minimum for body text
- Large Text Mode increases transcription text to 28px+ for active content
- Never use font sizes below 14px for any user-visible content
- Line height should be at least 1.5x the font size for readability
- Avoid all-caps for body text (acceptable for short labels like "TODAY")

## Touch Targets

- Minimum touch target size: 48x48dp (Android), 44x44pt (iOS), 48x48px (Web)
- Adequate spacing between interactive elements to prevent mis-taps
- Bottom navigation items must be easily reachable with one hand

## Screen Readers and Semantics

- All icons must have meaningful `contentDescription` (Android), accessibility labels (iOS), or `aria-label`/alt text (Web)
- Navigation tabs must convey their selected state to assistive technology
- Recording state changes must be announced (live regions on web, accessibility announcements on native)
- Form inputs must have associated labels

## Motion and Animation

- Respect `prefers-reduced-motion` on web
- Keep animations subtle and functional (state transitions, not decorative)
- Never rely solely on animation to convey information

## Platform-Specific Guidelines

### Web
- Use semantic HTML elements (`<main>`, `<nav>`, `<header>`, `<button>`)
- Ensure full keyboard navigability
- Use ARIA attributes only when native semantics are insufficient
- Focus indicators must be visible

### Android
- Use Material 3 components which include built-in accessibility support
- Set `contentDescription` on all `Icon` composables
- Use `semantics` modifiers for custom components
- Support TalkBack navigation

### iOS
- Use standard SwiftUI components which include VoiceOver support
- Add `.accessibilityLabel()` to custom views and icons
- Use `.accessibilityHint()` for non-obvious interactions
- Support Dynamic Type for system font scaling
