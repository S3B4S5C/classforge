#!/usr/bin/env bash
set -euo pipefail
if [[ ${EUID} -ne 0 ]]; then echo 'Run with sudo enable-https.sh HOSTNAME EMAIL' >&2; exit 1; fi
hostname=${1:-}
hostname=${hostname,,}
email=${2:-}
if [[ ! $hostname =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$ ]] || [[ $hostname != *.* ]]; then
  echo 'Invalid hostname.' >&2; exit 1
fi
if [[ ! $email =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
  echo 'Pass your certificate contact email.' >&2; exit 1
fi
certbot certonly --webroot --webroot-path /var/lib/letsencrypt \
  --domain "$hostname" --email "$email" --agree-tos --non-interactive --keep-until-expiring
site_tmp=$(mktemp /etc/nginx/sites-available/classforge.XXXXXX)
trap 'rm -f "$site_tmp"' EXIT
sed "s/__HOSTNAME__/$hostname/g" /opt/classforge/bin/nginx-https.conf > "$site_tmp"
if [[ -f /etc/nginx/sites-available/classforge ]]; then
  cp -p /etc/nginx/sites-available/classforge /etc/nginx/sites-available/classforge.before-tls
fi
install -m 0644 "$site_tmp" /etc/nginx/sites-available/classforge
if ! nginx -t; then
  if [[ -f /etc/nginx/sites-available/classforge.before-tls ]]; then
    cp -p /etc/nginx/sites-available/classforge.before-tls /etc/nginx/sites-available/classforge
  fi
  echo 'nginx validation failed; prior configuration restored.' >&2; exit 1
fi
install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\n/usr/sbin/nginx -t && /usr/bin/systemctl reload nginx\n' > /etc/letsencrypt/renewal-hooks/deploy/classforge-nginx
chmod 0755 /etc/letsencrypt/renewal-hooks/deploy/classforge-nginx
systemctl enable --now certbot.timer
systemctl reload nginx
echo 'HTTPS/WSS configured. Run certbot renew --dry-run, then start ClassForge.'
