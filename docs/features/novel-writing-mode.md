# Novel Writing mode — feature reference

**Status as of this document: feature-complete checkpoint.** This is the
detailed reference for the Novel (Plan / Write / Chat / Review / Codex /
Snippets) side of the app, written at the point the user considers it done
and work is about to shift heavily to the Roleplay side. See
`docs/CHECKPOINTS.md` for the commit this snapshot corresponds to and how
to revert to it if roleplay work needs to be rolled back independently.

This document assumes familiarity with `docs/ARCHITECTURE.md` (module
layout, data model, AI provider layer) and goes one level deeper on the
Write screen specifically, since that's where this session's work landed.

## Screen map

Under `feature/novel/`, one subpackage per rail:

| Package | Screen | Purpose |
|---|---|---|
| `plan/` | `PlanScreen` | Outline view — acts/chapters/scenes as a tree, scene creation. |
| `write/` | `WriteScreen` | The manuscript editor for one scene. See below. |
| `chat/` | `WorkshopChatScreen` | AI chat *about* the novel (not roleplay) — brainstorming, threaded, tied to a prompt/scene. |
| `review/` | `ReviewScreen` | Read-through / proofing view. |
| `manuscript/` | `ManuscriptRailScreen` | Full-manuscript navigation rail. |
| `codex/` | `CodexRailScreen`, `CodexEntryDetailScreen` | Worldbuilding entries + lorebook fields (see `ARCHITECTURE.md` "Codex"). |
| `snippets/` | `SnippetsRailScreen` | Reusable text snippets, and (via the same table, different scope) global Notes. |

## Write screen (`write/WriteScreen.kt`, `WriteViewModel.kt`)

The manuscript editor for a single `SceneEntity`. Composed of, top to
bottom:

1. **Scene header row**: title, word count, and three action buttons —
   `Aa` (format toolbar expand/collapse), `Prompting` (dropdown: Extend /
   Summarize / Condense / Replace / Retry — see "Prompting menu" below),
   `Media` (dropdown: Mic / Audio / Picture).
2. **Format toolbar** (`editor/FormatToolbar.kt`) — collapsed to nothing
   but the header's `Aa` button by default; expanding shows Undo/Redo,
   Bold/Italic/Underline toggle capsules, then a second row (Font family,
   Font size, Strikethrough, Superscript, Subscript, Color, Highlight).
   Purely a view over the same `WriteViewModel` selection-based formatting
   functions the long-press menu uses (see "Rich text" below) — no
   separate state.
3. **`DocumentEditor`** (`editor/DocumentEditor.kt`) — a `LazyColumn` over
   the scene's `Block` list (see `ARCHITECTURE.md` "Document format"),
   dispatching to a renderer per block type: `BlockEditorField` for
   `Paragraph`, `MediaBlockView`/`MediaStackBlockView` for images,
   `SceneBeatBlockView` for AI scene-beat prompts.
