#!/usr/bin/env bash
#
# Sweep the PR pre-releases produced by ci.yml (tag `pr-<number>-<sha>`).
#
# Policy:
#   - a stable release (prerelease == false) is NEVER touched;
#   - a pre-release whose PR is closed or merged is deleted;
#   - for a PR still open, only the most recent pre-release is kept;
#   - the git tag is deleted with the release, so no orphan tags pile up.
#
# Environment:
#   REPO      owner/name of the repository (required)
#   DRY_RUN   "true" -> log what would be deleted, delete nothing
#   GH_TOKEN  token used by `gh` (needs contents:write to delete)
#
# Portability: bash 3.2 + BSD userland (macOS runners).
# No mapfile, no associative arrays, no ${var,,}, no `sed -i`, no `date -d`.

set -eu

REPO="${REPO:?REPO must be set to owner/name}"
DRY_RUN="${DRY_RUN:-false}"

TAB=$(printf '\t')
WORK=$(mktemp -d 2>/dev/null || mktemp -d -t sweep)
trap 'rm -rf "$WORK"' EXIT INT TERM

RELEASES="$WORK/releases.tsv"
CANDIDATES="$WORK/candidates.tsv"
PR_NUMBERS="$WORK/pr-numbers.txt"
PR_STATES="$WORK/pr-states.tsv"

deleted=0
kept=0
skipped=0

log() { printf '%s\n' "$*"; }

# --------------------------------------------------------------------------
# 1. Inventory every release. `id` is monotonically increasing, so it orders
#    pre-releases by age without any date parsing (BSD `date` has no -d).
# --------------------------------------------------------------------------
gh api "repos/$REPO/releases?per_page=100" --paginate \
  --jq '.[] | [(.prerelease|tostring), (.id|tostring), .tag_name] | @tsv' \
  >"$RELEASES"

total=$(wc -l <"$RELEASES" | tr -d ' ')
stable=$(awk -F"$TAB" '$1 == "false"' "$RELEASES" | wc -l | tr -d ' ')
log "Repository ......... $REPO"
log "Dry run ............ $DRY_RUN"
log "Releases inventoried $total (stable: $stable — never touched)"

# --------------------------------------------------------------------------
# 2. Keep only pre-releases whose tag is a CI PR build. Anything else
#    (hand-made pre-release, semantic-release channel tag, ...) is left alone.
#    Sort by PR number ascending, then release id descending: the first line
#    of each PR group is that PR's most recent build.
# --------------------------------------------------------------------------
awk -F"$TAB" -v OFS="$TAB" '
  $1 == "true" && $3 ~ /^pr-[0-9]+-[0-9a-f]+$/ {
    n = $3
    sub(/^pr-/, "", n)
    sub(/-.*$/, "", n)
    print n, $2, $3
  }
' "$RELEASES" | sort -t"$TAB" -k1,1n -k2,2nr >"$CANDIDATES"

candidate_count=$(wc -l <"$CANDIDATES" | tr -d ' ')
other_prereleases=$(awk -F"$TAB" '$1 == "true"' "$RELEASES" | wc -l | tr -d ' ')
other_prereleases=$((other_prereleases - candidate_count))
log "PR pre-releases .... $candidate_count"
log "Other pre-releases . $other_prereleases (left untouched)"
log ""

if [ "$candidate_count" -eq 0 ]; then
  log "Nothing to sweep."
  exit 0
fi

# --------------------------------------------------------------------------
# 3. Resolve each PR state once (open / closed). A PR that cannot be read is
#    reported and its pre-releases are kept — never delete on a doubt.
# --------------------------------------------------------------------------
cut -f1 "$CANDIDATES" | sort -n | uniq >"$PR_NUMBERS"
: >"$PR_STATES"

while read -r pr; do
  [ -n "$pr" ] || continue
  if state=$(gh api "repos/$REPO/pulls/$pr" --jq '.state' 2>/dev/null </dev/null); then
    :
  else
    state="unknown"
  fi
  printf '%s%s%s\n' "$pr" "$TAB" "$state" >>"$PR_STATES"
