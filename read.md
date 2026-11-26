# راهنمای گام به گام تکمیل پروژه Payflow

نقشه راه آموزشی برای ساخت یک سامانه انتقال پول با Spring Boot و Kafka و RabbitMQ و Redis

مخاطب: توسعه دهنده Junior Java و Spring Boot

مبنای این راهنما: وضعیت فعلی فایل های پروژه و معماری پیشنهادی برای ادامه توسعه

تاریخ تهیه: 25 نوامبر 2026

## هدف این سند

این سند به تو می گوید از وضعیت فعلی پروژه دقیقا چه کارهایی را، با چه ترتیبی و برای چه دلیلی انجام دهی. در پایان مسیر، یک سناریوی کامل خواهی داشت: کاربر ثبت نام می کند، کیف پول دارد، انتقال ایجاد می کند، موجودی رزرو می شود، ثبت حسابداری انجام می شود و اعلان قابل retry ارسال می شود.

این یک پروژه آموزشی است، نه سامانه مالی آماده تولید. با این حال الگوهای درست مانند مالکیت داده، Transactional Outbox، مصرف کننده idempotent، Saga و double entry ledger را تمرین می کند.

## فهرست مسیر

| بخش | نتیجه ای که باید بگیری |
| --- | --- |
| 1. تصویر فعلی پروژه | بدانی چه چیزی آماده است و چه چیزی هنوز فقط اسکلت است. |
| 2. مفاهیم پایه | فرق استفاده Kafka و RabbitMQ و Redis را بفهمی. |
| 3. فاز صفر | پروژه را قابل build و قابل اجرا کنی. |
| 4. فازهای پیاده سازی | wallet، transfer، ledger، notification و gateway را به ترتیب بسازی. |
| 5. تست و مشاهده پذیری | رفتار را با test و metric اثبات کنی. |
| 6. برنامه هفتگی | بدانی در هر هفته چه خروجی قابل اندازه گیری لازم است. |

## 1. تصویر واقعی پروژه در شروع

پروژه یک Maven multi module با Java 21 و Spring Boot 3.5.7 است. Docker Compose برای PostgreSQL، Kafka، RabbitMQ و Redis وجود دارد. فقط auth-service رفتار کاربردی دارد؛ سایر سرویس ها هنوز domain model، migration و API عملی ندارند.

| بخش | وضعیت فعلی | گام بعدی |
| --- | --- | --- |
| api gateway | route سرویس auth و بررسی JWT دارد. | route و سیاست امنیتی wallet و transfer را اضافه کن. |
| auth service | ثبت نام، login، JWT، refresh token و outbox دارد. | تنظیم outbox را اصلاح و event را قابل مصرف کن. |
| wallet service | فقط bootstrap و dependency دارد. | کیف پول، موجودی و رزرو را بساز. |
| transfer service | فقط bootstrap و security موقت دارد. | orchestrator انتقال و Saga را بساز. |
| ledger service | فقط bootstrap دارد. | دفتر کل immutable و double entry بساز. |
| notification service | فقط bootstrap و dependency دارد. | صف RabbitMQ، retry و DLQ بساز. |
| infrastructure | Compose معتبر و سرویس های محلی آماده هستند. | health check و متغیرهای محیطی را تکمیل کن. |

### اشکال های مهمی که پیش از توسعه باید رفع شوند

1. در auth-service مقدار outbox زیر spring.payflow تعریف شده، اما کد property به نام payflow.outbox.publisher.enabled را می خواند. بنابراین ظاهرا publisher خاموش است ولی در عمل با matchIfMissing فعال می شود.

2. artifactId ماژول api gateway به اشتباه نام dependency یعنی spring-cloud-starter-gateway است. آن را به api-gateway تغییر بده.

3. تست های چهار سرویس وجود دارند ولی dependency مربوط به spring-boot-starter-test در POM آن ها نیست. build تست در Maven شکست خواهد خورد.

4. Wallet، transfer، ledger و notification به JPA و PostgreSQL وابسته اند اما datasource و Flyway configuration ندارند. Spring Boot هنگام اجرا DataSource پیدا نمی کند.

