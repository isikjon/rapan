# Rapan Android Mini Apps

Проект содержит 2 Android-приложения (flavors):
- `client` -> ярлык `рапан-клиент`
- `driver` -> ярлык `рапан-такси`

## Поведение
- `client` стартует с `https://рапан-такси.рф/index/order`.
- Рабочие страницы открываются внутри приложения (WebView), без принудительного перехода во внешний браузер.
- `driver` стартует с `https://рапан-такси.рф/user`.
- В `driver` шапка сайта скрывается (режим “без шапки”).
- Внешние переходы используются только для специальных схем: `tel:`, `mailto:`, `sms:`, `intent://` и внешних доменов.
- Авторизация и сессионные cookie сохраняются между перезапусками приложения/телефона.

## Иконки
Отдельные иконки для `client` и `driver` уже подключены в `mipmap-*` по flavor.

## Сборка
1. Установить JDK 17 и Android SDK.
2. Убедиться, что `JAVA_HOME` указывает на JDK 17.
3. Создать `local.properties` по образцу `local.properties.example` и прописать путь к SDK.
4. Собрать debug APK:
   - `./gradlew assembleClientDebug`
   - `./gradlew assembleDriverDebug`
5. Собрать релизные AAB:
   - `./gradlew bundleClientRelease`
   - `./gradlew bundleDriverRelease`

## Подпись release
Перед релизом задать переменные окружения:
- `RAPAN_KEYSTORE_PATH`
- `RAPAN_KEYSTORE_PASSWORD`
- `RAPAN_KEY_ALIAS`
- `RAPAN_KEY_PASSWORD`
