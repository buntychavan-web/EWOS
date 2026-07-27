# Deployment guide

Covers everything past the container image itself — see
[`docker.md`](./docker.md) for how that image is built. This page is
Kubernetes/Helm, required configuration, and secrets handling.

## Required configuration before any non-dev deploy

The `prod` profile (`SPRING_PROFILES_ACTIVE=prod`) refuses to start unless
these are set to real values — see `.env.example` at the repo root for the
full variable reference:

| Variable | Guard | Failure mode if left at the dev/test default |
| --- | --- | --- |
| `JWT_SECRET` | `JwtSecretGuard` | `IllegalStateException` at boot — placeholder or < 32 bytes |
| `ADMIN_PASSWORD` | `AdminPasswordGuard` | `IllegalStateException` at boot — still `ChangeMe!Admin123` |
| `APP_CORS_ALLOWED_ORIGINS` | `CorsConfig` (ADR-0004) | `IllegalStateException` at boot if `*` is set; a warning (not a boot failure) if left empty |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | none — required, no default in `application-prod.yml` | Spring context fails to start (no placeholder value at all) |

Generate a real `JWT_SECRET` with `openssl rand -base64 48`.

## Deploying to Kubernetes

Two equivalent options — pick one, they are not meant to be applied together:

- **`k8s/`** — plain manifests for a single cluster/environment. See
  `k8s/README.md` for apply order.
- **`helm/ewos/`** — the same resources as a parametrized chart, for
  multiple environments or repeatable installs:

  ```bash
  helm install ewos ./helm/ewos \
    --namespace ewos --create-namespace \
    --set image.tag=<git-sha-or-release-tag> \
    --set secrets.SPRING_DATASOURCE_URL='jdbc:postgresql://<host>:5432/ewos' \
    --set secrets.SPRING_DATASOURCE_PASSWORD='<password>' \
    --set secrets.JWT_SECRET="$(openssl rand -base64 48)" \
    --set secrets.ADMIN_PASSWORD='<strong-password>'
  ```

  Run `helm lint ./helm/ewos` and `helm template ./helm/ewos` before a first
  real install to confirm the rendered manifests look right in your cluster.

Neither option provisions Postgres/Redis — bring your own (managed
RDS/CloudSQL/ElastiCache, or a separate chart) and point the Secret/ConfigMap
at it.

## Secrets handling

Never commit filled-in secret values. `k8s/secret.example.yaml` and
`helm/ewos/values.yaml`'s `secrets:` block are templates only — the repo's
`.gitignore` blocks `k8s/secret.yaml` and `helm/**/secrets.yaml` /
`helm/**/values-*.local.yaml` so a real copy can't be committed by accident.
In CI, populate secrets from your platform's secret store (GitHub Actions
Environments, Vault, cloud Secrets Manager) rather than checking anything in.

## Rolling deploys / zero-downtime

- `readinessProbe` (`/actuator/health/readiness`) gates traffic until Flyway
  migrations and the DB/Redis connections are actually ready — don't remove
  it.
- Two replicas by default (`replicaCount` / `k8s/deployment.yaml`) so a
  rolling update always has at least one pod serving traffic.
- The HPA (`k8s/hpa.yaml` / `helm/ewos/templates/hpa.yaml`) scales on CPU and
  memory; tune the thresholds once you have real production load data —
  70%/80% are reasonable starting points, not measured numbers.

## What this repo does not decide for you

DNS, TLS certificate issuance (the Ingress examples assume cert-manager),
the ingress controller itself, and the Postgres/Redis instances are cluster/
platform choices outside this repo's scope — the manifests here assume they
already exist and just wire the backend up to them.