5. Transfer service فعلا همه درخواست ها را permitAll می کند. قبل از اضافه کردن endpoint انتقال، آن را resource server مبتنی بر JWT کن.

6. پوشه های target و .idea داخل ZIP هستند. آن ها خروجی build و تنظیمات محلی هستند و باید خارج از Git و artifact نهایی بمانند.

## 2. واژه نامه کوتاه

| واژه | توضیح ساده |
| --- | --- |
| Kafka topic | کانال رویدادها. پیام ها برای مدت مشخص نگهداری می شوند و مصرف کننده می تواند آن ها را دوباره بخواند. |
| RabbitMQ queue | صف دستور کار. معمولا یک مصرف کننده باید کار را تحویل بگیرد و انجام دهد؛ برای retry و DLQ عالی است. |
| Redis | حافظه سریع key value. برای cache، rate limit و داده کوتاه مدت مناسب است؛ حقیقت موجودی مالی نیست. |
| Outbox | ثبت event در همان transaction دیتابیس business و ارسال آن بعدا به broker. جلوی از دست رفتن event را می گیرد. |
| Idempotency | اگر یک پیام یا request دو بار رسید، نتیجه فقط یک بار اعمال شود. |
| Saga | هماهنگی چند سرویس مستقل با event و عملیات جبرانی، بدون transaction مشترک بین databaseها. |
| DLQ | صف پیام های ناموفق بعد از retryهای مشخص؛ برای بررسی و retry دستی. |

### انتخاب درست ابزار

قاعده ساده این پروژه: Kafka برای eventهای دامنه و تاریخچه قابل replay است؛ RabbitMQ برای فرمان های تحویل محور مثل ارسال ایمیل استفاده می شود؛ Redis برای سرعت و کنترل درخواست است، نه برای نگهداری مانده حساب.

```text
Kafka:  TransferRequested -> FundsReserved -> LedgerPosted -> TransferCompleted
RabbitMQ: SendNotification -> email queue -> retry queue -> dead letter queue
Redis:  rate-limit:login:{ip} ، idempotency:transfer:{userId}:{key}
```

## 3. معماری هدف و جریان یک انتقال

هر سرویس مالک database خودش است. هیچ سرویس دیگری نباید مستقیم به جدول آن وصل شود. ارتباط business بین سرویس ها با event انجام می شود؛ HTTP فقط برای درخواست های کاربر از طریق gateway کاربرد دارد.

```text
Client
  |
  v
API Gateway --> Auth Service  (JWT و JWKS)
  |
  +--> Transfer Service --Kafka--> Wallet Service --Kafka--> Ledger Service
                                ^                       |
                                |                       +--Kafka--> Notification Service
                                |                                      |
                                +----------Kafka-----------------------+--RabbitMQ--> email/log
```

سناریوی حداقلی انتقال:

1. کاربر با access token و Idempotency-Key درخواست POST /api/v1/transfers می فرستد.

2. Transfer service رکورد PENDING و event انتقال را در یک transaction ذخیره می کند.

3. Publisher سرویس transfer، event را از Outbox به Kafka می فرستد.

4. Wallet service event را یک بار پردازش می کند، مبلغ را رزرو و FundsReserved یا FundsRejected منتشر می کند.

5. Transfer service نتیجه را می گیرد. در موفقیت دستور ثبت دفترکل می دهد؛ در شکست وضعیت FAILED می شود.

6. Ledger service postingهای debit و credit را به صورت immutable ثبت و LedgerPosted منتشر می کند.

7. Transfer service وضعیت COMPLETED می دهد و Notification service پیام اطلاع رسانی را با RabbitMQ ارسال می کند.

## 4. فاز صفر آماده سازی محیط

### 4.1 ابزارهایی که لازم داری

☐ JDK 21 - در محیط فعلی Java 21 موجود است.

☐ Docker Desktop - برای اجرای brokerها و PostgreSQL.

☐ Apache Maven 3.9 یا Maven Wrapper - در پروژه فعلی wrapper وجود ندارد.

