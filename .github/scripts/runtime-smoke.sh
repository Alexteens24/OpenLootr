#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: runtime-smoke.sh <minecraft-version> <OpenLootr.jar>" >&2
  exit 2
fi

version="$1"
plugin_jar="$2"
user_agent='OpenLootr-CI/1.0 (https://github.com/Alexteens24/OpenLootr)'
work_dir="$(mktemp -d "${RUNNER_TEMP:-/tmp}/openlootr-${version}-XXXXXX")"
server_pid=''

cleanup() {
  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    kill "$server_pid" 2>/dev/null || true
  fi
}
trap cleanup EXIT

mkdir -p "$work_dir/plugins"
cp "$plugin_jar" "$work_dir/plugins/OpenLootr.jar"
plugin_sha="$(sha256sum "$work_dir/plugins/OpenLootr.jar" | cut -d' ' -f1)"

builds_json="$work_dir/builds.json"
curl --fail --silent --show-error \
  -H "User-Agent: $user_agent" \
  -o "$builds_json" \
  "https://fill.papermc.io/v3/projects/paper/versions/${version}/builds"

download_url="$(jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".url) // empty' "$builds_json")"
server_sha="$(jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".checksums.sha256) // empty' "$builds_json")"
if [[ -z "$download_url" || -z "$server_sha" ]]; then
  echo "No stable Paper build is available for $version" >&2
  exit 1
fi

curl --fail --silent --show-error \
  -H "User-Agent: $user_agent" \
  -o "$work_dir/paper.jar" \
  "$download_url"
echo "$server_sha  $work_dir/paper.jar" | sha256sum --check --status

printf 'eula=true\n' > "$work_dir/eula.txt"
printf 'online-mode=false\nserver-port=0\nmotd=OpenLootr CI\n' > "$work_dir/server.properties"
mkfifo "$work_dir/console.pipe"

(
  cd "$work_dir"
  timeout --signal=TERM 240s java -jar paper.jar --nogui < console.pipe > server.log 2>&1
) &
server_pid="$!"
exec 3>"$work_dir/console.pipe"

ready=false
for _ in $(seq 1 180); do
  if grep -Fq 'Done (' "$work_dir/server.log"; then
    ready=true
    break
  fi
  if ! kill -0 "$server_pid" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [[ "$ready" != true ]]; then
  cat "$work_dir/server.log"
  echo "Paper $version did not become ready" >&2
  exit 1
fi

printf 'openlootr debug\n' >&3
linked=false
for _ in $(seq 1 20); do
  if grep -Fq "nms=linked:${version}/" "$work_dir/server.log"; then
    linked=true
    break
  fi
  sleep 1
done
printf 'stop\n' >&3
exec 3>&-
wait "$server_pid"
server_pid=''

if [[ "$linked" != true ]]; then
  cat "$work_dir/server.log"
  echo "OpenLootr did not report linked NMS for $version" >&2
  exit 1
fi
if grep -Fq 'Failed to initialize OpenLootr' "$work_dir/server.log"; then
  cat "$work_dir/server.log"
  exit 1
fi
grep -Fq 'WAL=wal, sync=FULL' "$work_dir/server.log"

echo "Paper $version smoke passed with OpenLootr SHA-256 $plugin_sha"
