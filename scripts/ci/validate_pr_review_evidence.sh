#!/usr/bin/env bash
set -euo pipefail

event_path="${GITHUB_EVENT_PATH:-}"

if [[ -z "$event_path" || ! -f "$event_path" ]]; then
  echo "error: GITHUB_EVENT_PATH must point to a pull_request event payload" >&2
  exit 1
fi

python3 - "$event_path" <<'PY'
import json
import pathlib
import re
import sys

event_path = pathlib.Path(sys.argv[1])
event = json.loads(event_path.read_text())
pull_request = event.get("pull_request")

if not pull_request:
    print("No pull_request payload found; skipping review evidence validation.")
    sys.exit(0)

body = pull_request.get("body") or ""
errors = []


def get_field(label: str):
    pattern = re.compile(rf"^- {re.escape(label)}:\s*(.*)$", re.MULTILINE)
    match = pattern.search(body)
    return match.group(1).strip() if match else None


def is_placeholder(value: str) -> bool:
    normalized = value.strip().strip("`").strip().lower()
    return normalized in {"", "pending", "tbd", "todo", "n/a", "na", "none"}


reviewers = get_field("Reviewer agents used")
reviewed_head_sha = get_field("Reviewed head SHA")
review_evidence = get_field("Review evidence")
findings = get_field("Key findings addressed")
current_head_sha = (pull_request.get("head") or {}).get("sha") or ""

if reviewers is None:
    errors.append("missing `Reviewer agents used` field in PR body")
elif is_placeholder(reviewers):
    errors.append("`Reviewer agents used` must list at least two reviewer agents")
else:
    entries = [
        token.strip()
        for token in re.split(r"\s*,\s*|\s*;\s*|\s+and\s+", reviewers.strip().strip("`"))
        if token.strip()
    ]
    if len(entries) < 2:
        errors.append("`Reviewer agents used` must list at least two reviewer agents")

if reviewed_head_sha is None:
    errors.append("missing `Reviewed head SHA` field in PR body")
elif is_placeholder(reviewed_head_sha):
    errors.append("`Reviewed head SHA` must match the current pull request head SHA")
elif reviewed_head_sha.strip().strip("`").strip() != current_head_sha:
    errors.append("`Reviewed head SHA` does not match the current pull request head SHA; rerun reviewer agents after the latest commit")

if review_evidence is None:
    errors.append("missing `Review evidence` field in PR body")
elif is_placeholder(review_evidence):
    errors.append("`Review evidence` must describe where the agent review record lives")

if findings is None:
    errors.append("missing `Key findings addressed` field in PR body")
elif is_placeholder(findings):
    errors.append("`Key findings addressed` must summarize the resolved findings or state that no material findings were raised")

if errors:
    for error in errors:
        print(f"error: {error}", file=sys.stderr)
    print(
        "The `## Agent Review` section must contain at least two reviewer agents, the current PR head SHA, review evidence, and a findings summary.",
        file=sys.stderr,
    )
    sys.exit(1)

print("PR review evidence validated.")
PY