☐ IDE مانند IntelliJ IDEA Community یا Ultimate با پلاگین Spring.

☐ Git و یک REST client مانند Postman یا Bruno.

### 4.2 Maven Wrapper یا Maven

بهترین حالت برای تیم، اضافه کردن Maven Wrapper به root پروژه است. در این حالت همه از نسخه یکسان Maven استفاده می کنند. ابتدا Maven را نصب کن، سپس در ریشه پروژه دستور زیر را اجرا کن:

```text
mvn -N wrapper:wrapper
.\mvnw.cmd test
```

فایل های mvnw، mvnw.cmd و پوشه .mvn را commit کن. بعد از آن در مستندات تیم به جای mvn از mvnw.cmd استفاده کن.

### 4.3 اصلاح POM و dependency تست

در api-gateway/pom.xml این مقدار را اصلاح کن:

```text
<artifactId>api-gateway</artifactId>
```

در هر سرویس که test دارد، dependency زیر را اضافه کن. auth-service آن را دارد، اما بقیه باید اضافه کنند.

```text
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 4.4 اصلاح configuration Outbox

در application.yml سرویس auth، بخش outbox را از زیر spring بیرون بیاور و با بخش payflow.jwt یکی کن. YAML نباید دو کلید هم نام payflow در سطح root داشته باشد.

```text
payflow:
  jwt:
    issuer: payflow-auth
    access-token-ttl: 15m
    refresh-token-ttl: 7d
    key-id: payflow-key-1
    private-key: ${JWT_PRIVATE_KEY:}
    public-key: ${JWT_PUBLIC_KEY:}
  outbox:
    publisher:
      enabled: false