done <"$PR_NUMBERS"

# --------------------------------------------------------------------------
# 4. Delete. The prerelease flag is re-read from the API right before the
#    call, so a stable release can never be removed by a stale listing.
# --------------------------------------------------------------------------
delete_release() {
  rel_id=$1
  rel_tag=$2
  reason=$3

  is_pre=$(gh api "repos/$REPO/releases/$rel_id" --jq '.prerelease' 2>/dev/null </dev/null || echo "error")
  if [ "$is_pre" != "true" ]; then
    log "  GUARD    release $rel_id ($rel_tag) is not a pre-release ($is_pre) — refused"
    skipped=$((skipped + 1))
    return 0
  fi

  if [ "$DRY_RUN" = "true" ]; then
    log "  WOULD DELETE  $rel_tag (release $rel_id) — $reason"
    deleted=$((deleted + 1))
    return 0
  fi

  log "  DELETE   $rel_tag (release $rel_id) — $reason"
  gh api -X DELETE "repos/$REPO/releases/$rel_id" --silent </dev/null
  if gh api -X DELETE "repos/$REPO/git/refs/tags/$rel_tag" --silent 2>/dev/null </dev/null; then
    log "           tag $rel_tag deleted"
  else
    log "           tag $rel_tag already absent"
  fi
  deleted=$((deleted + 1))
}

while read -r pr state; do
  [ -n "$pr" ] || continue
  group="$WORK/group-$pr.tsv"
  awk -F"$TAB" -v pr="$pr" '$1 == pr { print $2 "\t" $3 }' "$CANDIDATES" >"$group"
  group_count=$(wc -l <"$group" | tr -d ' ')

  case "$state" in
    closed)
      log "PR #$pr [closed or merged] — $group_count pre-release(s), all obsolete"
      while read -r rel_id rel_tag; do
        [ -n "$rel_id" ] || continue
        delete_release "$rel_id" "$rel_tag" "PR closed or merged"
      done <"$group"
      ;;
    open)
      newest_tag=$(head -1 "$group" | cut -f2)
      log "PR #$pr [open] — $group_count pre-release(s), keeping $newest_tag"
      kept=$((kept + 1))
      # a pipe would run the loop in a subshell and lose the counters
      tail -n +2 "$group" >"$group.superseded"
      while read -r rel_id rel_tag; do
        [ -n "$rel_id" ] || continue
        delete_release "$rel_id" "$rel_tag" "superseded by $newest_tag"
      done <"$group.superseded"
      ;;
    *)
      log "PR #$pr [unreadable] — $group_count pre-release(s) kept, nothing deleted"
      skipped=$((skipped + group_count))
      ;;
  esac
done <"$PR_STATES"

log ""
if [ "$DRY_RUN" = "true" ]; then
  log "Summary (dry run): $deleted pre-release(s) would be deleted, $kept kept as the latest build of an open PR, $skipped skipped."
else
  log "Summary: $deleted pre-release(s) deleted, $kept kept as the latest build of an open PR, $skipped skipped."
fi

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  # shellcheck disable=SC2016  # the backticks are markdown, not substitutions
  {
    printf '### PR pre-release sweep\n\n'
    printf '| Field | Value |\n|---|---|\n'
    printf '| Repository | `%s` |\n' "$REPO"
    printf '| Dry run | `%s` |\n' "$DRY_RUN"
    printf '| Releases inventoried | %s |\n' "$total"
    printf '| Stable releases (never touched) | %s |\n' "$stable"
    printf '| PR pre-releases examined | %s |\n' "$candidate_count"
    printf '| Deleted | %s |\n' "$deleted"
    printf '| Kept (latest build of an open PR) | %s |\n' "$kept"
    printf '| Skipped | %s |\n' "$skipped"
  } >>"$GITHUB_STEP_SUMMARY"
fi
