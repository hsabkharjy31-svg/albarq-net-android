# البرق نت | ALBARQ NET

تطبيق Android لمحفظة رصيد مغلقة مخصّصة لشراء بطاقات الإنترنت من شبكة مزوّد الخدمة. لا يدعم التحويل بين المستخدمين أو السحب النقدي. التطبيق لا ينشئ حسابات MikroTik ولا يتصل بواجهة MikroTik؛ يستلم بيانات البطاقة الجاهزة من الخادم بعد نجاح الشراء.

## الحالة الحالية

المرحلة الأولى — تأسيس المشروع. يتضمن هذا المستودع هيكل Android باستخدام Kotlin وJetpack Compose وMaterial 3 وHilt، والثيم العربي RTL، وإعدادات البناء وCI. ستُنفذ طبقات الشبكة والتخزين والشاشات الوظيفية على مراحل منفصلة.

## لقطات الشاشة

لقطات الشاشات ستضاف بعد تشغيل التطبيق على جهاز أو محاكي Android.

## المتطلبات والبناء

- JDK 17
- Android SDK
- Android Gradle Plugin 8.11.1 وGradle 8.14.3
- Kotlin 2.2.21

انسخ `local.properties.example` إلى `local.properties`، واضبط مسار SDK و`API_BASE_URL`، ثم نفّذ:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

## ملخص عقد الخادم

يُضبط عنوان API من `local.properties` عبر `API_BASE_URL`. العقد يشمل OTP والتسجيل وتسجيل الدخول وبيانات المستخدم، المحفظة والمعاملات، وسائل الشحن وطلبات الشحن مع إيصال، الباقات والشراء باستخدام `Idempotency-Key`، وقائمة البطاقات. لا يتصل التطبيق بـ MikroTik مباشرة.

## قواعد أمنية

- PIN يظهر من استجابة الشراء الناجحة فقط، ويبقى في الذاكرة ولا يُحفظ في Room أو DataStore.
- استجابة `/my-cards` لا تحتوي PIN؛ ظهور هذا الحقل يعد خطأً أمنياً.
- الرصيد يُجلب من الخادم ولا يُخزن محلياً عبر الجلسات.
- الشحن لا يزيد الرصيد إلا بعد موافقة الإدارة.
- عند HTTP 401 تُمسح الجلسة ويُعاد المستخدم لتسجيل الدخول.

## الافتراضات

- لم تُرفق ملفات Cairo؛ يستخدم التطبيق خط النظام بديلاً حتى توفيرها.
- `compileSdk` مضبوط على 36، مع `targetSdk` 34 و`minSdk` 23.
- المرحلة الأولى هي هيكل تأسيسي ولا تنفذ طلبات الشبكة بعد.

## الترخيص

مشروع خاص/تجاري لصالح مالك شبكة البرق نت. إتاحة المستودع للعامة لا تمنح حق إعادة التوزيع أو الاستخدام التجاري لطرف ثالث. يجب إضافة ترخيص ملكية مناسب قبل أي توزيع.

---

# English

ALBARQ NET is an Android closed-wallet client for purchasing pre-generated internet cards from the ISP. It does not support peer-to-peer transfers or cash withdrawals and never calls the MikroTik API.

## Status

Phase 1 — project foundation. Kotlin, Jetpack Compose, Material 3, Hilt, Arabic RTL theme, build configuration, and CI foundation are provided. Networking, persistence, and feature screens will be implemented in separately approved phases.

## Build

Requirements: JDK 17, Android SDK, AGP 8.11.1, Gradle 8.14.3, Kotlin 2.2.21. Copy `local.properties.example` to `local.properties`, configure `sdk.dir` and `API_BASE_URL`, then run:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lint
```

## Backend contract

The configurable API base URL serves OTP, registration, login, current-user, wallet, transactions, manual-review top-ups, packages, idempotent orders, and purchased-card listing. The app never calls MikroTik directly.

## Security

PIN is only available from a successful purchase response and must remain in memory. It must never be persisted or returned by `/my-cards`. Wallet balance is always fetched from the backend. Top-up receipts affect balance only after admin approval.

## License

Private/commercial project. Public repository visibility does not grant third-party redistribution or commercial-use rights.