```

تا زمانی که Kafka و consumerها آماده نشده اند، enabled را false نگه دار. وقتی test integration گذشت، آن را در profile local برابر true قرار بده.

### 4.5 اجرای زیرساخت محلی

```text
cd infrastructure
docker compose up -d
docker compose ps
docker compose logs -f kafka
```

آدرس های محلی: PostgreSQL روی 5432، Redis روی 6379، Kafka روی 9092 و RabbitMQ روی 5672 اجرا می شوند. پنل RabbitMQ در http://localhost:15672 است. اطلاعات login توسعه در docker-compose.yml قرار دارد؛ آن را برای محیط واقعی استفاده نکن.

### 4.6 اجرای تدریجی و baseline test

```text
.\mvnw.cmd test
.\mvnw.cmd -pl auth-service spring-boot:run
.\mvnw.cmd -pl api-gateway spring-boot:run
```

ابتدا فقط auth-service را بالا بیاور. سپس gateway را اجرا کن. برای تست health از این درخواست استفاده کن:

```text
curl http://localhost:8081/actuator/health
curl http://localhost:8080/actuator/health
```

تعریف موفقیت فاز صفر: همه testها سبز، Docker Compose بالا، auth-service و gateway بدون خطا start می شوند.

## 5. فاز یک تکمیل Auth و اولین Kafka event

### 5.1 قرارداد event را قبل از consumer بساز

هر event باید envelope ثابت داشته باشد. این کار debugging، versioning و idempotency را ساده می کند. payloadهای نامرتب یا eventهای بدون شناسه، بعدا مشکل بزرگی ایجاد می کنند.

```text
public record EventEnvelope<T>(
    UUID eventId,
    String eventType,
    int version,
    Instant occurredAt,
    T payload
) {}
```

برای UserRegistered، payload فقط اطلاعاتی را داشته باشد که consumer واقعا نیاز دارد: userId و email. password یا refresh token هرگز نباید داخل Kafka منتشر شود.

### 5.2 استاندارد نام topic

| Topic | Producer | Consumer آغازین | کلید پیام |
| --- | --- | --- | --- |
| payflow.user.registered.v1 | auth | wallet | userId |
| payflow.transfer.requested.v1 | transfer | wallet | transferId |
| payflow.funds.reserved.v1 | wallet | transfer | transferId |
| payflow.funds.rejected.v1 | wallet | transfer | transferId |
| payflow.ledger.post.requested.v1 | transfer | ledger | transferId |
| payflow.ledger.posted.v1 | ledger | transfer | transferId |
| payflow.transfer.completed.v1 | transfer | notification | transferId |

برای محیط local سه partition و replication factor یک کافی است. در production، تعداد partition و retention باید براساس بار و نیاز replay تعیین شود.

### 5.3 Outbox و delivery حداقل یک بار

Outbox تضمین می کند اگر ثبت کاربر موفق شد، event گم نشود. با این حال Kafka delivery معمولا at least once است: اگر برنامه بعد از ارسال Kafka و قبل از ثبت published_at crash کند، همان event دوباره ارسال می شود. این طبیعی است و با idempotent consumer حل می شود، نه با امید به ارسال دقیقا یک بار.

☐ Event را در همان @Transactional ثبت business ایجاد کن.

☐ Publisher eventهای منتشرنشده را batch بخواند و پس از ack آن ها را published کند.

☐ هر consumer eventId را در جدول processed_messages ذخیره کند.

☐ اگر event قبلا دیده شده بود، بدون اجرای منطق business از آن عبور کن.

### 5.4 تست هایی که همین فاز لازم دارد

• ثبت نام باید user و outbox event را در یک transaction بسازد.

• خاموش بودن Kafka نباید ثبت نام را خراب کند؛ event باید در outbox بماند.

• روشن شدن publisher باید event ذخیره شده را به Kafka برساند.

• پردازش یک event تکراری نباید دو wallet بسازد.

## 6. فاز دو Wallet Service

Wallet service مالک مانده حساب و reservation است. هیچ سرویس دیگری نباید مستقیم ستون balance را تغییر دهد. برای پروژه آموزشی، یک endpoint deposit مدیریت شده می تواند موجودی اولیه بسازد؛ در محصول واقعی این بخش به payment provider یا فرایند دیگری متصل می شود.

### 6.1 مدل داده پیشنهادی

| جدول | فیلدهای اصلی | هدف |
| --- | --- | --- |
| wallets | id، user_id، currency، available_balance، reserved_balance، version | مانده قابل برداشت و مبلغ رزرو شده. |
| wallet_reservations | id، transfer_id unique، wallet_id، amount، status، created_at | مانع reserve تکراری برای یک انتقال. |
| processed_messages | event_id primary key، processed_at، event_type | idempotent consumer. |
| outbox_events | event envelope، message key، published_at | انتشار event با transaction. |

برای مبلغ از BigDecimal و ستون NUMERIC(19,4) استفاده کن، یا مبلغ را با واحد کوچک پول مانند ریال به شکل long نگه دار. از double استفاده نکن؛ خطای گرد کردن باعث می شود پول ناپدید یا اضافه شود.

### 6.2 endpointهای اولیه

```text
POST /api/v1/wallets
GET  /api/v1/wallets/me
POST /api/v1/wallets/{id}/deposits   # فقط توسعه و تست
```

در نسخه اول، wallet را هنگام دریافت UserRegistered بساز تا کاربر با یک کیف پول پیش فرض وارد سیستم شود. endpoint ایجاد دستی wallet فقط اگر چندارزی یا چندکیف پول لازم داری نگه دار.

### 6.3 رزرو موجودی با optimistic locking

به entity کیف پول یک @Version اضافه کن. وقتی دو انتقال همزمان روی یک wallet اجرا شوند، فقط یکی از updateهای ناسازگار موفق می شود؛ دیگری باید retry کنترل شده شود. داخل transaction بررسی کن که available_balance حداقل مبلغ موردنیاز باشد، سپس available را کم و reserved را زیاد کن.

```text
@Version
private Long version;

// available = available - amount
// reserved  = reserved + amount
```

reservation باید transferId یکتا داشته باشد. اگر همان event دوباره آمد، reservation موجود را برگردان و دوباره balance را تغییر نده.

## 7. فاز سه Transfer Service و Saga

Transfer service مالک state انتقال است و نقش orchestrator دارد. این سرویس نه موجودی wallet را مستقیما تغییر می دهد و نه posting دفترکل را خودش می نویسد؛ فقط eventها را ارسال و نتیجه ها را مدیریت می کند.

### 7.1 مدل وضعیت انتقال

```text
PENDING
  -> FUNDS_RESERVED
  -> LEDGER_POSTING
  -> COMPLETED

