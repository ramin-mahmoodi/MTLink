<div align="center">

<img src="app/src/main/res/drawable-nodpi/mtlink_mascot.png" width="96" alt="MTLink Logo" />

# MTLink

**Collect, test and manage Telegram proxies — offline, entirely on your device**

[![Android](https://img.shields.io/badge/Platform-Android%2024%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue)](#-license)
[![Version](https://img.shields.io/badge/Version-1.3.12-orange)](https://github.com/ramin-mahmoodi/MTLink/releases)

[**English**](#-english) · [**فارسی**](#-فارسی)

</div>

---

## 🇬🇧 English

### What is MTLink?

MTLink is a native Android application for collecting, testing, and managing public Telegram proxies — **entirely on-device, with no intermediary server**.

Built with pure Kotlin and standard Android Views. No Jetpack Compose, no Fragments, no heavy dependencies.

---

### ✨ Features

| Feature | Description |
|---------|-------------|
| 🔗 **Proxy Fetching** | Reads MTProto & SOCKS5 proxies from public sources |
| ⚡ **Latency Testing** | Measures connection latency via direct TCP/SOCKS5 handshake |
| ⭐ **Favorites** | Mark frequently-used proxies for quick access |
| 🌍 **Country Detection** | Shows server country via IP lookup |
| 📋 **Copy & Share** | Copy proxy link or share directly to other apps |
| 📷 **QR Code** | Display QR code for quick scanning |
| 🔄 **Periodic Testing** | Schedule automatic background proxy tests |
| ➕ **Manual Add** | Add proxies via `tg://proxy` or `https://t.me/proxy` links |
| 📂 **Source Management** | Add, edit, and disable fetch sources |
| 🌙 **Dark Theme** | Fully dark UI |
| 🔤 **Bilingual** | Persian (RTL) and English support |

---

### 🏗️ Project Architecture

```
app/src/main/java/ir/mtlink/client/
│
├── MainActivity.kt          # Main Activity — full tab-based UI
├── Models.kt                # Data models: ProxyRecord, SourceDefinition, ...
├── MTLinkStore.kt           # On-device storage via SharedPreferences
├── ProxyEngine.kt           # Core logic: fetch, parse, test
├── ProxyAdapter.kt          # Proxy list RecyclerView Adapter (swipe actions)
├── SourceAdapter.kt         # Source list RecyclerView Adapter
├── CountryLocator.kt        # Country detection from IP
├── QrDialog.kt              # Proxy QR Code display
├── LoadingOverlay.kt        # Loading state overlay
├── MTFonts.kt               # Vazirmatn font loader
├── UiText.kt                # Bilingual text manager (FA/EN)
├── BootReceiver.kt          # BroadcastReceiver for post-reboot startup
├── PeriodicTestReceiver.kt  # BroadcastReceiver for scheduled tests
└── PeriodicTestScheduler.kt # AlarmManager setup for periodic testing
```

---

### 🔒 Scope & Privacy

- MTLink **does not generate any proxies** — it only reads publicly defined sources
- **No data is sent to any server** — all information is stored locally on your device
- Periodic testing only runs when the user **explicitly** enables it
- For country detection, the proxy IP is queried against public services like `ipwho.is` (not stored)

---

### 📋 Requirements

| Property | Value |
|----------|-------|
| Minimum SDK | Android 7.0 (API 24) |
| Target SDK | Android 15 (API 35) |
| Language | Kotlin |
| Build System | Gradle (Kotlin DSL) |

---

### 🔧 Build

```bash
git clone https://github.com/ramin-mahmoodi/MTLink.git
cd MTLink
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/`.

**Release build:**

Create a `keystore.properties` file at the project root:
```properties
storeFile=path/to/your.keystore
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```
Then:
```bash
./gradlew assembleRelease
```

---

### 🤝 Contributing

1. Fork the repository
2. Create a new branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add: your feature'`
4. Push the branch: `git push origin feature/your-feature`
5. Open a Pull Request

---

### 📄 License

```
MIT License

Copyright (c) 2025 ramin-mahmoodi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

## 🇮🇷 فارسی

### MTLink چیست؟

MTLink یک اپلیکیشن اندرویدی بومی است که به شما اجازه می‌دهد پراکسی‌های عمومی تلگرام را از منابع مختلف دریافت، آزمایش و مدیریت کنید — **بدون هیچ سرور واسطی و کاملاً روی دستگاه خودتان**.

با Kotlin خالص و View های استاندارد اندروید نوشته شده؛ بدون Jetpack Compose، بدون Fragment، بدون وابستگی‌های سنگین.

---

### ✨ قابلیت‌ها

| قابلیت | توضیح |
|--------|--------|
| 🔗 **دریافت پراکسی** | خواندن پراکسی‌های MTProto و SOCKS5 از منابع عمومی |
| ⚡ **آزمایش سریع** | اندازه‌گیری تأخیر (latency) با اتصال مستقیم TCP/SOCKS5 |
| ⭐ **علاقه‌مندی** | نشانه‌گذاری پراکسی‌های پرکاربرد |
| 🌍 **تشخیص کشور** | نمایش کشور سرور با استفاده از IP lookup |
| 📋 **کپی و اشتراک‌گذاری** | کپی لینک پراکسی یا اشتراک‌گذاری مستقیم |
| 📷 **QR Code** | نمایش QR برای اسکن سریع |
| 🔄 **آزمایش دوره‌ای** | زمان‌بندی خودکار آزمایش پراکسی‌ها در پس‌زمینه |
| ➕ **افزودن دستی** | اضافه کردن پراکسی با لینک `tg://proxy` یا `https://t.me/proxy` |
| 📂 **مدیریت منابع** | افزودن، ویرایش و غیرفعال کردن منابع دریافت |
| 🌙 **تم تاریک** | رابط کاربری کاملاً تاریک |
| 🔤 **دوزبانه** | پشتیبانی از فارسی (RTL) و انگلیسی |

---

### 🏗️ معماری پروژه

```
app/src/main/java/ir/mtlink/client/
│
├── MainActivity.kt          # Activity اصلی — رابط کاربری کامل (tab-based)
├── Models.kt                # مدل‌های داده: ProxyRecord، SourceDefinition، ...
├── MTLinkStore.kt           # ذخیره‌سازی روی دستگاه با SharedPreferences
├── ProxyEngine.kt           # منطق اصلی: fetch، parse، test
├── ProxyAdapter.kt          # RecyclerView Adapter پراکسی‌ها (swipe actions)
├── SourceAdapter.kt         # RecyclerView Adapter منابع
├── CountryLocator.kt        # تشخیص کشور از روی IP
├── QrDialog.kt              # نمایش QR Code پراکسی
├── LoadingOverlay.kt        # نمایش وضعیت بارگذاری
├── MTFonts.kt               # بارگذاری فونت Vazirmatn
├── UiText.kt                # مدیریت متن دوزبانه (FA/EN)
├── BootReceiver.kt          # BroadcastReceiver برای اجرا پس از ریستارت
├── PeriodicTestReceiver.kt  # BroadcastReceiver برای آزمایش دوره‌ای
└── PeriodicTestScheduler.kt # تنظیم AlarmManager برای آزمایش دوره‌ای
```

---

### 🔒 دامنه و حریم خصوصی

- MTLink **هیچ پراکسی‌ای تولید نمی‌کند** — فقط منابع عمومی از پیش تعریف‌شده را می‌خواند
- **هیچ داده‌ای به سرور ارسال نمی‌شود** — همه اطلاعات فقط روی دستگاه شما ذخیره می‌شود
- آزمایش دوره‌ای تنها زمانی اجرا می‌شود که کاربر **صریحاً** آن را فعال کرده باشد
- برای تشخیص کشور، IP پراکسی با سرویس‌های عمومی مثل `ipwho.is` بررسی می‌شود (بدون ذخیره‌سازی)

---

### 📦 پیش‌نیازها و ساخت

**پیش‌نیازها:**
- Android Studio Hedgehog یا جدیدتر
- JDK 17+
- دستگاه یا شبیه‌ساز با Android 7.0 (API 24) یا بالاتر

**ساخت:**
```bash
git clone https://github.com/ramin-mahmoodi/MTLink.git
cd MTLink
./gradlew assembleDebug
```

فایل APK در مسیر `app/build/outputs/apk/debug/` قرار می‌گیرد.

**ساخت Release:**

یک فایل `keystore.properties` در ریشه پروژه بسازید:
```properties
storeFile=path/to/your.keystore
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```
سپس:
```bash
./gradlew assembleRelease
```

---

### 🤝 مشارکت در توسعه

1. ریپوزیتوری را Fork کنید
2. یک Branch جدید بسازید: `git checkout -b feature/your-feature`
3. تغییرات را Commit کنید: `git commit -m 'Add: your feature'`
4. Branch را Push کنید: `git push origin feature/your-feature`
5. یک Pull Request باز کنید