4. Optional **continuation box** at the bottom (`/` AI · `\` manual, same
   pattern as the global prompt overlay).

### Rich text / formatting

`core/text/DocumentModel.kt`'s `Span` carries the full styling surface:
`marks: Set<Mark>` (`Bold, Italic, Underline, Strikethrough, Code,
Superscript, Subscript`), `colorHex`, `highlightHex`, `codexEntryId`
(codex mention linking), and — added this session —
**`fontFamilyKey`** and **`fontSizeSp`**.

Font family is deliberately scoped to Compose's built-in families
(`core/text/FontOption.kt`: Default, Serif, Sans Serif, Monospace,
Cursive) rather than named fonts like Arial — Android doesn't ship those
without bundling font files, and the app bundles none, so promising
"Arial" would silently fall back to something else. `FontSizeOptions` is a
fixed list (10–32sp) offered via `FontSizePickerDialog`.

`core/text/SpanEdit.kt` is the pure-function algebra all formatting goes
through: `toggleMark`, `applyColor`, `applyHighlight`, `applyFontFamily`,
`applyFontSize` all take `(spans, start, end, value)` and return a new
span list; `marksInRange`/`fontFamilyKeyInRange`/`fontSizeSpInRange`
compute the *shared* value across a selection (null/mixed if not uniform)
for driving active-state UI (✓ marks, current font/size labels).
`toAnnotatedString` renders all of this — including Underline,
Strikethrough, Superscript/Subscript (via `BaselineShift`), and
`highlightHex` (via `SpanStyle.background`) — **all of which existed in
the `Mark` enum and `Span.highlightHex` before this session but were never
actually rendered** until this pass added them to `toAnnotatedString`.

Two entry points apply formatting, both funneling into the same
`WriteViewModel` functions (`toggleMarkOnSelection`, `applyColorOnSelection`,
`applyHighlightOnSelection`, `applyFontFamilyOnSelection`,
`applyFontSizeOnSelection`), so the toolbar and the long-press menu can
never drift out of sync:

- **The Format toolbar** (above).
- **Long-press → Format…** (`core/ui/components/EditTextPopup.kt`) — a
  two-page dropdown menu. The Main page is the familiar Copy/Cut/Paste/
  Select all/Delete/Dictate/Speak list plus a single "Format…" entry
  (rather than inlining Bold/Italic/Color as flat items, which is how it
  worked before this session); tapping it opens the Format page (Bold,
  Italic, Underline, Strikethrough, Superscript, Subscript, Text color…,
  Highlight…, Font…, Size…, each showing a ✓ when active in the
  selection). **Highlighting text (a non-empty drag-selection) opens
  straight into the Format page**, skipping Main — the popup's
  `LaunchedEffect(expanded)` picks the start page from
  `config.hasSelection && config.showFormatting` at open time.

#### The `showMenu()` reopen-loop bug (fixed, worth remembering)

Early in this session's Format work, toggling a mark from the popup made
it appear to "get stuck" — closing and instantly reopening, with the
keyboard flickering. Root cause, found by extracting frames from a screen
recording: toggling a mark rewrites `paragraph.spans` with the text
unchanged; `BlockEditorField`'s resync `LaunchedEffect` (which exists to
pull in undo/AI-accept changes without clobbering the caret mid-typing)
reassigns a brand-new `TextFieldValue` in response; Compose interprets
that reassignment as reason to ask the system to redisplay the selection
toolbar — so `TextToolbar.showMenu()` (which `BlockEditorField`
intercepts to show our custom popup instead of the system one) fires
again immediately, popping our just-dismissed popup back open. Dismissing
the popup after every action alone didn't fix it — the *reopen* is
self-triggered by the state change, not by the menu staying open.

Two fixes landed on top of each other here, from two different sessions
working the same branch concurrently — worth knowing both existed:

1. The resync effect (`BlockEditorField.kt`) now compares the *actual
   computed* `AnnotatedString` against what's already displayed and skips
   reassigning `value` entirely if nothing changed (a typing round-trip
   echoing back content already on screen was reassigning a fresh object
   for no reason, independent of the popup bug).
2. **The mechanism that actually stops the reopen** is
   `editor/EditMenuGate.kt` (with `EditMenuGateTest.kt`) — not a timer.
   An earlier pass tried a 400ms `suppressShowMenuUntilMillis` window
   after dismiss, but `showMenu()` can fire again well past 400ms later
   (e.g. canceling the Text color dialog), so a fixed window either
   reopens too late-blocked or too early-unblocked depending on how long
   the user takes. `EditMenuGate` instead tracks *identity*: once the
   popup is dismissed for a given `TextRange`, further `showMenu()` calls
   for that *same* selection are ignored outright, with no time limit.
   The gate only re-arms when the selection genuinely changes — the caret
   collapses (tap away) or the range moves (a new long-press/drag-select)
   — which is the actual signal that the user is doing something new
   rather than the system re-asking to show a toolbar for a selection
   that already had its menu dismissed.

Both fixes are still in place and complementary: the resync-skip avoids
wasted `TextFieldValue` churn generally, and `EditMenuGate` is what
actually gates `showMenu()`. If this class of bug resurfaces, extend
`EditMenuGate` rather than reaching for another timer.

### Prompting menu (`Prompting` button in the scene header)

Replaced a lone "Summarize" button. Opens a dropdown of the writing-AI
actions, all pre-existing `WriteViewModel` functions just newly exposed
in one place instead of being reachable only via the long-press menu or
not at all from the header:

- **Extend** / **Condense** / **Replace** → `startSelectionAi(commandId,
  label)` — operates on the current text selection if any, else the
  whole scene; `Condense` reuses the existing `"shorten"` command id
  under a clearer label (no new AI command was added).
- **Summarize** → `summarizeScene()` (whole-scene summary; unchanged
  behavior, just relocated).
- **Retry** → `retryAiGeneration()` (re-runs the last AI overlay
  generation); enabled only when `state.aiOverlay != null`.

### Global Prompt Overlay (`feature/prompt/GlobalPromptOverlay.kt`)

The floating `/` (AI) · `\` (manual) prompt box available from
anywhere in Novel mode (Write, Chat, etc. — gated by
`PromptSurface.usesGlobalOverlay`), independent from the Write screen's
own inline scene-beat/continuation boxes. This session's changes, in
order:

1. Redesigned header: "Hide" → "−" (condense), pinned top-right.
2. Added a **Man/Gen** toggle (`InkModeCapsule`, compact) so
   Manual/Generative mode can be picked before typing anything, not only
   by typing `/` or `\`. `GlobalPromptViewModel.setKind()` opens the
   window fresh (mirroring the shortcut-triggered `open()`) if nothing
   was selected yet, or switches mode in place (keeping typed text) once
   a mode is already active.
3. Iterated the layout twice more per feedback: Man/Gen moved to the
   bottom row, then merged onto **one row** with Models/model
   name/accept/clear/mic (Man/Gen shrunk via a new `compact` param on
   `InkModeCapsule`) to reduce vertical space.
4. Idle placeholder changed from "Continue…  / AI · \ manual" to
   "Insert text" now that Man/Gen covers mode selection visually.

Model name uses `Modifier.basicMarquee()` to scroll instead of
truncating when it doesn't fit the row.

## Release process reminder

Every push to a feature branch in an active session should be followed by
a `release.yml` workflow-dispatch run (see chat history for the pattern:
`mcp__github__actions_run_trigger` with `workflow_id: "release.yml"`,
`inputs: {"version": "vX.Y.Z"}`) — this is a standing instruction from the
user for this branch, not a one-off. If the run fails, check whether the
Gradle build itself succeeded before assuming a code regression — the
`softprops/action-gh-release` publish step has intermittently failed on
transient GitHub API timeouts with the build otherwise green; `rerun_failed_jobs`
is the correct response to that specific failure mode, not a code fix.
