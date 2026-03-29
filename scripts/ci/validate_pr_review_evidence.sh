#!/usr/bin/env bash
set -euo pipefail

event_path="${GITHUB_EVENT_PATH:-}"

if [[ -z "$event_path" || ! -f "$event_path" ]]; then
  echo "error: GITHUB_EVENT_PATH must point to a pull_request event payload" >&2
  exit 1
fi

python3 - "$event_path" <<'PY'
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request

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


def normalize_text(value: str) -> str:
    return value.strip().strip("`").strip()


def parse_reviewer_entries(value: str):
    return [
        token.strip()
        for token in re.split(r"\s*,\s*|\s*;\s*|\s+and\s+", normalize_text(value))
        if token.strip()
    ]


def normalize_agent_key(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).casefold()


def fetch_issue_comment(full_name: str, comment_id: str):
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if not token:
        raise RuntimeError("GITHUB_TOKEN is required to validate review evidence comment URLs")

    request = urllib.request.Request(
        f"https://api.github.com/repos/{full_name}/issues/comments/{comment_id}",
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def extract_comment_field(body_text: str, label: str):
    pattern = re.compile(rf"^{re.escape(label)}:\s*(.+)$", re.MULTILINE)
    match = pattern.search(body_text)
    return match.group(1).strip() if match else None


reviewers = get_field("Reviewer agents used")
reviewed_head_sha = get_field("Reviewed head SHA")
review_evidence = get_field("Review evidence")
findings = get_field("Key findings addressed")
current_head_sha = (pull_request.get("head") or {}).get("sha") or ""
repository = event.get("repository") or {}
repository_full_name = repository.get("full_name") or ""
pr_number = pull_request.get("number")

if reviewers is None:
    errors.append("missing `Reviewer agents used` field in PR body")
elif is_placeholder(reviewers):
    errors.append("`Reviewer agents used` must list at least two reviewer agents")
else:
    entries = parse_reviewer_entries(reviewers)
    unique_entries = {normalize_agent_key(entry): entry for entry in entries}
    if len(unique_entries) < 2:
        errors.append("`Reviewer agents used` must list at least two reviewer agents")

if reviewed_head_sha is None:
    errors.append("missing `Reviewed head SHA` field in PR body")
elif is_placeholder(reviewed_head_sha):
    errors.append("`Reviewed head SHA` must match the current pull request head SHA")
elif normalize_text(reviewed_head_sha) != current_head_sha:
    errors.append("`Reviewed head SHA` does not match the current pull request head SHA; rerun reviewer agents after the latest commit")

if review_evidence is None:
    errors.append("missing `Review evidence` field in PR body")
elif is_placeholder(review_evidence):
    errors.append("`Review evidence` must link at least two PR conversation comments")
elif not repository_full_name or pr_number is None:
    errors.append("repository and pull request metadata are required to validate review evidence links")
else:
    expected_prefix = f"https://github.com/{repository_full_name}/pull/{pr_number}#issuecomment-"
    comment_urls = re.findall(r"https://github\.com/\S+#issuecomment-\d+", review_evidence)
    comment_urls = [url.rstrip("`,.") for url in comment_urls if url.startswith(expected_prefix)]
    unique_comment_urls = list(dict.fromkeys(comment_urls))

    if len(unique_comment_urls) < 2:
        errors.append("`Review evidence` must link at least two PR conversation comments for this pull request")
    else:
        comment_ids = []
        for url in unique_comment_urls:
            match = re.search(r"#issuecomment-(\d+)$", url)
            if match:
                comment_ids.append(match.group(1))

        linked_reviewers = {}
        for comment_id in comment_ids:
            try:
                comment = fetch_issue_comment(repository_full_name, comment_id)
            except (RuntimeError, urllib.error.HTTPError, urllib.error.URLError) as exc:
                errors.append(f"failed to fetch review evidence comment `{comment_id}`: {exc}")
                continue

            comment_body = comment.get("body") or ""
            comment_reviewer = extract_comment_field(comment_body, "Reviewer agent")
            comment_sha = extract_comment_field(comment_body, "Reviewed head SHA")
            comment_summary = extract_comment_field(comment_body, "Summary")

            if comment_reviewer is None or is_placeholder(comment_reviewer):
                errors.append(f"review evidence comment `{comment_id}` is missing a `Reviewer agent` line")
                continue
            if comment_sha is None or normalize_text(comment_sha) != current_head_sha:
                errors.append(f"review evidence comment `{comment_id}` must record the current pull request head SHA")
                continue
            if comment_summary is None or is_placeholder(comment_summary):
                errors.append(f"review evidence comment `{comment_id}` is missing a non-placeholder `Summary` line")
                continue
            if not re.search(r"^Findings:\s*$", comment_body, re.MULTILINE):
                errors.append(f"review evidence comment `{comment_id}` must include a `Findings:` section")
                continue

            linked_reviewers[normalize_agent_key(comment_reviewer)] = comment_reviewer

        if len(linked_reviewers) < 2:
            errors.append("linked review evidence comments must represent at least two unique reviewer agents")
        elif reviewers is not None and not is_placeholder(reviewers):
            pr_body_reviewers = {normalize_agent_key(entry): entry for entry in parse_reviewer_entries(reviewers)}
            if set(pr_body_reviewers) != set(linked_reviewers):
                errors.append("`Reviewer agents used` must match the reviewer-agent names recorded in the linked PR comments")

if findings is None:
    errors.append("missing `Key findings addressed` field in PR body")
elif is_placeholder(findings):
    errors.append("`Key findings addressed` must summarize the resolved findings or state that no material findings were raised")

if errors:
    for error in errors:
        print(f"error: {error}", file=sys.stderr)
    print(
        "The `## Agent Review` section must contain at least two unique reviewer agents, the current PR head SHA, links to PR conversation comments for the current head, and a findings summary.",
        file=sys.stderr,
    )
    sys.exit(1)

print("PR review evidence validated.")
PY
