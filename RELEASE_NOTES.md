# EWOS backend — release notes

## 2026-07-27 — CTO Production Readiness Audit

A cross-cutting remediation pass across CI, security, and deployment
readiness. See `PROJECT_STATUS.md` §0 for the fuller writeup; this is the
condensed version.

### Fixed
- CI never actually ran on `ewos-main` — the branch every sprint since
  Sprint 1.1 developed on — because the workflow's branch trigger only
  listed `main`. `.github/workflows/ci.yml` now triggers on both.
- Reformatted 139 files that had drifted out of Spotless compliance
  (cosmetic; verified against the full unit suite before and after —
  no functional change).
- `main` fast-forwarded to `ewos-main`'s tip (0 divergent commits,
  verified via `git merge-base --is-ancestor` before merging) so the
  default branch reflects the same code as the active development branch.

### Added
- `AdminPasswordGuard` — refuses to boot outside `dev`/`test` if
  `ADMIN_PASSWORD` is still the shipped placeholder, mirroring the existing
  `JwtSecretGuard` for `JWT_SECRET`.
- `k8s/` (plain manifests) and `helm/ewos/` (parametrized chart) for
  Kubernetes deployment; `.env.example`; `docs/operations/deployment.md`.

### Changed
- `server.error.include-message` / `include-binding-errors` now default to
  `never` in the base `application.yml` (secure-by-default), with `dev`/
  `test` profiles explicitly opting back into verbose messages for local
  debugging. `prod` already had this right; the change closes the gap for
  any profile-less or new-profile deployment.

### Known limitations
- GitHub's repository default-branch *setting* could not be changed via
  any tool available in this environment (no repo-settings API access) —
  documented as a manual step for a repo admin in `PROJECT_STATUS.md` §11.
- The frontend's production API routing (`/api/v1/*` → this backend) is not
  automatically wired up by either repo — see `enterprise-core`'s
  `docs/DEPLOYMENT.md`.
