# Branch map

_Added Sprint 24I (2026-08-02) after a repository review found three unrelated
branches that could each be mistaken for "the" current line of development._

## Canonical branch

**`claude/environment-selection-e607ds`** (continued as
`claude/sprint-24h2-recovery-6q6u16` as of Sprint 24H-2/24I) is the one
branch that contains every sprint of actual work — 118+ commits including
the T1–T12 Talent/Recruitment/Exit suite, WP-001–009, all Payroll
milestones, and Sprints 13 through 24I. **Any new work should branch from
here.**

## ⚠️ GitHub's configured default branch is wrong

As of this writing, `buntychavan-web/EWOS`'s default branch (what a fresh
`git clone` checks out, and what `create_branch` uses when `from_branch` is
omitted) is **`claude/repository-selection-575dn9`** — see "Obsolete
branches" below. This is very likely what caused prior sessions to start
work from the wrong base. **Recommended one-time fix:** a repo admin should
change the default branch to `claude/sprint-24h2-recovery-6q6u16` (or
whatever branch is canonical at the time) under Settings → Branches. This
tool session has no API access to change repository default-branch
settings, so it's called out here rather than attempted silently.

## Obsolete / stale branches

- **`main`, `ewos-main`** — both at `80a4111` ("Sprint 20 v1.0 readiness").
  These *are* ancestors of the canonical branch (confirmed via `git
  merge-base --is-ancestor`), just never fast-forwarded — stale, not
  divergent. Safe to fast-forward to the canonical branch's tip whenever
  convenient; not urgent since nothing is lost by leaving them behind.
- **`claude/repository-selection-575dn9`** — forked off `c63947c` (~Sprint
  5) and took a completely different 10-commit path with its own Company/
  Organization/Person domain model. Confirmed via `git merge-base
  --is-ancestor`: **not an ancestor** of the canonical branch — an abandoned
  experimental fork, not "behind." Currently the repo's default branch (see
  above), which is the actual problem, not the branch's existence. Once the
  default branch is repointed, this can be deleted outright (all 10 commits
  are reachable from this branch's own history if anyone needs them later;
  nothing here was ever merged into the canonical line).
- All other `claude/t*-*`, `claude/wp-*`, `claude/payroll-*` branches —
  these are historical per-PR branches from the canonical lineage's own
  development (each already merged into it). They're harmless clutter, not
  divergent work; clean up at leisure via GitHub's "delete branch after
  merge" setting rather than a one-time sweep.

## Rule going forward

Before starting work in a new session, check ancestry
(`git merge-base --is-ancestor <candidate> <canonical>`) rather than
assuming the default branch or the most recently-named branch is current.
