# Codacy Bandit

This is the docker engine we use at Codacy to have [Bandit](https://github.com/PyCQA/bandit) support.
You can also create a docker to integrate the tool and language of your choice!
See the [codacy-engine-scala-seed](https://github.com/codacy/codacy-engine-scala-seed) repository for more information.

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/22833b5348fb4fdf80ea370560648c0b)](https://www.codacy.com/gh/codacy/codacy-bandit?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=codacy/codacy-bandit&amp;utm_campaign=Badge_Grade)
[![Build Status](https://circleci.com/gh/codacy/codacy-bandit.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/codacy/codacy-bandit)

## Usage

You can create the docker by doing:

```bash
sbt universal:stage
docker build -t codacy-bandit .
```

The docker is ran with the following command:

```bash
docker run -it -v $srcDir:/src  <DOCKER_NAME>:<DOCKER_VERSION>
```

### Bump bandit version

* Change the `requirements.txt` file

### Generating documentation is obsolete
* Run `./scripts/generateDocs.sh`

## Docs

[Tool Developer Guide](https://support.codacy.com/hc/en-us/articles/207994725-Tool-Developer-Guide)

[Tool Developer Guide - Using Scala](https://support.codacy.com/hc/en-us/articles/207280379-Tool-Developer-Guide-Using-Scala)

## Test

We use the [codacy-plugins-test](https://github.com/codacy/codacy-plugins-test) to test our external tools integration.
You can follow the instructions there to make sure your tool is working as expected.

## Agent Playbook: Updating This Repository End-to-End

This section is written for an AI coding agent (or a human) tasked with updating this repo — most commonly bumping the wrapped Bandit version, but also base image / CircleCI orb bumps. Follow it top to bottom; it tells you what to change, how to regenerate derived files, how to test locally, and how to interpret CI so you can iterate on failures without guessing.

### 1. What this repository is

This is a **Codacy engine**: a thin Scala wrapper (built on `codacy-engine-scala-seed`, see `build.sbt`) that packages [Bandit](https://github.com/PyCQA/bandit) — a Python security linter — as a Docker image Codacy's platform can run against a customer's source code. Bandit itself is installed as a pip package inside the image (see `Dockerfile` / `requirements.txt`), it is not vendored Scala code.

The `docs/` directory is machine-consumed configuration, not just documentation:

- `docs/patterns.json` — the full list of Bandit rules ("patterns", e.g. `B101`, `B608`) Codacy knows about, their category/subcategory/level, and whether they're enabled by default. Generated file, do not hand-edit.
- `docs/description/description.json` + `docs/description/*.md` — human-readable titles/descriptions per pattern, used in the Codacy UI. Generated file, do not hand-edit.
- `docs/tests/*.py` and `docs/multiple-tests/{with-config,without-config}` — fixtures used by `codacy-plugins-test` to validate the engine actually produces the results it claims to for real code samples.
- `docs/tool-description.md` — short blurb about the tool, hand-maintained.

All the generated artifacts above are produced by a two-step pipeline, orchestrated by `scripts/generateDocs.sh`:

1. `scripts/generatePythonDocs.sh <version> <baseDir>` clones `PyCQA/bandit` at tag `<version>` into `<baseDir>`, creates a virtualenv, installs Bandit's own deps plus its Sphinx doc requirements, and runs `sphinx-build` to render Bandit's own documentation to HTML. This step runs inside a `python:3.12` Docker container (`generateDocs.sh` invokes `docker run ... python:3.12 bash scripts/generatePythonDocs.sh ...`), so it needs Docker and network access, but not a local Python install.
2. `sbt "doc-generator/run $VERSION $PWD/$BASE_DIR"` runs the Scala program `doc-generator/src/main/scala/docs/GenerateDocs.scala` (a separate sbt subproject, see the `doc-generator` project in `build.sbt`), which scrapes the freshly built Sphinx HTML for pattern definitions and writes `docs/patterns.json`, `docs/description/description.json`, and `docs/description/*.md`.

Confirmed from real history: commit `0e0b2b5` ("feat: Bump bandit to 1.9.4 [TCE-1554] (#80)") bumped `requirements.txt` and regenerated `docs/patterns.json` + `docs/description/*` accordingly — adding markdown/pattern-test files for newly introduced pattern IDs and removing them for dropped ones, while preserving hand-set `level`/`subcategory`/`enabled` values on patterns that survived. Treat that commit as the ground-truth template for the diff shape of a version bump.

### 2. Files that encode versions — check all of these on every update

| File | What it controls | What to check |
|---|---|---|
| `requirements.txt` → `bandit==<version>` | The Bandit release installed into the Docker image and the git tag `generateDocs.sh` clones for doc generation | Bump to the target version. Confirm a matching tag exists in [PyCQA/bandit](https://github.com/PyCQA/bandit/tags). |
| `build.sbt` → `codacy-engine-scala-seed` dependency | Codacy's engine SDK/base library | Check [Maven Central](https://mvnrepository.com/artifact/com.codacy/codacy-engine-scala-seed) if asked to bump it; not tied to Bandit version bumps. |
| `Dockerfile` → base image (`python:3.12-alpine3.21`) | Python runtime the packaged Bandit executes under | Only bump if the new Bandit version raises its minimum Python requirement, or if asked explicitly — don't bump opportunistically (history shows a dedicated commit, `6bf4c56` "Bump python version", separate from Bandit bumps). |
| `Dockerfile` → `openjdk11-jre` apk package | JRE the Scala engine wrapper itself runs on | Rarely needs touching; only if asked to change the JVM. |
| `.circleci/config.yml` → `codacy/base` orb | Shared CircleCI steps (checkout, versioning, sbt build, docker publish, tagging) | Check the latest published version in the CircleCI orb registry; `git log -p .circleci/config.yml` shows prior bump history as a fallback reference. |
| `.circleci/config.yml` → `codacy/plugins-test` orb | Runs `codacy-plugins-test` in CI after the image is built | Same as above. |

### 3. Step-by-step update procedure

1. **Bump the version** in `requirements.txt` (and `.circleci/config.yml` orbs / `Dockerfile` base image, if in scope for the task).
2. **Regenerate the docs**, from the repo root: `./scripts/generateDocs.sh`. This requires Docker (for the Python/Sphinx step) and `sbt`/network access (for the Scala scraping step). Review the diff for new/removed/renamed pattern IDs in `docs/patterns.json` and `docs/description/`, and check whether `docs/tests/` needs new fixture files for newly added patterns (add one small `.py` sample per new pattern, following the naming convention `docs/tests/<PatternId>.py` used by existing fixtures).
3. **Build the sbt project and format-check it**: `sbt "clean; set scalafmtUseIvy in ThisBuild := false; scalafmtCheckAll; Test / scalafmtCheck; scalafmtCheck; universal:stage"` (mirrors the CI `publish_docker_local` job).
4. **Build the Docker image**: `docker build -t codacy-bandit .`
5. **Run `codacy-plugins-test` locally** before pushing — clone https://github.com/codacy/codacy-plugins-test and run its `pattern`, `json`, and `multiple` DockerTest commands against your local image tag (the CI job runs the multi-test variant, see `run_multiple_tests: true` for `codacy_plugins_test/run` in `.circleci/config.yml`).
6. **Iterate on failures**, re-running only the relevant DockerTest command after each fix.
7. **Commit** the version bump together with the regenerated `docs/` files in one change.
8. **Push and open a PR.** CI runs `checkout_and_version` -> `publish_docker_local` -> `plugins_test` -> `publish_docker` (master only) -> `tag_version`.
9. **Poll the PR's real CI checks until they all pass — local validation is NOT the finish line.** After every push, run `gh pr checks <pr-url>` and keep re-polling (short sleep while any check is `pending`) until all checks finish. If a check fails, fetch its actual log (CircleCI API/UI for the failing job — don't guess), find the true root cause, fix it, push again (never `--no-verify`, never force-push), and re-poll. Repeat until every check is green. The CI environment's toolchain can differ from your local one, so a clean local run does not guarantee CI passes. Only stop iterating when every check passes, or you hit a genuine product/infra decision that needs a human — in which case explain it in the PR rather than guessing.

### 4. Common failure modes and fixes

| Symptom | Likely cause | Fix |
|---|---|---|
| `scalafmtCheckAll`/`scalafmtCheck` fails in CI/locally | Generated or hand-edited Scala file not formatted | Run `sbt scalafmt` then re-run the check command |
| `generatePythonDocs.sh` / `sphinx-build` step fails | Wrong/nonexistent version tag, or Bandit's own doc dependencies changed | Verify the tag exists upstream in `PyCQA/bandit`; check Bandit's `doc/requirements.txt` for changes |
| `pattern`/`json` DockerTest fails after a bump | Pattern IDs renamed/removed/added upstream between versions and `docs/patterns.json`/`description.json` weren't fully regenerated or reviewed | Re-run `./scripts/generateDocs.sh`; diff against the previous `docs/patterns.json` and confirm every add/remove matches Bandit's upstream changelog for that version |
| `multiple` DockerTest fails on a specific fixture folder | `docs/multiple-tests/{with-config,without-config}` expectations stale for new tool behavior | Regenerate/update the expected results to match the new (verified correct) output |
| CI `publish_docker`/`tag_version` don't run on your branch | Expected — gated to the default branch only | Nothing to fix |

### 5. Definition of done

- Bandit version bump reflected in `requirements.txt` (and any CI/base-image files in scope).
- `docs/patterns.json`, `docs/description/description.json`, and `docs/description/*.md` regenerated via `./scripts/generateDocs.sh`, with new/removed pattern IDs reconciled against upstream Bandit's changelog.
- New pattern fixtures added under `docs/tests/` for any newly introduced pattern IDs.
- Local `sbt ... scalafmtCheckAll ... universal:stage` build passes.
- Docker image builds successfully.
- `codacy-plugins-test` commands (`pattern`, `json`, `multiple`) all pass locally against the freshly built image.
- **After pushing and opening/updating the PR, every CI check on it is green.** Poll `gh pr checks <pr-url>` and iterate on any failure (fetch the real CI log, fix, push, re-poll) until all pass — a passing local build is not sufficient, because the CI toolchain can differ from your local one (see step 9).

## What is Codacy

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy’s features

- Identify new Static Analysis issues
- Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
- Auto-comments on Commits and Pull Requests
- Integrations with Slack, HipChat, Jira, YouTrack
- Track issues in Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

## Free for Open Source

Codacy is free for Open Source projects.
