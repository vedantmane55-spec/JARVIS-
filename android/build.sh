#!/usr/bin/env bash
set -euo pipefail
sdk="${ANDROID_HOME:?Install Android SDK platform 35 and build-tools 35.0.0}"
bt="$sdk/build-tools/35.0.0"
platform="$sdk/platforms/android-35/android.jar"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
mkdir -p "$out/assets" "$out/classes" "$out/dex" dist
# Android 10 can ship an older Chromium engine. Downlevel syntax for the bundled game.
python3 - "$out" <<'PY'
from pathlib import Path
import sys
out=Path(sys.argv[1])
prefix,tail=Path('index.html').read_text().split('<script>',1)
script,suffix=tail.split('</script>',1)
(out/'source.js').write_text(script)
(out/'assets/index.html').write_text(prefix+'<script>\n__DROP_SCRIPT__\n</script>'+suffix)
PY
npx --yes --package=esbuild@0.25.12 esbuild "$out/source.js" --target=chrome70 --outfile="$out/legacy.js"
python3 - "$out" <<'PY'
from pathlib import Path
import sys
out=Path(sys.argv[1])
page=out/'assets/index.html'
compat="if(!Element.prototype.replaceChildren){Element.prototype.replaceChildren=function(){while(this.firstChild)this.removeChild(this.firstChild);this.append.apply(this,arguments)}};\n"
page.write_text(page.read_text().replace('__DROP_SCRIPT__',compat+(out/'legacy.js').read_text()))
PY
"$bt/aapt2" link -I "$platform" --manifest android/AndroidManifest.xml -A "$out/assets" -o "$out/base.apk"
javac -source 8 -target 8 -classpath "$platform" -d "$out/classes" android/DropActivity.java
jar cf "$out/game.jar" -C "$out/classes" .
"$bt/d8" --lib "$platform" --min-api 24 --output "$out/dex" "$out/game.jar"
(cd "$out/dex" && zip -q "$out/base.apk" classes.dex)
"$bt/zipalign" -f 4 "$out/base.apk" "$out/aligned.apk"
keyStore="${DROP_KEYSTORE:-$out/test.jks}"
if [[ -z "${DROP_KEYSTORE:-}" ]]; then
  keytool -genkeypair -keystore "$keyStore" -storepass android -keypass android -alias drop -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=Vedyya Drop Test"
  export DROP_STORE_PASS=android DROP_KEY_PASS=android
fi
"$bt/apksigner" sign --ks "$keyStore" --ks-key-alias "${DROP_KEY_ALIAS:-drop}" --ks-pass env:DROP_STORE_PASS --key-pass env:DROP_KEY_PASS --out dist/Vedyya-Drop.apk "$out/aligned.apk"
"$bt/apksigner" verify --verbose dist/Vedyya-Drop.apk
