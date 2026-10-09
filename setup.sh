#!/usr/bin/env sh
# One-command NutriTrack setup with Docker (macOS and Linux).
# Creates .env with random secrets if needed, builds and starts the app, then prints its address.
set -eu
cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "Docker with the compose plugin is required: https://docs.docker.com/get-docker/" >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but not running. Start Docker Desktop (or the docker service) and try again." >&2
  exit 1
fi

[ -f .env ] || cp .env.example .env

# Sets KEY to a value only when it is missing, empty or still the example placeholder.
ensure() {
  key=$1; value=$2
  current=$(grep -E "^$key=" .env | tail -n 1 | cut -d= -f2- || true)
  if [ -z "$current" ] || [ "$current" = "change-me" ]; then
    grep -vE "^$key=" .env > .env.tmp || true
    printf '%s=%s\n' "$key" "$value" >> .env.tmp
    mv .env.tmp .env
    echo "Generated $key in .env"
  fi
}
random_alnum() { LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c "$1"; }

ensure DB_PASSWORD "$(random_alnum 24)"
ensure MYSQL_ROOT_PASSWORD "$(random_alnum 24)"
ensure JWT_SECRET "$(head -c 48 /dev/urandom | base64 | tr -d '\n')"
chmod 600 .env

port=$(grep -E '^APP_PORT=' .env | tail -n 1 | cut -d= -f2- || true)
port=${port:-3000}

echo "Building and starting NutriTrack (the first run downloads images and takes a few minutes)..."
docker compose up -d --build

printf "Waiting for the app to be ready"
i=0
until [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/api/nutrients" 2>/dev/null || true)" = "401" ]; do
  i=$((i + 1))
  if [ "$i" -ge 120 ]; then
    echo; echo "The app didn't start. See the logs with: docker compose logs backend" >&2
    exit 1
  fi
  printf "."; sleep 2
done
echo
echo "NutriTrack is running: http://localhost:$port"
echo "Stop it with: docker compose down   (your data is kept in the db-data volume)"
