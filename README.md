# X Controller — Universal Keyboard & Mouse Gaming App

Mobile games ko **keyboard aur mouse** se khelne ke liye Android app.

**Direct APK:** https://github.com/rewpal5-dotcom/X-Controller/releases/latest/download/app-debug.apk

## Kya kaam karta hai (v1.0 prototype)

- **Device detection** — keyboard aur mouse connect hone par home par ✅ dikhta hai
- **My Games** — har game ke liye alag profile (keys, positions, sensitivity)
- **Button editor** — tap se naya button, drag se jagah, long-press se delete
- **Floating controller** — game ke upar panel: ✥ se hilayein, KEY ON/OFF, ✕ band
- **Touch mapping engine** — Accessibility service se mapped point par tap gesture
- **Profiles save** — SharedPreferences mein (bina internet)

## Kaise chalayein

1. OTG/Bluetooth se keyboard-mouse jodein
2. App kholen → dono permissions dein (Floating panel + Accessibility)
3. My Games → profile → keys set karein → Save
4. Start Controller → game kholein

## Imaandari se seemayein

Har game mein ye kaam nahi karega. Android doosre app ke andar keyboard input
poori tarah nahi dene deta, aur kuch games ke anti-cheat bahari control block
karte hain. Ye app sirf un games ke liye hai jahan Android aur game ki policy
ijazat deti hai. Kisi bhi game ki policy/ban ki zimmedari user ki hai.

## Tech

Java · Accessibility Service · Overlay (SYSTEM_ALERT_WINDOW) · SharedPreferences · minSdk 24

## License

MIT
