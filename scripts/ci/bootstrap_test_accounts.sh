#!/usr/bin/env bash
set -euo pipefail

: "${BASE_URL:?BASE_URL is required}"
: "${API_TEST_USERNAME:?API_TEST_USERNAME is required}"
: "${API_TEST_PASSWORD:?API_TEST_PASSWORD is required}"
: "${API_TEST_ADMIN_USERNAME:?API_TEST_ADMIN_USERNAME is required}"
: "${API_TEST_ADMIN_PASSWORD:?API_TEST_ADMIN_PASSWORD is required}"
: "${CI_MYSQL_PASSWORD:?CI_MYSQL_PASSWORD is required}"

for username in "$API_TEST_USERNAME" "$API_TEST_ADMIN_USERNAME"; do
  [[ "$username" =~ ^[A-Za-z0-9_]{3,32}$ ]] || {
    echo "CI usernames must match [A-Za-z0-9_]{3,32}" >&2
    exit 1
  }
done

register() {
  local username="$1"
  local password="$2"
  local phone="$3"
  local response
  response=$(curl --fail-with-body --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${username}\",\"password\":\"${password}\",\"nickname\":\"CI测试用户\",\"phone\":\"${phone}\"}" \
    "${BASE_URL}/user/register")
  test "$(printf '%s' "$response" | jq -r '.code')" = "200"
}

register "$API_TEST_USERNAME" "$API_TEST_PASSWORD" "13900000001"
register "$API_TEST_ADMIN_USERNAME" "$API_TEST_ADMIN_PASSWORD" "13900000002"

docker compose -f compose.ci.yml exec -T mysql \
  mysql -uroot -p"$CI_MYSQL_PASSWORD" seckill_mall \
  -e "UPDATE user SET role=1 WHERE username='${API_TEST_ADMIN_USERNAME}';"

echo "CI test accounts created"
