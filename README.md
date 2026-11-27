# Vedyya Drop

A mobile third-person island survival game with solo and two-player cooperative matches. Open index.html in a browser. Squad play needs HTTPS, an internet connection, PeerJS Cloud and a working WebRTC route between devices. Room codes and Find teammate pair two players against ten bots. Use Mic in a connected match for optional voice chat.

Drag the pad to move and swipe the island to rotate the camera. Use Jump to exit the plane early. Auto toggles manual aim. Walk over crates to collect weapons or ammunition. The bag contains sprint, crouch, weapon switching and medkits. Desktop controls include WASD, Space to fire, J to jump, R to reload, Q to switch and B for the bag.

## Cloud accounts

Run `node server.mjs` with Node 22.13+ to serve the game with registration, sign-in and saved profiles. Expose it behind an HTTPS reverse proxy with DROP_HTTPS=1. SQLite storage defaults to vedyya.sqlite. Set DROP_DATABASE to a persistent path. Static hosting and the bundled APK use device-local guest profiles.

## Android

Run `bash android/build.sh` using Android platform 35, build-tools 35.0.0, JDK 17 and zip, or use the APK workflow. Output is dist/Vedyya-Drop.apk. By default each build uses a fresh test key. For install-in-place updates use the same DROP_KEYSTORE, DROP_KEY_ALIAS, DROP_STORE_PASS and DROP_KEY_PASS across builds. The APK bundles solo gameplay, uses Android text-to-speech and requests microphone access only when enabling squad voice.