PENDING or FUNDS_RESERVED
  -> FAILED
  -> COMPENSATING
  -> COMPENSATED
```

برای شروع، state machine را ساده و روشن نگه دار. هر transition باید فقط از stateهای مجاز اجازه داشته باشد. مثلا دریافت LedgerPosted برای انتقال FAILED نباید آن را دوباره COMPLETED کند.

### 7.2 Idempotency برای API انتقال

کاربر یا network ممکن است یک request را دوباره بفرستد. header اجباری Idempotency-Key از ساخت دو انتقال جلوگیری می کند. کلید را همراه userId و hash بدنه request ذخیره کن. اگر همان کلید با همان body رسید، پاسخ قبلی را بده؛ اگر body فرق داشت، خطای 409 برگردان.

```text
POST /api/v1/transfers
Authorization: Bearer <access-token>
Idempotency-Key: 51d0f7f2-...

{
  "sourceWalletId": "...",
  "destinationWalletId": "...",
  "amount": "125000.00",
  "currency": "IRR"
}
```

### 7.3 timeout و جبران سازی

اگر wallet مبلغ را reserve کرد ولی ledger در بازه مشخص ثبت نشد، transfer نباید تا ابد pending بماند. یک job زمان بندی شده انتقال های قدیمی را پیدا می کند و event ReleaseFundsRequested می فرستد. Wallet service با این event reservation را آزاد می کند. این عملیات نیز باید idempotent باشد.

## 8. فاز چهار Ledger Service

Ledger تاریخچه حسابداری است، نه cache مانده wallet. رکوردهای آن نباید update یا delete شوند. هر اصلاح با entry جدید انجام می شود تا audit trail حفظ شود.

| جدول | نمونه فیلدها | قانون |
| --- | --- | --- |
| journal_entries | id، transfer_id unique، occurred_at، reference | برای هر انتقال یک journal entry. |
| ledger_postings | id، entry_id، account_code، debit، credit، currency | حداقل دو posting در هر entry. |
| processed_messages | event_id، processed_at | رویداد تکراری نباید entry جدید بسازد. |

قانون کلیدی double entry: جمع debitها باید با جمع creditها برابر باشد. این invariant را پیش از commit بررسی کن و برای آن test مستقل بنویس. اگر انتقال داخلی بین دو wallet است، حداقل یک حساب بدهکار و یک حساب بستانکار در یک journal entry ثبت کن.

## 9. فاز پنج Notification با RabbitMQ

Notification service event TransferCompleted را از Kafka مصرف می کند، سپس فرمان SendNotification را به RabbitMQ می فرستد یا در آن مصرف می کند. دلیل استفاده RabbitMQ این است که ارسال ایمیل یک کار تحویل محور، قابل retry و احتمالا کند است؛ نه یک حقیقت دامنه که نیاز به replay طولانی مدت داشته باشد.

### 9.1 topology پیشنهادی RabbitMQ

```text
payflow.notifications.exchange
  routing key: notification.email
    -> payflow.notifications.email
    -> payflow.notifications.retry  (TTL و dead letter)
    -> payflow.notifications.dlq
