# Checkpoints

A running log of points in this branch's history the user has flagged as
stable — "if the next round of work goes badly, come back here." Each
entry names the commit, what was considered done at that point, and how
to get back to it.

Reverting to a checkpoint (pick one, depending on how much you want to
discard):

```bash
# See what's changed since the checkpoint, without touching anything:
git diff <checkpoint-sha>..HEAD

# Throw away everything after the checkpoint on this branch (destructive —
# confirm with the user first; this is exactly the kind of operation the
# assistant should never run silently):
git reset --hard <checkpoint-sha>

# Or, non-destructively: branch off the checkpoint instead of rewriting
# history, so both lines of work stay around:
git checkout -b <new-branch-name> <checkpoint-sha>
```

## 2026-08-18 — Novel writing mode feature-complete

**Commit**: (this commit — see `git log -1` right after this file was
added; it's the one that adds `docs/CHECKPOINTS.md`,
`docs/features/novel-writing-mode.md`, and
`docs/features/roleplay-mode-current-state.md`).

**Branch**: `claude/prompt-window-ui-updates-ttkkdd`.

**What's considered done as of here**: the Novel side of the app (Plan /
Write / Chat / Review / Codex / Snippets) — most recently, a run of UI
work on the Write screen: rich-text formatting (bold/italic/underline/
strikethrough/superscript/subscript/color/highlight/font/size) via a
collapsible Format toolbar and a restructured long-press Format menu; a
"Prompting" menu (Extend/Summarize/Condense/Replace/Retry) on the scene
header; and several iterations on the floating Global Prompt Overlay
(Man/Gen mode toggle, condensed layout, Models/accept row). Full detail:
`docs/features/novel-writing-mode.md`.

**What's explicitly NOT done / known gaps carried forward**: nothing
Novel-specific flagged as broken at this point — the last few rounds of
this session were bug-fix/polish passes (see the `showMenu()` reopen-loop
writeup in the features doc) rather than new-feature work with open ends.

**Why this checkpoint exists**: the user is about to start heavy work on
Roleplay mode (three display sub-modes — see
`docs/features/roleplay-mode-current-state.md` for the audit that
preceded that work) and wants a clean, documented point to fall back to
if that work needs to be reverted independently of the Novel-mode changes
above.
