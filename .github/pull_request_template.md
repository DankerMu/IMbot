# Summary

## Change Summary

- What changed:
- Why now:
- Impacted areas:
- Out of scope:

## Verification

- Local checks:
- CI expectations:
- Risks or follow-ups:

## Linked Work

- Issue: #
- OpenSpec change: ``
- Requirements: `FR-xx`, `NFR-xx`
- Test plan IDs: `UT-xx`, `IT-xx`, `E2E-xx`, `PERF-xx`

## Agent Review

- Record review evidence for the current PR head. If new commits are pushed, rerun reviewer agents and update this section.
- Treat linked reviewer comments as immutable evidence. If a reviewer result changes, post a new PR comment and update the links below instead of editing a linked comment.
- If you do change linked reviewer comments or links, also edit the PR body or rerun `Repo Governance` so the evidence check reevaluates them.
- Reviewer agents used: ``
- Reviewed head SHA: ``
- Review evidence: `https://github.com/<owner>/<repo>/pull/<pr>#issuecomment-..., https://github.com/<owner>/<repo>/pull/<pr>#issuecomment-...`
- Key findings addressed: ``

### Reviewer Comment Format

Post one PR conversation comment per reviewer agent using this shape:

```text
Reviewer agent: <agent name>
Reviewed head SHA: <40-char commit SHA>
Summary: <one-line conclusion>
Findings:
- <finding 1 or "None.">
```

`PR Review Evidence` validates these linked PR conversation comments. Conversation resolution remains a separate GitHub merge rule for any threaded review discussions.
The linked reviewer comments are the source of truth; `Key findings addressed` is the concise PR-level roll-up.

## Checklist

- [ ] Scope still matches `docs/PRD.md`
- [ ] Engineering spec and UI spec stay aligned with the change
- [ ] OpenSpec ownership is explicit or updated in the same PR
- [ ] Added or updated the required tests for the affected requirement
- [ ] At least two reviewer agents completed cross-review, posted readable PR comments, and the `Agent Review` section matches the current PR head
- [ ] The PR has no unresolved conversations before merge
- [ ] CI gate activation was reviewed if this PR introduces a new package, test layer, or release path
