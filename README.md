# X Controller — Universal Keyboard & Mouse Gaming App

Android app jo mobile games ko **keyboard aur mouse** se khelne ki koshish karta hai —
jahan Android aur game ki policy ijazat deti hai.

**Direct APK:** https://github.com/rewpal5-dotcom/X-Controller/releases/latest/download/app-debug.apk

## v2.0 mein kya hai (sab real, koi dummy button nahi)

| Screen | Kaam |
|---|---|
| **Home** | Keyboard / Mouse / Service / Accessibility / Overlay permission — live status |
| **My Games** | Installed games ki list + har game ka apna profile (banayein / use / delete) |
| **Keyboard & Mouse** | Asli test screen: key dabao → naam dikhta hai; mouse move/click/scroll detect |
| **Custom Controls** | Key → game action mapping: add / change / remove / reset / export / import (clipboard JSON) |
| **Controller Profiles** | Layout editor: drag, size (+/−), long-press delete, **Test mode**, save |
| **Settings** | Mouse sensitivity, aim sensitivity, pointer speed, invert Y, button size/opacity, default profile, reset app |
| **Help & Support** | Step-by-step guide |
| **About App** | Privacy policy, terms, compatibility report |
| **Floating panel** | Game ke upar: ✥ drag · KEY ON/OFF · ⏸ pause · ✎ edit · ✕ close |

## Kaam kaise karta hai

```
Keyboard key ──► Overlay panel (focusable) ──► profile lookup ──► Accessibility tap at mapped point
Mouse click  ──► Overlay panel ──► same mapping
```

## Imaandari se — kya nahi hota

- **Har game mein nahi chalega.** Android doosre app ke andar keyboard input nahi deta;
  mapping Accessibility gesture se hoti hai, jo kuch games block karte hain.
- **Anti-cheat wale games** (Free Fire, BGMI jaise) — bahari mapping block ho sakti hai.
  Ban ka risk user ka hai. App kisi anti-cheat ko bypass nahi karta.
- **Mouse se camera ghumana** sirf un games mein jahan native mouse support ho.
- Jo cheez Android support nahi karta, app use "unsupported" batata hai — nakli success nahi.

## Storage

Profiles, layouts, settings — sab phone ki local storage mein JSON (SharedPreferences).
Koi internet, koi server, koi data collection nahi. (Room database ke bajaye
dependency-free JSON store rakha gaya hai taaki APK halka aur build simple rahe.)

## Build

```bash
./gradlew assembleDebug
```

## License

MIT