```

مصرف کننده باید تعداد تلاش را در header نگه دارد. پس از مثلا 3 تلاش ناموفق، پیام را به DLQ بفرست. پیام DLQ را حذف نکن؛ آن را بررسی، علت را رفع و سپس retry دستی کن.

☐ برای شروع به جای SMTP واقعی، ارسال را در log و database ثبت کن.

☐ بعدا MailSender و یک template ساده email اضافه کن.

☐ هر notification باید transferId و messageId یکتا داشته باشد تا دوبار ارسال نشود.

## 10. فاز شش Redis

Redis را پس از درست شدن PostgreSQL و flow اصلی اضافه کن. اگر زود اضافه شود، debugging برای Junior سخت تر می شود. ابتدا منطق صحیح، سپس سرعت.

| کاربرد | کلید نمونه | TTL پیشنهادی | نکته |
| --- | --- | --- | --- |
| Rate limit login | rate-limit:login:{ip} | 1 تا 15 دقیقه | در صورت عبور limit پاسخ 429 بده. |
| Rate limit register | rate-limit:register:{ip} | 1 ساعت | از bot و abuse کم می کند. |
| Idempotency response | idempotency:transfer:{userId}:{key} | 24 ساعت | PostgreSQL همچنان منبع قطعی است. |
| Wallet summary cache | wallet-summary:{walletId} | 30 تا 120 ثانیه | بعد از تغییر wallet آن را invalidate کن. |

برای rate limit الگوریتم ساده fixed window کافی است. مقدار counter را با INCR بالا ببر و فقط بار اول expiry بگذار. در آینده sliding window یا token bucket را تمرین کن.

## 11. فاز هفت Gateway و امنیت

1. routeهای wallet و transfer را به gateway اضافه کن. هر سرویس داخلی در توسعه می تواند روی port خودش باشد، اما client باید عمدتا gateway را صدا بزند.

2. در gateway و سرویس های محافظت شده، JWT signature، issuer، expiry و در صورت نیاز audience را validate کن.

3. برای local می توان key موقت ساخت؛ اما برای محیط پایدار، JWT_PRIVATE_KEY و JWT_PUBLIC_KEY را از secret امن بخوان. restart نباید همه tokenها را بی اعتبار کند مگر عمدا key rotate شود.

4. actuator/health را public نگه دار، اما سایر actuator endpointها را در production محافظت کن.

5. password، token، refresh token و اطلاعات کارت را هرگز log نکن.

## 12. تست چگونه باید رشد کند

| نوع تست | چه چیزی را ثابت می کند | مثال |
| --- | --- | --- |
| Unit test | یک کلاس بدون شبکه و database درست است. | محاسبه state transition و قوانین amount. |
| Repository test | mapping و query database درست است. | یافتن reservation با transferId. |
| Integration test | Spring، PostgreSQL، Kafka یا RabbitMQ با هم کار می کنند. | ثبت نام، تولید outbox و انتشار event. |
| Contract test | شکل event بین producer و consumer ثابت است. | version و فیلدهای UserRegistered. |
| End to end test | سناریوی واقعی از HTTP تا event و database کامل است. | انتقال موفق و ثبت ledger. |

برای integration testهای واقعی به جای H2، Testcontainers را یاد بگیر. H2 رفتار PostgreSQL را دقیقا تقلید نمی کند؛ مثلا انواع داده، locking و SQL dialect می تواند متفاوت باشد.

```text
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

## 13. مشاهده پذیری و debugging

وقتی سیستم event driven می شود، فقط log معمولی کافی نیست. برای هر request یک correlationId بساز یا از header آن را بگیر؛ آن را در log، Kafka header و RabbitMQ header منتقل کن. هنگام خطا می توانی کل مسیر یک transfer را با همان ID پیدا کنی.

☐ Spring Boot Actuator health، metrics و info را فعال نگه دار.

☐ در log، transferId، eventId، userId و correlationId را ساختاریافته ثبت کن.

☐ Metric برای تعداد پیام های retry، DLQ، consumer lag، transfer pending و transfer failed بساز.

☐ برای local از Kafka UI یا CLI و RabbitMQ Management UI استفاده کن تا eventها و queueها را ببینی.

## 14. ترتیب دقیق commitها

commitهای کوچک باعث می شوند اگر مرحله ای شکست خورد، دلیل را سریع پیدا کنی. هر commit باید build و test خودش را داشته باشد.

