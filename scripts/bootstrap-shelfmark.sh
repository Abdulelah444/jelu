#!/usr/bin/env bash
#
# One-time bootstrap for the Shelfmark downloader that ships alongside Jelu.
#
# A brand-new Shelfmark container starts with no mirror URLs and default DNS,
# so searches return nothing until it is configured. This script pushes a
# known-good starting config (mirror order + DNS provider) through Shelfmark's
# settings API — the same endpoint Jelu's Preferences > Downloads uses.
#
# Idempotent: safe to run again. Afterwards, change things from
# Jelu > Preferences > Downloads (Shelfmark) and hit "Apply to Shelfmark".
#
# Usage:
#   ./scripts/bootstrap-shelfmark.sh
#
# Environment:
#   SHELFMARK_URL      Base URL of Shelfmark   (default: http://localhost:8084)
#   SHELFMARK_API_KEY  Optional admin key if Shelfmark has auth enabled
#   AA_MIRRORS         Comma-separated Anna's Archive mirrors, first = tried first
#                      (default: https://annas-archive.gd,https://annas-archive.org)
#   LIBGEN_MIRRORS     Comma-separated LibGen mirrors
#                      (default: https://libgen.is)
#   SHELFMARK_DNS      DNS provider: auto|system|google|cloudflare|quad9|opendns|manual
#                      (default: cloudflare)
#   SHELFMARK_DNS_SERVERS  Used only when SHELFMARK_DNS=manual (e.g. "1.1.1.1, 8.8.8.8")

set -euo pipefail

SHELFMARK_URL="${SHELFMARK_URL:-http://localhost:8084}"
SHELFMARK_URL="${SHELFMARK_URL%/}"

AA_MIRRORS="${AA_MIRRORS:-https://annas-archive.gd,https://annas-archive.org}"
LIBGEN_MIRRORS="${LIBGEN_MIRRORS:-https://libgen.is}"
SHELFMARK_DNS="${SHELFMARK_DNS:-cloudflare}"
SHELFMARK_DNS_SERVERS="${SHELFMARK_DNS_SERVERS:-}"

# "a, b , c" -> JSON array ["a","b","c"]
json_list() {
    local IFS=','
    local out="" item
    for item in $1; do
        item="$(echo "$item" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
        [ -z "$item" ] && continue
        [ -n "$out" ] && out="$out,"
        out="$out\"$item\""
    done
    printf '[%s]' "$out"
}

echo "Waiting for Shelfmark at $SHELFMARK_URL ..."
for i in $(seq 1 60); do
    if curl -fsS -m 3 -o /dev/null "$SHELFMARK_URL/api/health" 2>/dev/null; then
        echo "Shelfmark is up."
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "error: Shelfmark did not become reachable at $SHELFMARK_URL" >&2
        echo "       Is the stack running? Try: docker compose up -d" >&2
        exit 1
    fi
    sleep 2
done

auth_headers=()
if [ -n "${SHELFMARK_API_KEY:-}" ]; then
    auth_headers=(-H "Authorization: Bearer ${SHELFMARK_API_KEY}")
fi

push_tab() {
    local tab="$1" payload="$2" response
    echo "Applying $tab ..."
    response=$(curl -fsS -m 30 -X PUT "$SHELFMARK_URL/api/settings/$tab" \
        -H "Content-Type: application/json" \
        ${auth_headers[@]+"${auth_headers[@]}"} \
        -d "$payload") || {
        echo "error: failed to apply '$tab' settings" >&2
        exit 1
    }
    echo "  $response"
}

mirrors_json=$(printf '{"AA_MIRROR_URLS":%s,"LIBGEN_MIRROR_URLS":%s}' \
    "$(json_list "$AA_MIRRORS")" "$(json_list "$LIBGEN_MIRRORS")")

dns_servers_json=$(json_list "$SHELFMARK_DNS_SERVERS")
network_json=$(printf '{"CUSTOM_DNS":"%s","CUSTOM_DNS_MANUAL":%s,"USE_DOH":true}' \
    "$SHELFMARK_DNS" "$dns_servers_json")

push_tab mirrors "$mirrors_json"
push_tab network "$network_json"

echo
echo "Done. Mirrors and network settings are seeded."
echo "From now on, change them in Jelu: Settings > Preferences > Downloads (Shelfmark)."
