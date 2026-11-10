#!/usr/bin/env bash
set -euo pipefail
if [[ ${EUID} -ne 0 ]]; then echo 'Run with sudo bash install.sh HOSTNAME' >&2; exit 1; fi
hostname=${1:-}
hostname=${hostname,,}
if [[ ! $hostname =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$ ]] || [[ $hostname != *.* ]]; then
  echo 'Pass the public DNS hostname, without https:// or a path.' >&2; exit 1
fi
bundle_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd -- "$bundle_dir"
sha256sum --check app.jar.sha256

apt-get update
apt-get install -y openjdk-21-jre-headless nginx certbot python3-certbot-nginx curl ca-certificates
if ! id classforge >/dev/null 2>&1; then
  useradd --system --home-dir /opt/classforge --shell /usr/sbin/nologin classforge
fi
install -d -m 0755 /opt/classforge/releases /opt/classforge/bin /etc/classforge
install -d -o classforge -g classforge -m 0750 /opt/classforge/data
install -d -m 0755 /var/lib/letsencrypt/.well-known/acme-challenge
checksum=$(sha256sum app.jar | cut -d' ' -f1)
release_dir="/opt/classforge/releases/$checksum"
install -d -m 0755 "$release_dir"
install -o root -g root -m 0644 app.jar "$release_dir/app.jar"
if [[ -e /opt/classforge/current && ! -L /opt/classforge/current ]]; then
  echo '/opt/classforge/current is not a symlink; refusing to replace it.' >&2; exit 1
fi
ln -sfn "$release_dir" /opt/classforge/current.next
mv -Tf /opt/classforge/current.next /opt/classforge/current
install -m 0755 check-environment.sh /opt/classforge/bin/check-environment.sh
install -m 0755 enable-https.sh /opt/classforge/bin/enable-https.sh
install -m 0644 nginx-https.conf /opt/classforge/bin/nginx-https.conf
install -m 0644 classforge.service /etc/systemd/system/classforge.service
if [[ ! -f /etc/classforge/classforge.env ]]; then
  install -o root -g root -m 0600 classforge.env.example /etc/classforge/classforge.env
fi
chown root:root /etc/classforge/classforge.env
chmod 0600 /etc/classforge/classforge.env
if [[ ! -e /etc/nginx/sites-available/classforge ]]; then
  sed "s/__HOSTNAME__/$hostname/g" nginx-bootstrap.conf > /etc/nginx/sites-available/classforge
fi
ln -sfn /etc/nginx/sites-available/classforge /etc/nginx/sites-enabled/classforge
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl daemon-reload
systemctl enable nginx classforge
systemctl restart nginx
echo 'Installed without starting ClassForge; existing private settings/TLS were preserved.'
echo '1. Edit /etc/classforge/classforge.env (Mongo replica set, JWT, exact HTTPS origin, Groq).'
echo "2. sudo /opt/classforge/bin/enable-https.sh $hostname YOUR_EMAIL"
echo '3. sudo systemctl restart classforge'
echo '4. Inspect systemctl status classforge and run the HTTPS smoke from your computer.'
