#!/usr/bin/env bash
# Tag a release so JitPack can build and host the AAR.
# Prereq: bump testchimp-rum/build.gradle.kts → libraryVersion, commit, push to main.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_LINE="$(grep -E '^val libraryVersion' testchimp-rum/build.gradle.kts | head -1)"
VERSION="$(echo "$VERSION_LINE" | sed -E 's/.*"([^"]+)".*/\1/')"

if [[ -z "$VERSION" ]]; then
  echo "Could not parse libraryVersion from testchimp-rum/build.gradle.kts" >&2
  exit 1
fi

echo "libraryVersion in Gradle: $VERSION"
echo "After push, JitPack will serve (see https://jitpack.io/#testchimphq/testchimp-rum-android):"
echo "  implementation(\"com.github.testchimphq:testchimp-rum-android:$VERSION\")"
echo "  maven(\"https://jitpack.io\")"
echo ""
read -r -p "Create annotated git tag '$VERSION' and push to origin? [y/N] " ok
if [[ "${ok:-}" != "y" && "${ok:-}" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Error: working tree or index is not clean. Commit or stash first." >&2
  exit 1
fi

git tag -a "$VERSION" -m "Release $VERSION"
git push origin "$VERSION"

echo ""
echo "Next: open https://jitpack.io/#testchimphq/testchimp-rum-android/$VERSION"
echo "Wait for green build, then use the dependency line shown there if it differs."
