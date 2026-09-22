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

install -d -o classforge -g classforge /opt/classforge/app /opt/classforge/data /opt/classforge/whisper/models
install -d -o root -g root /var/www/classforge /etc/classforge

install -o classforge -g classforge -m 0644 "$BUNDLE_DIR/classforge.jar" /opt/classforge/app/classforge.jar
rm -rf /var/www/classforge/*
cp -a "$BUNDLE_DIR/frontend/." /var/www/classforge/
chown -R root:root /var/www/classforge

install -m 0644 "$BUNDLE_DIR/nginx/classforge.conf" /etc/nginx/sites-available/classforge
ln -sfn /etc/nginx/sites-available/classforge /etc/nginx/sites-enabled/classforge
rm -f /etc/nginx/sites-enabled/default

install -m 0644 "$BUNDLE_DIR/systemd/classforge.service" /etc/systemd/system/classforge.service
install -m 0644 "$BUNDLE_DIR/systemd/classforge-whisper.service" /etc/systemd/system/classforge-whisper.service

if [[ ! -f /etc/classforge/classforge.env ]]; then
  install -m 0600 "$BUNDLE_DIR/classforge.env.example" /etc/classforge/classforge.env
  echo "Created /etc/classforge/classforge.env. Edit CHANGE_ME before starting ClassForge." >&2
else
  chmod 0600 /etc/classforge/classforge.env
fi

nginx -t
systemctl daemon-reload
systemctl enable nginx classforge classforge-whisper
systemctl restart nginx

cat <<'MSG'
Origin files installed.

Before starting the application:
  1. Edit /etc/classforge/classforge.env (JWT secret + exact CloudFront origin when known).
  2. Install Whisper reproducibly: sudo bash ./install-whisper.sh
     (or place a known-good whisper-server + ggml-base.bin in /opt/classforge/whisper).
  3. Attach the EC2 IAM role that can invoke Bedrock.
  4. Run: systemctl restart classforge-whisper classforge
  5. Check: systemctl status classforge-whisper classforge --no-pager
MSG
