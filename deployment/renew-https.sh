#!/usr/bin/env bash
set -euo pipefail

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

[[ $EUID -eq 0 ]] || fail 'Run this on the Droplet as root.'

domain=managefiles.duckdns.org
cert_dir=/etc/letsencrypt
certificate="$cert_dir/live/$domain/fullchain.pem"
renewal_config="$cert_dir/renewal/$domain.conf"

cd -- "${1:-/opt/file-system}"
compose=(docker compose -f docker-compose.yml -f docker-compose.digitalocean.yml)
frontend=$("${compose[@]}" ps --status running -q frontend)
[[ -n "$frontend" && "$frontend" != *$'\n'* ]] || fail 'Expected one running frontend in this Compose project.'

if ! openssl x509 -in "$certificate" -noout -checkend 0 >/dev/null 2>&1; then
    command -v certbot >/dev/null || fail 'Certbot is missing on the Droplet.'
    [[ -r "$renewal_config" ]] || fail "Renewal configuration missing. Run certbot certificates to check the name and certificate path."

    authenticator=$(awk -F= '$1 ~ /^[[:space:]]*authenticator[[:space:]]*$/ {
        gsub(/[[:space:]]/, "", $2)
        print $2
        exit
    }' "$renewal_config")
    [[ -n "$authenticator" ]] || fail 'Could not determine the existing Certbot authenticator.'

    if [[ "$authenticator" == standalone ]]; then
        restore_frontend() {
            local status=$?
            trap - EXIT
            docker start "$frontend" >/dev/null || status=1
            exit "$status"
        }

        printf 'Pausing the frontend briefly so Certbot can listen on port 80.\n'
        trap restore_frontend EXIT
        trap 'exit 130' INT
        trap 'exit 143' TERM
        docker stop "$frontend" >/dev/null
        certbot renew --cert-name "$domain" --non-interactive
        docker start "$frontend" >/dev/null
        trap - EXIT INT TERM
    else
        if [[ "$authenticator" == webroot ]]; then
            mount_targets=$(docker inspect --format '{{range .Mounts}}{{println .Destination}}{{end}}' "$frontend")
            grep -Fxq /var/www/certbot <<< "$mount_targets" ||
                fail 'Certbot uses webroot, but the frontend has no /var/www/certbot mount for the ACME challenge.'
        fi
        certbot renew --cert-name "$domain" --non-interactive
    fi
fi

openssl x509 -in "$certificate" -noout -checkend 0 -enddate
docker exec "$frontend" nginx -t
docker exec "$frontend" nginx -s reload
curl --fail --silent --show-error --output /dev/null \
    --retry 5 --retry-delay 1 --retry-connrefused --connect-timeout 3 --max-time 8 \
    --resolve "$domain:443:127.0.0.1" "https://$domain/"
printf 'HTTPS passed certificate and response checks on the Droplet.\n'
