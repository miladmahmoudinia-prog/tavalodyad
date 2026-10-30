# تولدیاد (TavalodYad)

اپ آفلاین یادآوری تولدهای تقویم شمسی، با ذخیره‌سازی محلی Room و پشتیبان‌گیری/بازیابی JSON. تاریخ روز از ساعت دستگاه گرفته می‌شود و تبدیل تقویم به‌صورت Kotlin خالص و بدون کتابخانهٔ بیرونی انجام می‌شود.

## دریافت APK با GitHub Actions
1. محتوای این پوشه را در مخزن GitHub (شاخهٔ `main` یا `master`) قرار دهید.
2. با push یا از تب Actions اجرای دستی `Android CI` را آغاز کنید.
3. artifact با نام `tavalodyad-apks` شامل APKهای debug و release خواهد بود.

Workflow از JDK 17 و Gradle 8.7 استفاده می‌کند و دستور `gradle build assembleDebug assembleRelease` را اجرا می‌کند. این پروژه در محیط تولید فایل build/کامپایل نشده است.

## مشخصات
- شناسه برنامه: `ir.tavalodyad.app`
- تاریخ‌ها: جلالی؛ الگوریتم breaks-array و بدون نیاز به اینترنت
- داده‌ها: پایگاه‌داده محلی Room؛ فایل پشتیبان/بازیابی JSON
