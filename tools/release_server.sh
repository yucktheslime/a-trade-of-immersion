#!/usr/bin/env bash
# Publishes kagrenac-server/ as https://github.com/yucktheslime/toi-kagrenac-server (the repository the
# DwemerDistro launcher lists), and optionally a launcher release of it.
#
#   tools/release_server.sh sync              push the kagrenac-server/ history to the server repo's main
#   tools/release_server.sh package vX.Y.Z    build the release folder (server ZIP, client ZIP with the NeoForge
#                                             jar, kagrenac-release.json) under ../toi-release/vX.Y.Z; changes nothing
#   tools/release_server.sh publish vX.Y.Z    publish that folder as a stable GitHub release (adds topic kagrenac-mod)
#
# The server repository's main is always `git subtree split --prefix=kagrenac-server` of this repository's
# main, so every push is a fast-forward. Nothing is ever force-pushed. Needs git, gh (signed in) and python3.
set -euo pipefail
REPO_SLUG="yucktheslime/toi-kagrenac-server"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TOI_RELEASE_DIR:-$ROOT/../toi-release}"
cd "$ROOT"

split() { git subtree split --prefix=kagrenac-server HEAD 2>/dev/null | tail -n1; }

case "${1:-}" in
sync)
    [ -z "$(git status --porcelain -- kagrenac-server)" ] || { echo "Commit kagrenac-server/ first." >&2; exit 1; }
    sha="$(split)"
    echo "kagrenac-server/ at $(git rev-parse --short HEAD) -> server commit $sha"
    git push "https://github.com/$REPO_SLUG.git" "$sha:refs/heads/main"
    ;;
package)
    tag="${2:?version tag, e.g. v0.3.2}"
    sha="$(split)"
    remote="$(git ls-remote "https://github.com/$REPO_SLUG.git" refs/heads/main | cut -f1)"
    [ "$remote" = "$sha" ] || { echo "The server repo's main ($remote) is not $sha; run: $0 sync" >&2; exit 1; }
    ver="$(sed -n 's/^mod_version=//p' gradle.properties)"
    jar="neoforge/build/libs/a-trade-of-immersion-neoforge-$ver.jar"
    [ -f "$jar" ] || { echo "Build first: ./gradlew build ($jar missing)" >&2; exit 1; }
    [ "$tag" = "v$ver" ] || { echo "Tag $tag does not match mod_version $ver." >&2; exit 1; }
    work="$(mktemp -d)"
    git clone -q "https://github.com/$REPO_SLUG.git" "$work/server"
    git -C "$work/server" checkout -q "$sha"
    mkdir "$work/client"
    cp "$jar" "$work/client/"
    mkdir -p "$OUT"
    python3 "$work/server/scripts/publish.py" package --repo "$work/server" --client-dir "$work/client" \
        --version "$tag" --output "$OUT/$tag"
    echo "Review: unzip -l $OUT/$tag/trade-of-immersion-server.zip ; unzip -l $OUT/$tag/trade-of-immersion-client.zip"
    ;;
publish)
    tag="${2:?version tag}"
    [ -f "$OUT/$tag/kagrenac-release.json" ] || { echo "Run: $0 package $tag" >&2; exit 1; }
    work="$(mktemp -d)"
    git clone -q "https://github.com/$REPO_SLUG.git" "$work/server"
    python3 "$work/server/scripts/publish.py" publish --output "$OUT/$tag" --confirm-publish "$REPO_SLUG"
    ;;
*)
    sed -n '2,13p' "$0"; exit 1 ;;
esac
