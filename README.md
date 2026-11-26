# PayFlow

تمرین ساخت سیستم انتقال پول با معماری Microservices، Java 21 و Spring Boot.

## Modules

- `api-gateway`: ورودی HTTP و مسیریابی درخواست‌ها
- `auth-service`: کاربران و احراز هویت
- `wallet-service`: کیف پول و عملیات موجودی
- `transfer-service`: مدیریت انتقال پول و Saga
- `ledger-service`: دفتر کل و ثبت مالی
- `notification-service`: ارسال اعلان

## Infrastructure

زیرساخت محلی در `infrastructure/docker-compose.yml` تعریف می‌شود:
PostgreSQL، Kafka، RabbitMQ و Redis.

## شروع

```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

سپس هر سرویس را جداگانه از IDE اجرا کنید.

> این پروژه آموزشی است و برای پردازش پول واقعی طراحی یا تأیید نشده است.
