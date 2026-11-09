#!/usr/bin/env bash
set -euo pipefail

# Run as root from the unpacked ClassForge AWS deployment bundle on Ubuntu 24.04.
if [[ ${EUID} -ne 0 ]]; then
  echo "Run with sudo: sudo bash ./install-origin.sh" >&2
  exit 1
fi

BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

apt-get update
apt-get install -y openjdk-21-jre-headless nginx

if ! id classforge >/dev/null 2>&1; then
  useradd --system --home /opt/classforge --shell /usr/sbin/nologin classforge
fi

install -d -o classforge -g classforge /opt/classforge/app /opt/classforge/data
install -d -o root -g root /var/www/classforge /etc/classforge

install -o classforge -g classforge -m 0644 "$BUNDLE_DIR/classforge.jar" /opt/classforge/app/classforge.jar
rm -rf /var/www/classforge/*
cp -a "$BUNDLE_DIR/frontend/." /var/www/classforge/
chown -R root:root /var/www/classforge

if [[ ! -f /etc/nginx/sites-available/classforge ]]; then
  install -m 0644 "$BUNDLE_DIR/nginx/classforge.conf" /etc/nginx/sites-available/classforge
else
  echo "Preserving existing /etc/nginx/sites-available/classforge (for example, direct HTTPS/TLS configuration)."
fi
ln -sfn /etc/nginx/sites-available/classforge /etc/nginx/sites-enabled/classforge
rm -f /etc/nginx/sites-enabled/default

install -m 0644 "$BUNDLE_DIR/systemd/classforge.service" /etc/systemd/system/classforge.service

if [[ ! -f /etc/classforge/classforge.env ]]; then
  install -m 0600 "$BUNDLE_DIR/classforge.env.example" /etc/classforge/classforge.env
  echo "Created /etc/classforge/classforge.env. Edit CHANGE_ME before starting ClassForge." >&2
else
  chmod 0600 /etc/classforge/classforge.env
fi

nginx -t
systemctl daemon-reload
systemctl enable nginx classforge
systemctl restart nginx

cat <<'MSG'
Origin files installed.

Before starting the application:
  1. Edit /etc/classforge/classforge.env (JWT secret + exact CloudFront origin when known).
  2. Configure CLASSFORGE_MONGODB_URI with a MongoDB replica set (for example Atlas).
     Standalone MongoDB is insufficient for project/audit transactions.
  3. Set GROQ_API_KEY in /etc/classforge/classforge.env (server-side only).
     Text, image analysis and speech all use Groq. No local AI runtime or Bedrock role is needed.
  4. Run: systemctl restart classforge
  5. Check: systemctl status classforge --no-pager
MSG
