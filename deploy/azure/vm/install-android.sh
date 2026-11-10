#!/usr/bin/env bash
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo.' >&2; exit 1; }
[[ $# -eq 1 && -d $1 ]] || { echo 'Usage: install-android.sh STAGING_DIRECTORY' >&2; exit 1; }
stage=$(cd -- "$1" && pwd)
config=/etc/nginx/sites-available/classforge
snippet=/etc/nginx/snippets/classforge-android.conf
root=/var/lib/classforge/android
[[ -f $config ]] || { echo 'Existing ClassForge nginx configuration required.' >&2; exit 1; }
for file in classforge.apk classforge.apk.sha256 assetlinks.json release.json; do
  [[ -f "$stage/$file" && ! -L "$stage/$file" ]] || { echo "Missing release file: $file" >&2; exit 1; }
done
checksum=$(python3 - "$stage" <<'PY'
import hashlib,json,pathlib,re,sys
p=pathlib.Path(sys.argv[1])
m=json.loads((p/'release.json').read_text())
assert m['origin']=='https://classforge-tiago-15124.eastus.cloudapp.azure.com', 'Wrong origin'
assert m['packageName']=='com.classforge.app', 'Wrong package'
h=hashlib.sha256((p/'classforge.apk').read_bytes()).hexdigest()
assert m['apkSha256']==h, 'APK checksum mismatch'
assert (p/'classforge.apk.sha256').read_text().strip()==h+'  classforge.apk', 'Checksum file mismatch'
f=m['signingCertificateSha256'].replace(':','').upper()
assert re.fullmatch('[A-F0-9]{64}',f), 'Invalid signer'
f=':'.join(f[i:i+2] for i in range(0,64,2))
assert json.loads((p/'assetlinks.json').read_text())==[{'relation':['delegate_permission/common.handle_all_urls'],'target':{'namespace':'android_app','package_name':'com.classforge.app','sha256_cert_fingerprints':[f]}}], 'DAL mismatch'
print(h)
PY
)
[[ $checksum =~ ^[a-f0-9]{64}$ ]] || exit 1
release="$root/releases/$checksum"
install -d -m 0755 "$release" /etc/nginx/snippets
for file in classforge.apk classforge.apk.sha256 assetlinks.json release.json; do
  install -o root -g root -m 0644 "$stage/$file" "$release/$file"
done
old_target=$(readlink "$root/current" || true)
[[ ! -e "$root/current" || -L "$root/current" ]] || { echo 'Current release must be a symlink.' >&2; exit 1; }
backup=$(mktemp /etc/nginx/classforge.android-backup.XXXXXX)
cp -p "$config" "$backup"
snippet_backup=''
if [[ -f $snippet ]]; then
  snippet_backup=$(mktemp /etc/nginx/classforge.android-snippet-backup.XXXXXX)
  cp -p "$snippet" "$snippet_backup"
fi
rollback() {
  cp -p "$backup" "$config"
  if [[ -n $snippet_backup ]]; then cp -p "$snippet_backup" "$snippet"; else rm -f -- "$snippet"; fi
  if [[ -n $old_target ]]; then ln -sfn "$old_target" "$root/current"; else rm -f -- "$root/current"; fi
  echo 'Android publication failed; previous nginx configuration/release restored.' >&2
}
trap rollback ERR
cat > "$snippet" <<'NGINX'
location = /.well-known/assetlinks.json {
    alias /var/lib/classforge/android/current/assetlinks.json;
    default_type application/json;
    add_header Cache-Control "public, max-age=300";
}
location = /android/classforge.apk {
    alias /var/lib/classforge/android/current/classforge.apk;
    default_type application/vnd.android.package-archive;
    add_header Content-Disposition 'attachment; filename="classforge.apk"';
    add_header Cache-Control "no-cache";
}
location = /android/classforge.apk.sha256 {
    alias /var/lib/classforge/android/current/classforge.apk.sha256;
    default_type text/plain;
    add_header Cache-Control "no-cache";
}
location = /android/release.json {
    alias /var/lib/classforge/android/current/release.json;
    default_type application/json;
    add_header Cache-Control "no-cache";
}
NGINX
chmod 0644 "$snippet"
python3 - "$config" <<'PY'
import pathlib,sys
p=pathlib.Path(sys.argv[1]); text=p.read_text()
include='include /etc/nginx/snippets/classforge-android*.conf;'
if include not in text:
    marker='listen 443 ssl;'
    assert text.count(marker)==1, 'Expected exactly one ClassForge TLS server'
    p.write_text(text.replace(marker,marker+'\n    '+include))
PY
ln -sfn "$release" "$root/current.next"
mv -Tf "$root/current.next" "$root/current"
nginx -t
systemctl reload nginx
trap - ERR
echo "Published Android APK and verified association: $checksum"
