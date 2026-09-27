#!/usr/bin/env bash
# Публикация VaVsta Messenger: APK + манифест обновлений на chat.vavsta.ru
#
# Использование:
#   ./publish-ota.sh <путь-к-apk> [заметки]
#
# Что делает:
#   1. достаёт из APK реальный versionCode (не доверяем имени файла)
#   2. считает sha256
#   3. пишет version.json рядом
#   4. заливает оба файла в /var/www/element/vavsta-messenger/ на chat.vavsta.ru
#   5. проверяет, что всё отдаётся по HTTPS и что размеры/хэши совпадают
set -euo pipefail

APK="${1:-}"
NOTES="${2:-}"
HOST="chat.vavsta.ru"
REMOTE_DIR="/var/www/element/vavsta-messenger"
BASE_URL="https://$HOST/vavsta-messenger"
AAPT2="$(ls -d /opt/android-sdk/build-tools/*/aapt2 | tail -1)"

if [[ -z "$APK" || ! -f "$APK" ]]; then
    echo "usage: $0 <path-to-apk> [notes]" >&2
    exit 1
fi

APK="$(readlink -f "$APK")"
PKG="$($AAPT2 dump badging "$APK" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")"
VERSION_CODE="$($AAPT2 dump badging "$APK" | sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p")"
VERSION_NAME="$($AAPT2 dump badging "$APK" | sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p")"
SIZE="$(stat -c%s "$APK")"
SHA="$(sha256sum "$APK" | cut -d' ' -f1)"
OUT_NAME="vavsta-messenger-arm64-${VERSION_CODE}.apk"

if [[ -z "$PKG" || -z "$VERSION_CODE" || -z "$VERSION_NAME" ]]; then
    echo "не удалось распарсить badging из $APK" >&2
    exit 1
fi

echo "package      : $PKG"
echo "version      : $VERSION_NAME (code $VERSION_CODE)"
echo "size         : $SIZE"
echo "sha256       : $SHA"
echo "target       : $BASE_URL/$OUT_NAME"

# манифест: apkUrl абсолютный, иначе телефон не сможет его забрать
python3 - "$OUT_NAME" "$VERSION_CODE" "$VERSION_NAME" "$SIZE" "$SHA" "$NOTES" > version.json <<'PY'
import json, sys
out_name, code, name, size, sha, notes = sys.argv[1:7]
manifest = {
    "versionCode": int(code),
    "versionName": name,
    "apkUrl": f"https://chat.vavsta.ru/vavsta-messenger/{out_name}",
    "sha256": sha,
    "sizeBytes": int(size),
    "publishedAt": __import__("datetime").datetime.now().astimezone().isoformat(timespec="seconds"),
}
if notes:
    manifest["notes"] = notes
print(json.dumps(manifest, ensure_ascii=False, indent=2))
PY

echo "--- манифест ---"
cat version.json
echo "--- заливка ---"
export SSH_AUTH_SOCK="${SSH_AUTH_SOCK:-/tmp/opencode/agent.sock}"
SSH_OPTS=(-o IdentitiesOnly=yes -o BatchMode=yes)
scp "${SSH_OPTS[@]}" "$APK" "root@$HOST:$REMOTE_DIR/$OUT_NAME"
scp "${SSH_OPTS[@]}" version.json "root@$HOST:$REMOTE_DIR/version.json"
ssh "${SSH_OPTS[@]}" "root@$HOST" "chown www-data:www-data '$REMOTE_DIR/$OUT_NAME' '$REMOTE_DIR/version.json'"

echo "--- проверка ---"
REMOTE_SIZE="$(curl -fsSL "$BASE_URL/$OUT_NAME" | wc -c)"
REMOTE_JSON="$(curl -fsSL "$BASE_URL/version.json")"
echo "apk по HTTPS: $REMOTE_SIZE байт (локально $SIZE)"
[[ "$REMOTE_SIZE" == "$SIZE" ]] || { echo "РАЗМЕР НЕ СОВПАЛ" >&2; exit 1; }
echo "$REMOTE_JSON" | python3 -c "
import json, sys
m = json.load(sys.stdin)
print('version.json с сервера:', m['versionCode'], m['versionName'], m['apkUrl'])
assert m['sha256'] == '$SHA', 'sha256 в манифесте не совпадает с залитым файлом'
assert m['apkUrl'].endswith('/$OUT_NAME')
print('sha256 и apkUrl сходятся')
"
echo "ГОТОВО: $BASE_URL/version.json"
