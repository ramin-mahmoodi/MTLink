# MTLink

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Interface](https://img.shields.io/badge/UI-Native%20Views-1F2937?style=flat-square)
![Minimum Android](https://img.shields.io/badge/Android-8%2B-0F766E?style=flat-square)

> **A lightweight, native Android manager for public Telegram proxy sources.**

[فارسی](#فارسی) · [English](#english)

---

<h2 dir="rtl" align="right">فارسی</h2>

<p dir="rtl" align="right"><strong>برنامهٔ MTLink</strong> یک مدیر Native Android برای دریافت، سامان‌دهی و آزمون پراکسی‌های عمومی Telegram است. پروژه با Kotlin و Viewهای استاندارد Android نوشته شده تا مسیر اصلی برنامه، ساده، کم‌وابستگی و قابل نگه‌داری بماند. رابط کاربری Dark Mode است و از فارسی با چیدمان RTL واقعی، در کنار زبان انگلیسی، پشتیبانی می‌کند.</p>

<h3 dir="rtl" align="right">قابلیت‌های اصلی</h3>

<p dir="rtl" align="right"><strong>دریافت.</strong> استخراج پراکسی‌های MTProto و SOCKS5 از منبع‌های Text، JSON و HTML؛ حذف موارد تکراری و ذخیره‌سازی محلی.</p>

<p dir="rtl" align="right"><strong>منابع.</strong> فعال یا غیرفعال‌سازی هر منبع، دریافت تک‌منبع، مشاهدهٔ خطا و خروجی آخر، سقف مستقل برای هر منبع و اعمال سقف یک‌جا برای همهٔ منابع.</p>

<p dir="rtl" align="right"><strong>آزمون.</strong> آزمون مستقیم اتصال، ثبت latency و دریافت کد کشور IP در زمان آزمون.</p>

<p dir="rtl" align="right"><strong>استفاده.</strong> کپی لینک، اشتراک‌گذاری، باز کردن مستقیم در Telegram، QR Code و علاقه‌مندی‌ها.</p>

<p dir="rtl" align="right"><strong>تجربهٔ کاربری.</strong> Dark Mode، فارسی/انگلیسی، RTL، actionهای swipe و پیشرفت درون‌صفحه‌ای برای عملیات طولانی.</p>

<h3 dir="rtl" align="right">دامنه و حریم خصوصی</h3>

<p dir="rtl" align="right">برنامهٔ MTLink ارائه‌دهندهٔ پراکسی نیست و فقط منبع‌های عمومی تعریف‌شده در برنامه را می‌خواند. اطلاعات پراکسی‌ها، وضعیت آزمون و تنظیمات برنامه به‌صورت محلی روی دستگاه نگه‌داری می‌شوند. آزمون دوره‌ای نیز فقط پس از فعال‌سازی صریح کاربر اجرا می‌شود.</p>

---

## English

**MTLink** is a native Android manager for collecting, organizing, and testing public Telegram proxies. It is written with Kotlin and standard Android Views to keep the core experience lightweight, dependency-conscious, and maintainable. The interface uses a dark visual system and supports both English and Persian with true RTL layout.

### Core capabilities

| Area | What it provides |
|---|---|
| Fetching | Extracts **MTProto** and **SOCKS5** proxies from Text, JSON, and HTML sources; de-duplicates and stores results locally. |
| Sources | Per-source enablement, single-source fetches, last result and error visibility, individual source limits, and one-tap limits for every source. |
| Testing | Direct connectivity checks, latency reporting, and IP country-code lookup during testing. |
| Use | Copy, share, open in Telegram, QR codes, and favorites. |
| Experience | Dark mode, English/Persian language support, RTL layout, swipe actions, and in-page progress for longer operations. |

### Scope and privacy

MTLink does not provide proxies. It reads only the public sources configured within the application. Proxy data, test status, and preferences are stored locally on the device. Periodic checks run only after an explicit user opt-in.
