# EWOS backend — raw Kubernetes manifests

Plain manifests for a single-cluster deploy without Helm. For multi-environment
or repeatable deploys, use the Helm chart in `../helm/ewos` instead — these
two are alternatives, not both-required.

## Apply order

```bash
kubectl apply -f namespace.yaml
kubectl apply -f configmap.yaml

# Copy secret.example.yaml -> secret.yaml, fill in real values, then:
kubectl apply -f secret.yaml
# ...or create the Secret imperatively without writing it to disk — see the
# comment at the top of secret.example.yaml.

kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f hpa.yaml       # optional
kubectl apply -f ingress.yaml   # optional — requires an ingress controller + cert-manager
```

## Notes

- `image: ghcr.io/buntychavan-web/ewos:latest` in `deployment.yaml` is a
  placeholder — point it at wherever your CI publishes the image built from
  the repo's `Dockerfile`, pinned to a specific tag/digest in real
  deployments (not `:latest`).
- The Postgres and Redis the backend needs are **not** included here — bring
  your own (managed RDS/Cloud SQL, a Bitnami/official chart, etc.) and point
  `SPRING_DATASOURCE_URL` / `REDIS_HOST` at them via the Secret/ConfigMap.
- `runAsUser: 10001` matches the fixed UID baked into the Dockerfile's
  runtime stage — don't change one without the other.
- Liveness/readiness probes hit `/actuator/health/liveness` and
  `/actuator/health/readiness` (Spring Boot's Kubernetes probe groups,
  already enabled via `management.endpoint.health.probes.enabled: true` in
  `application.yml`).
