# SonarQube / SonarCloud integration

We use Sonar as a **supplementary** quality check on top of the Maven build's own gates. Sonar is
not a replacement for Spotless, Checkstyle, PMD, SpotBugs, or JaCoCo — those run every build. Sonar
lets us track trends over time and see one merged view of the code smells that each of those tools
flags individually.

## Configuration

- `sonar-project.properties` at the repo root pins:
  - `sonar.projectKey=buntychavan-web_ewos`
  - `sonar.java.source=21`
  - `sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml`
  - Exclusion list matching our JaCoCo exclusions (generated code, DTOs, entities, wiring).
- The JaCoCo XML report is produced by `mvn verify` because the `jacoco-maven-plugin` runs its
  `report` goal in that phase.

## Running locally

You need two environment variables:

```bash
export SONAR_TOKEN=your-personal-token
export SONAR_HOST_URL=https://sonarcloud.io   # or your self-hosted URL
```

Then:

```bash
mvn -B verify           # produces JaCoCo XML at target/site/jacoco/jacoco.xml
mvn -B sonar:sonar      # uploads results
```

The scanner is pulled in as a Maven plugin on demand; no extra install is required.

## Running in CI

Add a step after the existing `mvn verify` step:

```yaml
- name: Sonar scan
  if: ${{ secrets.SONAR_TOKEN != '' }}
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
    SONAR_HOST_URL: ${{ vars.SONAR_HOST_URL }}
  run: mvn -B sonar:sonar
```

The `if:` guard means forks and PRs from external contributors don't fail because they don't have
the secret.

## Quality gate

- Coverage floor is enforced by JaCoCo in Maven (`jacoco.line.coverage.min=0.80`, BUNDLE
  instruction coverage). Sonar's own coverage threshold is set to the same **80%** so both agree.
- New code quality gate ("A" rating on maintainability, reliability, security) is a Sonar-side
  setting, not repo config; owners configure it in the SonarCloud UI.

## What NOT to do

- Don't add `sonar.exclusions` for a file just because it has a smell. Fix the smell or add a PR
  discussion explaining why the smell is acceptable.
- Don't lower `sonar.coverage.jacoco.xmlReportPaths` — the JaCoCo output path is fixed by the pom.
- Don't add per-branch Sonar keys. One project, one history.
