# Браузерные проверки

Отдельно от основного приложения, на своей тестовой копии (порт 8090, своя база):

```bash
cd e2e && npm install && npx playwright install chromium && mkdir -p shots && cd ..
COMPOSE_PROJECT_NAME=wgtest APP_PORT=8090 docker compose up -d --build
COMPOSE_PROJECT_NAME=wgtest APP_PORT=8090 docker compose exec -T backend node dist/scripts/seed.js
cd e2e && node audit.mjs   # вёрстка: все экраны, ширины и языки
```

- `audit.mjs`, `feat.mjs`, `buy.mjs`, `freeze.mjs` нужны демо-данные. Перед каждым пересоздавайте тестовую базу: `docker compose down -v`, затем `up -d` и seed с теми же переменными.
- `flow.mjs` нужна пустая база, без seed.
- `sheet.mjs` собирает скриншоты из `shots/` на один лист.

Основную копию (порт 8080) эти скрипты не трогают.
