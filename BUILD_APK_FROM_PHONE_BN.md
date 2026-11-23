# ফোন দিয়ে OBITO APK বানানোর সহজ উপায় (GitHub Actions)

> এই প্রকল্পের ZIP-এ Android source code আছে; APK এখনো তৈরি/পরীক্ষিত নয়। Cloud build চালাতে GitHub account এবং ইন্টারনেট লাগবে।

## ধাপ
1. ZIP ফাইল Extract করুন। ভিতরে `AutoEnhanceCamera` ফোল্ডার খুলুন।
2. GitHub-এ নতুন repository তৈরি করুন (উদাহরণ: `OBITO-Camera`)। চাইলে Private রাখুন।
3. Repository-র `Add file` → `Upload files` থেকে `AutoEnhanceCamera` ফোল্ডারের **ভিতরের সব ফাইল ও ফোল্ডার** upload করুন। `.github` ফোল্ডারটিও upload নিশ্চিত করুন। Root-এ `app`, `build.gradle.kts`, `settings.gradle.kts`, `.github` দেখা উচিত।
4. Commit changes করুন, branch নাম `main` রাখুন।
5. Repository → **Actions** → **Build OBITO APK** workflow → **Run workflow** → সবুজ Run workflow চাপুন। প্রথম push-এ workflow নিজেও চলতে পারে।
6. Build শেষ হলে (সবুজ ✓) run-এ ঢুকে নিচে **Artifacts**-এ `OBITO-debug-apk` ZIP download করুন।
7. Artifact ZIP extract করে `app-debug.apk`-ийг Android-এ ডাউনলোড করুন। ফাইল চাপুন → Install. যদি permission চায়, Android Settings-এ ওই file manager/browser-এর **Install unknown apps** অনুমতি দিন; ইনস্টল শেষে অনুমতি বন্ধ করে দিতে পারেন।

## সমস্যা হলে
- লাল ❌ build run হলে Logs খুলে error দেখুন; Android SDK/Gradle dependencies online resolve হয়।
- APK ইনস্টল blocked হলে Settings → Security/Apps → Install unknown apps থেকে ব্যবহৃত app অনুমতি দিন।
- এটি debug APK, Play Store release নয়।
- AI face restoration-এর জন্য compatible model weights অন্তর্ভুক্ত নেই; model ছাড়া app conventional enhancement fallback করবে।
