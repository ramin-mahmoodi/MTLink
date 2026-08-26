# MTLink

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Interface](https://img.shields.io/badge/UI-Native%20Views-1F2937?style=flat-square)
![Minimum Android](https://img.shields.io/badge/Android-8%2B-0F766E?style=flat-square)

> **A lightweight, native Android manager for public Telegram proxy sources.**

[فارسی](#فارسی) · [English](#english)

---

<h2 dir="rtl" align="right">فارسی</h2>

<p dir="rtl" align="right">
<strong>MTLink</strong> یک اپ اندرویدی بومی برای جمع‌آوری، مرتب‌سازی و آزمایش پراکسی‌های عمومی تلگرام است. با <bdi>Kotlin</bdi> و Viewهای استاندارد اندروید نوشته شده، بدون وابستگی اضافه و با تمرکز روی سادگی و نگهداری‌پذیری کد. رابط کاربری تیره است و از فارسی (با چیدمان درست RTL) و انگلیسی پشتیبانی می‌کند.
</p>

<h3 dir="rtl" align="right">قابلیت‌های اصلی</h3>

<div dir="rtl" align="right">

- **دریافت پراکسی:** استخراج پراکسی‌های <bdi>MTProto</bdi> و <bdi>SOCKS5</bdi> از منابع متنی، <bdi>JSON</bdi> و <bdi>HTML</bdi>، همراه با حذف خودکار موارد تکراری و ذخیره‌سازی محلی.
- **مدیریت منابع:** فعال یا غیرفعال کردن هر منبع به‌طور جداگانه، دریافت تکی از هر منبع، مشاهده‌ی آخرین نتیجه و خطای هر منبع، و تعیین سقف مجزا برای هر منبع یا یک سقف مشترک برای همه.
- **آزمایش اتصال:** تست مستقیم هر پراکسی، ثبت تأخیر (<bdi>latency</bdi>) و تشخیص کد کشور IP در لحظه‌ی آزمایش.
- **استفاده‌ی روزمره:** کپی لینک، اشتراک‌گذاری، باز کردن مستقیم در تلگرام، ساخت <bdi>QR Code</bdi> و افزودن به علاقه‌مندی‌ها.
- **تجربه‌ی کاربری:** حالت تیره، پشتیبانی همزمان از فارسی و انگلیسی با RTL واقعی، اکشن‌های swipe، و نوار پیشرفت درون‌صفحه برای عملیات‌های طولانی.

</div>

<h3 dir="rtl" align="right">دامنه و حریم خصوصی</h3>

<p dir="rtl" align="right">
MTLink خودش هیچ پراکسی‌ای تولید نمی‌کند؛ فقط منابع عمومی‌ای را می‌خواند که در برنامه تعریف شده‌اند. اطلاعات پراکسی‌ها، وضعیت آزمایش‌ها و تنظیمات برنامه فقط روی خود دستگاه ذخیره می‌شوند. آزمایش دوره‌ای هم تنها زمانی اجرا می‌شود که کاربر آن را صریحاً فعال کرده باشد.
</p>

---

## English

**MTLink** is a native Android app for collecting, organizing, and testing public Telegram proxies. It's built with Kotlin and plain Android Views — no extra dependencies, just a simple, maintainable codebase. The UI uses a dark theme and works in both English and Persian, with proper RTL layout.

### Core features

- **Fetching:** Pulls MTProto and SOCKS5 proxies from Text, JSON, and HTML sources, removes duplicates, and stores everything locally.
- **Source management:** Enable or disable each source individually, fetch from a single source on demand, see the last result and error for each one, and set per-source limits or one shared limit for all sources at once.
- **Connection testing:** Tests each proxy directly, logs latency, and looks up the IP's country code while testing.
- **Everyday use:** Copy links, share them, open directly in Telegram, generate QR codes, and save favorites.
- **Experience:** Dark mode, English/Persian support with real RTL, swipe actions, and inline progress for longer operations.

### Scope and privacy

MTLink doesn't provide any proxies itself — it only reads the public sources configured in the app. Proxy data, test results, and settings are stored locally on your device. Periodic checks only run if you've explicitly turned them on.
