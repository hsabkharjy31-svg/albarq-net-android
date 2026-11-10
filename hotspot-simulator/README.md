# AL-BARQ NET – Hotspot Simulator

مشروع Android مستقل داخل مجلد `hotspot-simulator/`، ولا يستبدل تطبيق المحفظة الموجود في جذر المستودع.

## البناء والتشغيل
1. افتح مجلد `hotspot-simulator` في Android Studio.
2. استخدم JDK 17 وAndroid SDK Platform 36.
3. نفّذ Gradle Sync ثم Build > Build APK(s)، أو شغّل `:app:assembleDebug`.
4. اختر مجلد صفحات الهوتسبوت من الهاتف ثم افتح صفحة الدخول.

## الوظائف الحالية
- منتقي مجلدات SAF مع حفظ إذن القراءة وفهرسة المجلدات الفرعية.
- قراءة ملفات HTML/CSS/JS والصور والخطوط دون تعديل ملفات المصدر.
- WebView محلي على `https://hotspot.local/` مع منع الطلبات الخارجية.
- استبدال مجموعة من متغيرات RouterOS الشائعة ومعالجة كتل `if/elif/else/endif`.
- مسارات محلية: `/login` و`/status` و`/logout` و`/error`.
- اعتراض إرسال نموذج الدخول المحلي وإرساله إلى جسر المحاكاة بدلاً من إرسال POST إلى راوتر.
- محاكاة نجاح الدخول عند إدخال اسم مستخدم وكلمة مرور غير فارغين؛ لا يتم حفظ كلمة المرور.
- مسار `/login?var=callBack` يعيد كائن حالة محلياً `window.mikrotikPreview` ويطلق حدث `mikrotik-preview-ready`، ويمكن للقالب تعريف `window.albarqMikrotikCallback(state)`.
- سجل أولي لأخطاء 404 وأخطاء WebView والطلبات الخارجية والملفات المفقودة.

## حدود المحاكاة
هذا التطبيق ليس RouterOS ولا ينشئ اتصالاً أو جلسة Hotspot حقيقية. قالب العينة الحالي لا يحتوي على callback خاص بـ MikroTik ولا على CHAP؛ لذلك callback أعلاه عقد محلي خاص بالمحاكي، وليس تنفيذاً مطابقاً لجميع إصدارات RouterOS أو قوالبها. مصادقة CHAP/MD5، ومقارنة استجابات MD5، ومحاكاة متغيرات/شروط RouterOS كاملة غير منفذة. كما أن نتيجة الدخول المحلية تعتمد على كون الحقلين غير فارغين فقط ولا تتحقق من بيانات اعتماد حقيقية.

## الخصوصية
لا توجد صلاحية INTERNET في Manifest ولا خدمة رفع. يقرأ التطبيق الملفات من URI يختاره المستخدم عبر SAF، ولا يكتب إلى مجلد المصدر. كلمة المرور لا تُخزّن ولا تُعرض في سجل التشخيص.

## اختبار يدوي
1. اختر مجلداً يحتوي على `login.html` و`status.html` و`logout.html` مع مجلدات الأصول.
2. جرّب الدخول بمدخلين غير فارغين؛ يجب الانتقال إلى صفحة الحالة.
3. جرّب إرسال النموذج مع حقل فارغ؛ يجب البقاء في صفحة الدخول وإظهار رسالة خطأ إذا كان القالب يعرض `$(error)`.
4. اضغط زر اختبار callback للتحقق من المسار المحلي.
5. افتح رابط ملف غير موجود ثم راجع سجل التشخيصات.


## Live preview note
After selecting a folder, the app now indexes the selected tree and automatically opens the local `/login` preview. If `login.html` is nested one or more directories below the selected folder, that directory is treated as the template root.
