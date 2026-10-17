# מעקב תינוק 🍼

אפליקציית Android משפחתית לתיעוד האכלות (הנקה / תמ״ל / שאוב / שילוב), חיתולים, מקלחות ושאיבות, עם ווידג'ט להזנה מהירה וסנכרון בין שני טלפונים.

## התקנה בטלפון

1. בכל push ל-`main` נבנה APK חדש ומתפרסם תחת **Releases** בריפו.
2. מהטלפון: פותחים את ה-Release האחרון ← מורידים את `BabyTracker-x.y.z.apk` ← מאשרים "התקנה ממקורות לא ידועים".
3. כל גרסה חדשה מותקנת מעל הקודמת (חתומה באותו מפתח), והנתונים נשמרים.

> הריפו פרטי, לכן צריך להיות מחוברים ל-GitHub בדפדפן של הטלפון כדי להוריד.

## הוספת הווידג'ט

לחיצה ארוכה על מסך הבית ← ווידג'טים ← "מעקב תינוק". הכפתורים: 🍼 האכלה, 🧷 חיתול, 🫧 שאיבה (פותחים חלון קטן), 🛁 מקלחת (נשמרת מיד).

## הפעלת הסנכרון (Firebase) – פעם אחת

1. [console.firebase.google.com](https://console.firebase.google.com) ← **Add project** (אפשר לכבות Google Analytics).
2. **Build ← Authentication ← Get started ← Sign-in method ← Anonymous ← Enable**.
3. **Build ← Firestore Database ← Create database** ← מיקום `eur3` (או הקרוב) ← Production mode.
4. בלשונית **Rules** של Firestore: מדביקים את התוכן של [`firestore.rules`](firestore.rules) ← **Publish**.
5. **Project settings ← Your apps ← Android**, שם החבילה: `com.shirhen.babytracker` ← מורידים את `google-services.json`.
6. שמים את הקובץ ב-`app/google-services.json` (או ב-secret בשם `GOOGLE_SERVICES_JSON` בהגדרות הריפו) ← push ← הגרסה הבאה תכלול סנכרון.
7. באפליקציה: **הגדרות ← שיתוף** ← בטלפון אחד "יצירת משפחה חדשה", בשני מזינים את הקוד.

## חישוב הכמות

הערכה כללית, לא ייעוץ רפואי. הקוד ב-[`FeedingGuide.kt`](app/src/main/java/com/shirhen/babytracker/guide/FeedingGuide.kt).

- **שבוע ראשון:** לפי נפח הקיבה ביום הנוכחי (יום 1 כ-5–7 מ״ל, יום 3 כ-22–30, יום 7 כ-45–60), עד 60 מ״ל לארוחה (AAP).
- **משבוע שני:** משקל אחרון × 150–165 מ״ל לק״ג ליום (AAP: כ-75 מ״ל לכל 453 גרם; 150 – הערכה נמוכה מקובלת), עד 960 מ״ל ביממה, חלקי מספר הארוחות ב-24 השעות האחרונות.
- **לידה לפני שבוע 37:** גיל מתוקן, טווח 150–180 מ״ל לק״ג עד גיל מתוקן של 3 חודשים, והודעה שתוכנית הפגייה קודמת.
- **הנקה ישירה** לא נמדדת במ״ל; המדד הוא חיתולים רטובים (לפחות 6 ביממה אחרי יום 5).

מקורות: [AAP / HealthyChildren](https://healthychildren.org/English/ages-stages/baby/formula-feeding/Pages/Amount-and-Schedule-of-Formula-Feedings.aspx) ·
[Blueberry Pediatrics](https://blueberrypediatrics.com/health-tips/how-big-is-a-newborns-stomach-a-day-by-day-size-guide) ·
[Mother&Baby](https://www.motherandbaby.co.uk/baby/feeding/How-much-milk-should-a-newborn-drink/) ·
[GOSH NHS](https://media.gosh.nhs.uk/documents/Appendix_5_Feeding_Flowchart_over_1500_g.pdf) ·
[CHOP](https://chop.edu/sites/default/files/2025-01/11-Discharge-Algorithm.pdf)

## מבנה

Kotlin · Jetpack Compose · Room (שמירה מקומית, עובד בלי רשת) · Glance (ווידג'ט) · Firestore + Anonymous Auth (סנכרון) · WorkManager (רענון כל 15 דק׳).