| Commit پیشنهادی | محتوا |
| --- | --- |
| chore build baseline | Maven Wrapper، dependency تست، اصلاح artifactId و profileهای پایه. |
| fix auth outbox config | اصلاح property و test مربوط به آن. |
| feat shared event envelope | contract event نسخه دار و test serialization. |
| feat wallet create on user registered | migration، consumer idempotent و outbox. |
| feat wallet reserve funds | reservation، locking و eventهای نتیجه. |
| feat transfer saga | API، Idempotency-Key، state machine و timeout. |
| feat ledger postings | journal entry، double entry و event LedgerPosted. |
| feat notifications retry | RabbitMQ topology، retry و DLQ. |
| feat redis rate limits | rate limit و cache با metric و test. |

## 15. برنامه پیشنهادی ده هفته ای

| هفته | تمرکز | تعریف Done |
| --- | --- | --- |
| 1 | Build و Docker | همه سرویس ها start می شوند و test baseline سبز است. |
| 2 | Auth و Outbox | UserRegistered پس از ثبت نام در Kafka دیده می شود. |
| 3 | Wallet پایه | UserRegistered یک wallet می سازد و wallet قابل خواندن است. |
| 4 | Reservation | رزرو همزمان و event تکراری balance را خراب نمی کند. |
| 5 | Transfer API | Idempotency-Key و state PENDING کار می کنند. |
| 6 | Saga | reserve موفق یا ناموفق، وضعیت transfer را درست تغییر می دهد. |
| 7 | Ledger | postingهای متعادل و immutable ثبت می شوند. |
| 8 | Notification | پیام ناموفق retry و سپس وارد DLQ می شود. |
| 9 | Redis و Gateway | rate limit، cache و routeهای امن کار می کنند. |
| 10 | E2E و observability | سناریوی کامل، metric و راهنمای اجرا آماده است. |

## 16. چک لیست نهایی قبل از گفتن پروژه کامل است

☐ هر سرویس database خودش را دارد و table مشترک ندارد.

☐ هر event مهم با Outbox منتشر و هر consumer با Inbox یا processed_messages idempotent است.

☐ مبلغ با double نگهداری نشده و تغییر موجودی transaction و locking دارد.

☐ ledger immutable است و جمع debit و credit هر entry برابر است.

☐ هیچ endpoint انتقال بدون JWT قابل دسترسی نیست.

☐ Kafka event contract نسخه دارد و eventهای تکراری باعث اثر تکراری نمی شوند.

☐ RabbitMQ دارای retry و DLQ است و پیام مرده قابل بررسی است.

☐ Redis منبع اصلی balance نیست.

☐ همه migrationها با Flyway اجرا می شوند و test integration با container وجود دارد.

☐ یک سناریوی end to end مستند و خودکار اجرا می شود.

## 17. اشتباه هایی که نباید انجام دهی

• برای ساده کردن کار، database سرویس دیگر را مستقیم query نکن.

• همه چیز را با Kafka حل نکن؛ request کاربر و query ساده می تواند HTTP باشد.

• فقط به exactly once تکیه نکن. طراحی idempotent ضروری است.

• برای balance از Redis استفاده نکن و cache را حقیقت مالی فرض نکن.

• یک transaction توزیع شده بین PostgreSQL و Kafka نساز؛ Outbox و Saga را تمرین کن.

• قبل از داشتن test integration سراغ featureهای زیاد نرو.

• کلید خصوصی، password و token را commit یا log نکن.

## 18. اولین کار امروز

اگر فقط می خواهی از یک نقطه شروع کنی، همین ترتیب را انجام بده:

1. Maven Wrapper را اضافه کن و mvnw.cmd test را اجرا کن.

2. artifactId gateway، dependencyهای تست و property outbox را اصلاح کن.

3. Docker Compose را بالا بیاور و auth-service را اجرا کن.

4. یک تست integration بنویس که ثبت نام، ایجاد OutboxEvent و انتشار UserRegistered را بررسی کند.

5. بعد از سبز شدن آن تست، فقط wallet creation از UserRegistered را بساز؛ هنوز سراغ transfer نرو.

قاعده پیشرفت: هر بار فقط یک رفتار کوچک و قابل تست بساز. وقتی test آن سبز شد، commit کن؛ سپس مرحله بعدی را شروع کن.
