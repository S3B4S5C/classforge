#!/usr/bin/env bash
set -euo pipefail
for variable in CLASSFORGE_MONGODB_URI CLASSFORGE_JWT_SECRET CLASSFORGE_WEBSOCKET_ALLOWED_ORIGINS GROQ_API_KEY; do
  if [[ -z ${!variable:-} ]]; then
    echo "Required server setting is empty: $variable" >&2
    exit 1
  fi
done
if [[ ${CLASSFORGE_PUSH_ENABLED:-false} == true ]]; then
  for variable in CLASSFORGE_PUSH_VAPID_PUBLIC_KEY CLASSFORGE_PUSH_VAPID_PRIVATE_KEY CLASSFORGE_PUSH_VAPID_SUBJECT; do
    if [[ -z ${!variable:-} ]]; then
      echo "Required push setting is empty: $variable" >&2; exit 1
    fi
  done
fi
if [[ ${#CLASSFORGE_JWT_SECRET} -lt 64 ]]; then
  echo 'CLASSFORGE_JWT_SECRET must contain at least 64 characters.' >&2
  exit 1
fi
if [[ ! $CLASSFORGE_MONGODB_URI =~ ^mongodb(\+srv)?://[^/]+/[^?/]+ ]]; then
  echo 'MongoDB URI must name a database on a replica set.' >&2
  exit 1
fi
if [[ ! $CLASSFORGE_WEBSOCKET_ALLOWED_ORIGINS =~ ^https://[a-zA-Z0-9.-]+$ ]] || [[ $CLASSFORGE_WEBSOCKET_ALLOWED_ORIGINS == *REPLACE* ]]; then
  echo 'Set a single exact public HTTPS origin for this VM.' >&2
  exit 1
fi
