# Messenger App - Build Instructions

## Android Studio দিয়ে Build করার পদ্ধতি:

1. **Android Studio** খোলো
2. **File → Open** → এই `MessengerApp` ফোল্ডারটা select করো
3. Gradle sync হওয়া পর্যন্ত অপেক্ষা করো (2-3 মিনিট)
4. উপরে **Build → Build Bundle(s)/APK(s) → Build APK(s)** ক্লিক করো
5. APK তৈরি হলে notification আসবে **"Locate"** ক্লিক করো
   - Path: `app/build/outputs/apk/debug/app-debug.apk`
6. APK ফাইলটা ফোনে transfer করে install করো

## ফোনে Install করতে:
- Settings → Security → **"Unknown Sources"** অন করো
- APK ফাইলে ট্যাপ করো → Install

## Features:
✅ Messenger login
✅ Text messaging
✅ Photo/Video send
✅ Audio Call (WebRTC)
✅ Video Call (WebRTC)
✅ Facebook link BLOCKED
✅ Progress bar
✅ Back navigation

## Notes:
- Notification আসবে না (WebView limitation)
- Background-এ call receive হবে না
- Messenger.com এর সব feature কাজ করবে
