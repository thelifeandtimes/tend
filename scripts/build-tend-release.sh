#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
version=$(tr -d '[:space:]' < "$repo_dir/VERSION")
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "VERSION must be semantic x.y.z" >&2; exit 1; }
[[ -e "$repo_dir/.git" ]] || { echo "Build releases from a Tend Git checkout" >&2; exit 1; }

output_dir=${1:-"$repo_dir/dist"}
mkdir -p "$output_dir"
work_dir=$(mktemp -d)
trap 'chmod -R u+rwX "$work_dir"; rm -rf "$work_dir"' EXIT
release_name="tend-$version"
release_dir="$work_dir/$release_name"
mkdir -p "$release_dir"

# An explicit component allowlist plus Git's index includes the complete source
# without copying ignored APKs, Gradle caches, signing keys, or runtime fixtures.
# Stage new files before packaging; tracked edits are read from the working tree.
git -C "$repo_dir" ls-files -z -- \
  .gitignore AGENTS.md Makefile PRODUCT_ARCHITECTURE_PLAN.md README.md VERSION \
  bin desk docs mobile omarchy-plugin packaging scripts skills tests \
  | tar -C "$repo_dir" --null --verbatim-files-from --no-recursion -T - -cf - \
  | tar -C "$release_dir" -xf -
cp "$repo_dir/packaging/tend/install.sh" "$release_dir/install.sh"
chmod 0755 "$release_dir/bin/tend" "$release_dir/install.sh" "$release_dir/mobile/gradlew"

(
  cd "$release_dir"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)

archive_name="$release_name.tar.gz"
archive="$output_dir/$archive_name"
tar --sort=name --mtime='UTC 1970-01-01' --owner=0 --group=0 --numeric-owner -czf "$archive" -C "$work_dir" "$release_name"
(
  cd "$output_dir"
  sha256sum "$archive_name" > "$archive_name.sha256"
)
printf '%s\n' "$archive"
