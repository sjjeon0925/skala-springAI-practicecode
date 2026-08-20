# 1. Spring Boot 기초 – 어노테이션과 계층 구조

- 왜 계층을 나누는가 · 어노테이션이 하는 일
- Controller · Service · Repository · Mapper
- DTO 변환과 API 문서(Swagger)
- AI는 어느 계층에 두나

## 쉽게 말하면 – 계층 구조

- 코드를 역할별 서랍에 나눠 담는 것
- 서랍이 있으면 고칠 것만 꺼내 고친다
- 지금은 "위에서 아래로만 부른다"만 기억한다

| 이렇게 생각하면 쉽다 | 실제로는 |
|---|---|
| 주문 접수 창구 = Controller – 요청을 받는다 | 안 나누면: 창구 직원이 창고까지 뛴다 |
| 처리 담당자 = Service – 업무 흐름을 결정 | 규칙이 여기저기 흩어진다 |
| 창고 관리자 = Repository / Mapper – 데이터 | 아무나 창고에 들어간다 |
| 창구가 쓰는 서류 양식 = DTO – 주고받는 모양 | 내부 정보가 그대로 나간다 |

"창구는 창고에 안 간다" – 위에서 아래로만 호출. 한 곳을 고치면 전부 터진다.

> **지금은 이것만:** 역할을 나누고, 위에서 아래로만 부른다. 나머지 어노테이션과 규칙은 이 두 문장을 코드로 옮긴 것뿐이다 – 외우지 말고 이 그림만 기억하면 된다.

## 왜 계층을 나누는가

- 한 파일에 다 넣으면 빠르다 – 처음 2주만 그렇다
- 계층을 나누는 목적은 아름다움이 아니라 변경의 파급을 가두는 것
- 화면이 바뀌면 Controller만, 정책은 Service만, 저장소는 Repository만

```
요청: HTTP 요청 → @RestController → @Service → @Repository → DB / 외부 API
응답: DTO ← 도메인 객체 ← 엔티티
```

- **Controller** – 받고 · 검증하고 · 돌려준다. 업무 규칙을 넣지 않는다.
- **Service** – 업무 흐름과 트랜잭션 경계. 여러 Repository를 조합한다.
- **Repository** – 데이터에 닿는 유일한 곳. SQL·쿼리는 여기서 끝난다.

위에서 아래로만 호출한다. Repository가 Service를 부르거나 Controller가 Repository를 직접 부르면 계층은 이미 무너진 것이다.

## 요청 한 번의 여정 – 계층을 지나는 길

- 한 번의 GET이 어디를 지나는지 순서대로 따라가 본다
- 계층마다 쓰는 말이 다르다 – HTTP · 업무 · SQL 순으로 번역된다
- 어디서 실패했는지 알면 어디를 고칠지도 안다

```
GET /ch02/orders/12345?userId=user1

① web/OrderController      HTTP → 자바
   @PathVariable · @RequestParam · @Valid
   넘기는 것: 값(orderId, userId)        // 요청 객체를 그대로 넘기지 않는다

② service/OrderService     업무 흐름과 트랜잭션 경계 — "무엇을 하는가"
   넘기는 것: 조건(주문번호 + 소유자)

③ repository/OrderRepository   JPA — 메서드 이름이 곧 쿼리
   mapper/OrderMapper          MyBatis — SQL을 직접 (같은 자리, 다른 방식)
   돌아오는 것: 엔티티 또는 조회 전용 row

④ dto 변환
   엔티티 → 응답 DTO   (ownerId·cost는 여기서 버려진다)
   돌아오는 것: OrderResponse

⑤ web/OrderController      JSON 직렬화 → 200 OK

# 실패는 계층마다 다른 얼굴로 나타난다
#   400 검증(①) · 404 업무 규칙(②) · 500 SQL·연결(③) · null 변환 누락(④)
```

## 어노테이션 지도 – 무엇이 무엇을 하나

- 어노테이션은 표시일 뿐이다 – 실제 일은 스프링 부트가 스캔해서 한다
- 크게 빈 등록 · 요청 매핑 · 주입 · 설정 · 검증 · 부가 기능 여섯 갈래로 나뉜다
- 이름이 다른 이유는 의도를 드러내기 위해서다 – 기능은 거의 같다

| 갈래 | 어노테이션 | 무엇을 하나 |
|---|---|---|
| 빈 등록 | `@Component` · `@Service` · `@Repository` · `@Controller` | 클래스를 스프링 부트가 관리하는 객체(빈)로 등록한다 |
| 요청 매핑 | `@RestController` · `@GetMapping` · `@PostMapping` | HTTP 요청을 메서드에 연결한다 |
| 주입 | 생성자 주입(권장) · `@Autowired` · `@Qualifier` | 필요한 빈을 찾아 넣어 준다 |
| 설정 | `@Configuration` · `@Bean` · `@ConfigurationProperties` | 내가 직접 만드는 빈과 외부 설정 바인딩 |
| 검증·예외 | `@Valid` · `@RestControllerAdvice` · `@ExceptionHandler` | 입력을 검증하고 예외를 응답으로 바꾼다 |
| 부가 기능 | `@Transactional` · `@Async` · `@Retryable` · `@Aspect` | 본래 코드를 건드리지 않고 행동을 덧붙인다 |

## `@SpringBootApplication`의 정체

- 컴포넌트 스캔 · 자동 구성 · 설정 클래스 세 어노테이션을 합친 것
- 이 클래스가 있는 패키지 아래만 스캔한다 – 위치가 곧 범위다
- Spring AI의 자동 구성도 여기에 얹혀 동작한다

```java
@SpringBootApplication
// = 아래 세 개를 합친 것
//  @Configuration          // 이 클래스도 설정 클래스다
//  @ComponentScan          // 이 패키지 아래의 @Component 계열을 찾는다
//  @EnableAutoConfiguration// 클래스패스를 보고 필요한 빈을 자동 구성한다
public class HelpDeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelpDeskApplication.class, args);
    }
}

// com.skala.helpdesk              ← 여기에 두면
//   ├─ web/       @RestController    ✅ 스캔된다
//   ├─ service/   @Service           ✅ 스캔된다
//   └─ repository/@Repository        ✅ 스캔된다
// com.other.pkg   @Service           ❌ 스캔 안 된다
```

## 스테레오타입 – 이름이 다른 이유

- `@Service`·`@Repository`·`@Controller`는 모두 `@Component`의 특수형
- 빈 등록 기능은 같지만 읽는 사람에게 역할을 알려 준다
- `@Repository`만 예외 변환이라는 실제 추가 기능이 있다

```java
@Component
// 범용 — 위 셋 어디에도 맞지 않을 때
class PasswordHasher { }

@Controller
// 화면(View) 반환 — 템플릿 렌더링
@RestController // = @Controller + @ResponseBody — JSON 반환
class OrderController { }

@Service
// 업무 흐름 — 여러 Repository·외부 호출을 조합
class OrderService { }

@Repository
// 데이터 접근 — DB 예외를 Spring Boot 표준 예외로 변환해 준다
class OrderRepository { }

// @Service를 @Component로 바꿔도 동작은 같다.
// 그래도 @Service를 쓰는 이유는, 이 파일을 여는 사람이
// "여기엔 업무 로직이 있겠구나" 하고 바로 알기 때문이다.
```

## 쉽게 말하면 – 빈과 의존성 주입

- 필요한 물건을 내가 만들지 않고 받아 쓴다
- 누가 만들어 주나 – 스프링 부트가 시작할 때 한 번
- "new를 안 쓴다" 정도로 이해하면 충분하다

| 이렇게 생각하면 쉽다 | 실제로는 | 왜 그렇게 하나 |
|---|---|---|
| 회사가 지급하는 노트북 | 빈(Bean) – 스프링 부트가 만든 객체 | 각자 사 오면 관리가 안 된다 |
| 입사할 때 지급받는다 | 생성자 주입 | 필요한 것이 한눈에 보인다 |
| 한 대를 계속 쓴다 | 싱글턴 – 하나만 만들어 공유 | 매번 만들면 느리고 낭비다 |
| 비품 대장 | `@Configuration` · `@Bean` | 무엇이 있는지 한 곳에서 관리 |
| "내 물건이 아니다" | 상태를 필드에 담지 않는다 | 공유물에 낙서하면 사고가 난다 |

> **지금은 이것만:** 필요한 것은 만들지 말고 받아 쓴다. 그리고 받아 쓰는 것은 모두가 같이 쓰는 물건이니, 거기에 내 데이터를 담아 두면 안 된다.

## 빈 – 언제 만들어지고 몇 개인가

- 빈은 기본적으로 싱글턴 – 앱 전체에 하나만 만들어진다
- 그래서 필드에 요청별 상태를 두면 안 된다 – 다른 사용자와 섞인다
- 생성 시점은 기동 시다 – 설정 오류가 기동에서 바로 드러나는 이유

```java
@Service
class BadService {
    private String currentUserId;          // ❌ 싱글턴 필드에 요청별 상태
    void handle(String userId) {
        this.currentUserId = userId;       // 동시 요청이 서로 덮어쓴다
    }
}

@Service
class GoodService {
    private final OrderRepository repository;   // ✅ 불변 협력자만 필드로
    void handle(String userId) {                // 요청별 값은 파라미터로
        repository.findByOwnerId(userId);
    }
}

// 스코프 — 특별한 이유가 없으면 기본(싱글턴)을 쓴다
//   singleton  앱당 1개 (기본)
//   prototype  주입할 때마다 새로
//   request    HTTP 요청당 1개 (웹 전용)
```

## `@RestController` – 요청을 받는 곳

- 받고 · 검증하고 · 서비스에 넘기고 · 응답 형태로 바꿔 돌려준다
- 업무 규칙을 넣지 않는다 – if 문이 늘어나면 서비스로 옮길 신호
- 요청·응답은 DTO(record)로 받는다 – 엔티티를 그대로 노출하지 않는다

```java
@RestController
@RequestMapping("/api/orders")      // 공통 경로는 클래스에
public class OrderController {

    private final OrderService orderService;   // 서비스만 안다

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{orderId}")               // GET /api/orders/12345
    public OrderResponse find(@PathVariable String orderId,
                               Principal principal) {
        return orderService.find(orderId, principal.getName());
    }

    @PostMapping                            // POST /api/orders
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                                 Principal principal) {
        return orderService.create(request, principal.getName());
    }
}
```

> **주의:** 컨트롤러에 if가 쌓이기 시작하면 업무 규칙이 새어 들어온 것이다. 검증은 `@Valid`에, 판단은 서비스에 맡겨라.

## `@Service` – 업무 흐름과 트랜잭션

- 여러 Repository·외부 호출을 조합해 하나의 업무를 완성한다
- `@Transactional`로 경계를 긋고, 조회 전용은 `readOnly = true`

```java
@Service
@Transactional(readOnly = true)       // 클래스 기본값: 조회
public class OrderService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;

    public OrderService(OrderRepository orderRepository,          // 생성자 주입
                         MemberRepository memberRepository) {
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
    }

    public OrderResponse find(String orderId, String userId) {
        Order order = orderRepository.findByIdAndOwnerId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderResponse.from(order);          // 엔티티 → DTO
    }

    @Transactional                                  // 쓰기에서만 재정의
    public OrderResponse create(CreateOrderRequest req, String userId) {
        Member member = memberRepository.findById(userId)
                .orElseThrow(() -> new MemberNotFoundException(userId));
        return OrderResponse.from(orderRepository.save(Order.of(req, member)));
    }
}
```

## `@Repository` – 데이터에 닿는 곳

- DB·외부 API 접근을 여기서 끝낸다 – 위 계층은 저장 방식을 모른다
- 인터페이스로 두면 저장소를 갈아 끼워도 서비스는 그대로
- 권한 조건은 쿼리 자체에 넣는다 – 조회 후 필터링은 새어 나간다

```java
public interface OrderRepository extends JpaRepository<Order, String> {

    // 소유자 조건을 쿼리에 넣는다 — 이 한 줄이 권한 경계다
    Optional<Order> findByIdAndOwnerId(String id, String ownerId);

    List<Order> findTop5ByOwnerIdOrderByOrderedAtDesc(String ownerId);

    @Query("select o from Order o where o.ownerId = :ownerId "
         + "  and o.status in :statuses")
    List<Order> findActive(@Param("ownerId") String ownerId,
                            @Param("statuses") List<OrderStatus> statuses);
}

// 외부 API도 같은 자리에 둔다 — 서비스는 출처를 모른다
@Repository
public class ShippingApiRepository {
    private final RestClient restClient;
    public Optional<Tracking> findTracking(String invoiceNo) { ... }
}
```

> **주의:** `findById()`로 꺼낸 뒤 자바에서 소유자를 비교하는 코드는 위험하다. 조건을 쿼리에 넣어야 실수로 빠뜨릴 여지가 없다.

## Mapper – SQL을 직접 쓰는 계층

- `@Mapper` 인터페이스 + SQL – 구현체는 MyBatis가 만든다 (Repository와 같다)
- 동적 조건·집계처럼 SQL이 주인공인 자리에서 강하다
- Repository와 같은 층에 선다 – 서비스는 어느 쪽인지 모른다

```java
@Mapper                          // 구현체는 MyBatis가 만들어 빈으로 등록한다
public interface OrderMapper {
    List<OrderRow> search(OrderSearchCondition condition);   // SQL은 XML에

    @Select("select count(*) from orders where owner_id = #{ownerId}")
    long countByOwner(@Param("ownerId") String ownerId);     // 짧으면 애노테이션
}
```

```xml
<!-- resources/mapper/OrderMapper.xml — 조건은 있을 때만 붙는다 -->
<select id="search" resultType="...OrderRow">
    select id as orderId, item, status from orders
    <where>
        owner_id = #{ownerId}                <!-- 권한 조건은 항상 걸리는 자리에 -->
        <if test="status != null">  and status = #{status}         </if>
        <if test="keyword != null"> and item like '%'||#{keyword}||'%' </if>
    </where>
</select>
```

> **주의:** `#{}`는 값 바인딩(?), `${}`는 문자열 결합이다. 정렬 컬럼처럼 `${}`가 필요한 자리는 허용 목록으로 값을 제한한 뒤에 쓴다 – 아니면 그대로 SQL 인젝션이다.

## JPA vs. Mapper

- 둘 다 데이터에 닿는 계층이다 – 자리는 같고 방식이 다르다
- 기준은 하나 – SQL이 주인공인가 아닌가
- 한 프로젝트에서 같이 써도 된다 – 실무에서 가장 흔한 조합이다

| 구분 | JPA Repository | MyBatis Mapper |
|---|---|---|
| SQL | 메서드 이름·JPQL로 생성된다 | 내가 직접 쓴다 |
| 잘 맞는 일 | 단건 CRUD · 엔티티 상태 변경 | 동적 검색 · 집계 · 리포트 |
| 돌려주는 것 | 엔티티 – 영속 상태·변경 감지 | 조회 전용 DTO – 그냥 값 |
| 쓰기 | `save()` 하나로 update까지 | update 문을 직접 쓴다 |
| 튜닝 | 생성된 SQL을 확인해야 안다 | 보이는 그대로 고친다 |
| 같이 쓸 때 | 쓰기·단건 조회를 맡는다 | 목록·통계 화면을 맡는다 |

## 쉽게 말하면 – DTO

- 밖으로 나갈 때 보여 줄 것만 골라 담는 상자
- DB 표를 그대로 보여 주지 않는다
- 귀찮아 보이지만 사고를 막는 장치다

| 이렇게 생각하면 쉽다 | 실제로는 | 안 쓰면 |
|---|---|---|
| 택배 송장 | 응답 DTO – 필요한 항목만 | 장부를 통째로 보낸다 |
| 장부 원본 | 엔티티 – DB 표와 1:1 | 원가·소유자가 그대로 노출 |
| 보낼 것만 옮겨 적기 | 엔티티 → DTO 변환 | 실수로 필드가 새어 나간다 |
| 주문서 양식 | 요청 DTO + 검증 규칙 | 이상한 값이 그대로 들어온다 |
| 양식이 바뀌어도 장부는 그대로 | API와 DB를 분리 | DB를 못 바꾸게 된다 |

> **지금은 이것만:** DB에 있는 것을 그대로 내보내지 않는다. 보여 줄 것만 골라 담는 상자를 하나 만든다 – 그 상자가 DTO다.

## DTO와 엔티티 – 계층의 경계

- 엔티티를 API로 그대로 내보내면 DB 구조가 곧 API 스펙이 된다
- 컬럼 하나 바꿨을 뿐인데 클라이언트가 깨진다
- record로 요청·응답 DTO를 만들고 변환은 한 곳에서

```java
// 요청 DTO — 검증 규칙을 여기에 붙인다
public record CreateOrderRequest(
        @NotBlank String productId,
        @Min(1) @Max(99) int quantity,
        @Size(max = 200) String memo) { }

// 응답 DTO — 내보낼 필드만 고른다
public record OrderResponse(String orderId, String item,
                             String status, LocalDate eta) {
    public static OrderResponse from(Order order) {   // 변환은 한 곳에서
        return new OrderResponse(order.getId(), order.getItem().getName(),
                order.getStatus().name(), order.getEta());
    }
}

@Entity
// 엔티티는 밖으로 나가지 않는다
class Order {
    @Id private String id;
    private String ownerId;      // 내부 전용 — 응답에 없다
    private BigDecimal cost;     // 원가 — 절대 노출하면 안 된다
}
```

## DTO ↔ 엔티티 – 변환은 어디서 하나

- 엔티티를 그대로 내보내면 DB 구조가 곧 API 스펙이 된다
- 변환 코드는 한 곳에 모은다 – 흩어지면 반드시 필드를 빠뜨린다
- 방식은 셋 – 정적 팩터리 · 매퍼 컴포넌트 · MapStruct 중에서 고른다

| 방식 | 언제 쓰나 | 대가 |
|---|---|---|
| DTO 안 정적 팩터리 | 변환이 한두 개 · 다른 빈이 필요 없을 때 | DTO가 엔티티를 알게 된다 |
| 매퍼 컴포넌트(`@Component`) | 변환에 규칙·다른 빈이 필요할 때 | 클래스가 하나는 는다 |
| MapStruct | 필드가 많고 같은 변환이 반복될 때 | 빌드 설정 · 생성 코드를 읽을 줄 알아야 |
| SQL이 직접 채움 | 조회 전용 화면 – 가장 빠르다 | 같은 모양을 여러 곳에서 쓰기 어렵다 |

```java
// ① 정적 팩터리 — 가장 가볍다. 이 프로젝트의 기본.
record OrderResponse(...) { static OrderResponse from(Order o) { ... } }

// ② 매퍼 컴포넌트 — 입구가 둘(엔티티·조회 row)이어도 출구는 하나로 모은다
@Component class OrderDtoMapper {
    OrderResponse toResponse(Order o)    { ... }
    OrderResponse toResponse(OrderRow r) { ... }
}

// ③ MapStruct — 구현체가 컴파일 시점에 생성된다 (리플렉션 비용 없음)
```

## 의존성 주입 – 생성자 주입이 기본

- 필드 주입(`@Autowired`)은 테스트가 어렵고 순환 참조를 숨긴다
- 생성자 주입은 final을 쓸 수 있어 불변 · 누락 시 컴파일 오류
- 생성자가 하나면 `@Autowired`도 생략할 수 있다

```java
// ❌ 필드 주입 — new로 만들 수 없어 단위 테스트가 번거롭다
@Service
class BadOrderService {
    @Autowired private OrderRepository repository;   // final 불가
}

// ✅ 생성자 주입 — 의존성이 시그니처에 드러난다
@Service
class OrderService {
    private final OrderRepository repository;
    private final ChatClient chatClient;

    OrderService(OrderRepository repository,
                 @Qualifier("supportClient") ChatClient chatClient) {
        this.repository = repository;      // 생성자 하나면 @Autowired 생략
        this.chatClient = chatClient;      // 같은 타입이 여럿이면 @Qualifier
    }
}

// 테스트에서는 그냥 new로 만든다 — 스프링 부트 컨텍스트가 필요 없다
var service = new OrderService(new FakeOrderRepository(), stubChatClient);
```

## `@Configuration` – 내가 만드는 빈

- 내 코드가 아닌 클래스(라이브러리)는 `@Component`를 붙일 수 없다
- 그럴 때 `@Configuration` 클래스에서 `@Bean`으로 직접 만든다
- Spring AI의 ChatClient·VectorStore도 이 방식으로 구성한다

```java
@Configuration
public class AiConfig {

    // 라이브러리 타입이라 @Component를 붙일 수 없다 → @Bean으로 만든다
    @Bean
    public ChatClient supportClient(ChatClient.Builder builder) {
        return builder.defaultSystem("너는 친절한 고객 상담원이다.")
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Bean
    // 조건부 등록 —
    @ConditionalOnMissingBean(VectorStore.class)   // 같은 타입이 있으면 물러난다
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}

// 외부 설정 바인딩 — 코드에 상수를 남기지 않는다
@ConfigurationProperties(prefix = "helpdesk")
public record HelpDeskProperties(int topK, double threshold) { }
```

## AOP – 본래 코드를 건드리지 않고

- 로깅·감사·측정처럼 여기저기 흩어지는 코드를 한곳에 모은다
- `@Transactional`도 사실 AOP다 – 이미 쓰고 있었던 셈
- Advisor(12장)가 AI 계층의 AOP다 – 발상이 같다

```java
@Aspect
@Component
public class ExecutionTimeAspect {

    // "service 패키지의 모든 public 메서드"를 가로챈다
    @Around("execution(public * com.skala..service..*(..))")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        try {
            return joinPoint.proceed();               // 본래 메서드 실행
        } finally {
            log.info("{} {}ms", joinPoint.getSignature().toShortString(),
                    (System.nanoTime() - started) / 1_000_000);
        }
    }
}

// ⚠ 프록시 기반이라 같은 클래스 안에서 부르면 안 걸린다
//    this.otherMethod();   → AOP 통과 안 함 (자기 호출)
```

## 입력 검증과 예외 처리

- 검증은 DTO에 선언하고 `@Valid`로 켠다 – 컨트롤러에 if를 쌓지 않는다
- 예외는 던지고, 응답으로 바꾸는 일은 `@RestControllerAdvice` 한 곳에서

```java
@RestControllerAdvice
// 모든 컨트롤러의 예외를 여기서 받는다
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("주문을 찾을 수 없습니다.", null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)   // @Valid 실패
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).collect(joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message, null));
    }

    @ExceptionHandler(Exception.class)          // 예상 못 한 오류
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] 처리 중 오류", traceId, e);      // 상세는 로그에만
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("처리 중 문제가 발생했습니다.", traceId));
    }
}
```

> **주의:** 스택트레이스를 응답에 담으면 내부 구조가 그대로 노출된다. 사용자에겐 안전한 문구와 추적 ID만, 상세는 로그에만 남긴다.

## API 문서 – 코드에서 나오게 한다

- 따로 쓴 문서는 반드시 코드와 어긋난다 – 시간 문제일 뿐이다
- 의존성 한 줄이면 컨트롤러가 곧 문서가 된다
- `@NotBlank` 같은 검증 규칙도 문서에 자동 반영된다

```java
// build.gradle — 이 한 줄이면 /swagger-ui.html이 생긴다
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6'

@Tag(name = "주문", description = "주문 조회·생성")        // 컨트롤러 묶음 이름
@RestController @RequestMapping("/ch02/orders")
class OrderController {

    @Operation(summary = "주문 단건 조회",                  // 목록에 보이는 한 줄
            description = "소유자 조건을 쿼리 안에서 함께 건다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "없거나 남의 주문")})
    OrderResponse find(@Parameter(description = "주문번호", example = "12345")
                        @PathVariable String orderId, ...) { ... }
}

# 확인:  http://localhost:8080/swagger-ui.html   ·   문서(JSON) /v3/api-docs
```

## Swagger UI로 계층을 검증한다

- "Try it out" – curl 없이 요청을 보내고 응답을 본다
- 인증이 걸린 API도 "Authorize" 한 번이면 헤더가 자동으로 붙는다
- 앞에서 배운 계층이 정말 그렇게 도는지 눈으로 확인한다

| 확인할 것 | 무엇을 눌러 보나 | 무엇이 보이면 맞는가 |
|---|---|---|
| 검증 – Controller | item을 비우고 주문 생성 | 400 + 항목별 메시지(서비스는 실행조차 안 된다) |
| 권한 – Service·SQL | user1로 99999 조회 | 404 – 남의 주문은 "없는 것"으로 |
| 동적 조건 – Mapper | status·keyword를 넣었다 뺐다 | 조건 수에 따라 결과가 바뀐다 |
| 집계 – Mapper | /statistics 호출 | SQL 한 번으로 계산된 건수·합계 |
| 인증 – Security | user1로 관리자 API 호출 | 403 – 문서에 적힌 그대로 |
| 응답 시간 | 요청마다 표시되는 duration | AI 호출은 이 숫자가 곧 사용자 경험이다 |

> **확인:** Swagger UI는 "실행되는 문서"다. 프런트·QA·기획이 같은 화면을 보고 이야기하게 되는 것이 가장 큰 이득이다 – 스펙을 두고 다투는 회의가 사라진다.

## 프로파일 – 환경별로 다르게

- 개발·운영의 차이는 코드가 아니라 설정으로 표현한다
- `application-{profile}.yml`이 공통 설정을 덮어쓴다
- `@Profile`로 빈 자체를 갈아 끼울 수도 있다

```yaml
# application.yml — 공통
helpdesk:
  rag: { top-k: 5, threshold: 0.62 }
---
spring.config.activate.on-profile: local
helpdesk:
  rag: { top-k: 3 }          # 로컬은 빠르게
logging.level.com.skala: DEBUG
---
spring.config.activate.on-profile: prod
logging.level.com.skala: INFO
spring.ai.chat.observations.log-prompt: false
```

```java
@Bean
@Profile("!prod")                     // 운영이 아닐 때만
VectorStore devVectorStore(EmbeddingModel m) {
    return SimpleVectorStore.builder(m).build();     // 인메모리
}
```

## 테스트 – 어디까지 띄울까

- 전체 컨텍스트를 띄우는 테스트는 느리고 잘 깨진다
- 슬라이스 테스트로 필요한 계층만 띄운다 – 훨씬 빠르다
- 계층을 나눠 뒀기 때문에 이런 선택이 가능하다

| 방식 | 무엇을 띄우나 | 속도 | 언제 |
|---|---|---|---|
| 단위 테스트 | 아무것도(new로 생성) | 매우 빠름 | 순수 로직 · 도구 |
| `@DataJpaTest` | JPA + DB만 | 빠름 | Repository · Service |
| `@WebMvcTest` | 웹 계층만(서비스는 목) | 빠름 | Controller · 검증 · 매핑 |
| `@SpringBootTest` | 전체 컨텍스트 | 느림 | 빈 조립 확인 · 통합 |

```java
@DataJpaTest
// JPA 관련 빈만 — AI 자동구성은 안 뜬다
@Import(OrderService.class)        // 검증할 서비스만 얹는다
class OrderServiceTest {
    @Autowired OrderService service;
    @Autowired OrderRepository repository;
}
```

## 로깅 – 무엇을 어떻게 남기나

- 로그는 나중의 나를 위한 것이다 – 문제가 났을 때만 읽힌다
- 추적 ID가 없으면 흩어진 로그를 이을 수 없다
- 개인정보를 남기지 않는 것이 AI 서비스에서 특히 중요하다

| 레벨 | 언제 | AI 서비스 예 |
|---|---|---|
| ERROR | 사람이 조치해야 함 | 모델 호출 최종 실패 · 폴백도 실패 |
| WARN | 이상하지만 처리는 됨 | 형식 실패 후 재요청 · 폴백 전환 |
| INFO | 업무 흐름의 이정표 | 도구 호출 · 인제스트 완료 |
| DEBUG | 개발 중 상세 | 검색 결과 · 프롬프트(개발에서만) |

```java
// ❌ 무엇이 실패했는지 알 수 없다
log.error("실패");

// ✅ 추적 ID · 식별자 · 원인을 함께 (개인정보는 제외)
log.error("traceId={} user={} orderId={} 주문 조회 실패",
        traceId, userId, orderId, e);
```

> **주의:** 프롬프트 원문을 INFO로 남기지 마라. 고객이 말한 주문번호·전화번호가 그대로 로그에 쌓이고, 로그는 대개 보존 기간이 길고 접근 범위가 넓다.

## AI는 어느 계층에 두나

- ChatClient를 컨트롤러에서 직접 부르지 않는다 – 가장 흔한 실수
- 공통 관심사는 Advisor, 업무 판단은 Service, 빈 구성은 Config
- 이 경계를 지키면 프롬프트를 고쳐도 업무 코드는 그대로

| 구분 | 내용 |
|---|---|
| 잘못된 예 | `@RestController` → ChatClient 직접 호출 → 프롬프트·도구·예외가 한 파일에 |
| 권장 | `@RestController` → `@Service`(업무 흐름) → ChatClient → Advisor 체인 |
| 근거·행동 | `@Repository` · VectorStore · `@Tool` 클래스 · 외부 API |

- **Controller** – AI를 모른다. 서비스 인터페이스만 본다.
- **Service** – 어떤 도구를 붙일지, 어떤 프롬프트를 쓸지
- **Config + Advisor** – 모델·옵션·공통 관심사 (RAG · 메모리 · 안전 · 감사)

AI는 새로운 계층이 아니라 기존 계층에 얹히는 하나의 관심사다 – 그래서 자리를 정해 줘야 한다.

## AI 계층 – 네 축의 책임

- AI는 하나의 계층이지 하나의 클래스가 아니다
- 바뀌는 이유가 다른 것끼리 따로 둔다 – 그래야 따로 고칠 수 있다
- Controller·Service·Repository 위에 AI 축이 얹히는 구조다

| 패키지 | 무엇을 책임지나 | 바뀌는 이유 | 주로 누가 |
|---|---|---|---|
| config | ChatClient·Advisor 조립 · 기본 옵션 | 모델·공급자 교체 | 백엔드 |
| service | 업무 흐름 · 프롬프트 조립 | 업무 규칙 변경 | 백엔드·기획 |
| rag | 인제스트 · 검색 · 근거 구성 | 문서와 검색 품질 | 데이터 |
| tools | 모델이 부를 수 있는 행동 | 연동 시스템 추가 | 백엔드 |
| advisor | 공통 관심사 – 로깅·안전·메모리 | 정책·감사 요구 | 보안·운영 |
| web | REST·SSE – AI를 모른다 | 화면 요구 | 프런트 |
| eval | 골든 세트 · 품질 기준선 | 품질 목표 | QA |

## AI 요청의 여정 – `/api/chat` 한 번

- 앞서 본 계층 왕복 위에 AI 축이 얹힌 모습이다
- 순서가 곧 정책이다 – 차단은 저장보다 앞에 있어야 한다
- 느리거나 비싸면 어느 구간인지를 이 그림에서 찾는다

```
POST /api/chat   {"question":"주문 12345 반품 되나요?"}

① web/ChatController       인증 확인 → 질문·세션 ID만 서비스로 넘긴다
② advisor/AuditAdvisor     감사 기록 시작 (order 0 — 가장 바깥)
③ advisor/SafetyAdvisor    입력 차단 — 민감어·인젝션 (저장보다 반드시 먼저)
④ advisor/MemoryAdvisor    같은 세션의 앞 대화를 붙인다
⑤ rag/RetrievalService     질문으로 문서 검색 → 근거를 프롬프트에
⑥ chat/HelpDeskService     프롬프트 조립 → 모델 호출
⑦ tools/OrderTools         모델이 필요하다고 판단하면 호출
   repository/OrderRepo    ↳ 권한 검증과 실제 데이터는 결국 아래 계층에서
⑧ advisor/TokenMeter       토큰·지연 기록 → 지표
⑨ chat/AnswerDto           답변 + 출처 + 도구 사용 여부로 조립해 반환

# 실패도 계층마다 얼굴이 다르다
#   401 인증(①) · 차단(③) · 근거 없음(⑤) · 도구 권한(⑦) · 타임아웃(⑥)
```

## 실습 코드 – 3계층 완성본 (간식 추천)

- 세 파일이면 계층이 완성된다 – 그대로 따라친다
- `GET /lab0/snack?mood=피곤` → "초코바 · 당 충전"
- 이 코드로 표의 ②④ 실험을 해 본다

```java
// ① Controller — 요청을 받아 서비스에 넘기기만 한다
@RestController @RequestMapping("/lab0/snack")
class SnackController {
    private final SnackService service;                 // 저장소는 모른다
    SnackController(SnackService service) { this.service = service; }

    @GetMapping                                          // GET /lab0/snack?mood=피곤
    SnackResponse pick(@RequestParam String mood) { return service.recommend(mood); }
}

// ② Service — '무엇을 하는가'는 여기에만 적는다
@Service
class SnackService {
    private final SnackRepository repo;
    SnackService(SnackRepository repo) { this.repo = repo; }

    SnackResponse recommend(String mood) {
        Snack s = repo.findByMood(mood)                  // ③ 데이터는 저장소에서만
                .orElse(new Snack("아메리카노", "무난하게"));
        return new SnackResponse(s.name(), s.reason());  // ④ 나갈 때는 DTO로
    }
}

record SnackResponse(String name, String reason) {}      // 밖으로 나가는 모양

// 실행 →  {"name":"초코바","reason":"당 충전"}
```

## 실행·테스트 – 간식 추천 3계층

- 이 실습은 키가 없어도 끝까지 돌아간다 – AI 없이 계층만 확인한다
- 컨트롤러 → 서비스 → 응답, 이 왕복이 눈에 보이면 성공이다
- 테스트는 서비스만 떼어 내 검증한다 – 웹 계층 없이 된다

```
# 1) 파일 위치 — 이 폴더에 이미 들어 있다
src/main/java/com/skala/lab0/
  web/SnackController.java   service/SnackService.java   SnackResponse.java
#    → 실행: SpringAI_실습/01_간식추천_3계층 폴더를 VS Code로 열고 F5 (또는 ./gradlew bootRun)

# 2) 호출 (셋 중 편한 것)
curl 'localhost:8080/lab0/snack?mood=피곤'
http/samples.http 에 한 줄 추가해 [Send Request]
http://localhost:8080/swagger-ui.html  →  Day1 실습 태그

# 3) 기대 결과
{"name":"초코바","reason":"당 충전"}          # mood를 바꾸면 답도 바뀐다
```

```java
// 4) 테스트로 굳히기 — AI를 안 쓰므로 키 없이 돈다
@WebMvcTest(SnackController.class)
class SnackControllerTest {
    @Autowired MockMvc mvc;  @MockitoBean SnackService service;

    @Test void 추천이_내려온다() throws Exception {
        given(service.recommend("피곤")).willReturn(new SnackResponse("초코바","당 충전"));
        mvc.perform(get("/lab0/snack").param("mood","피곤"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("초코바"));
    }
}
```

```
# 안 되면 —
404: 경로 오타 · 500: 저장소 빈 미등록 · 한글 깨짐: files.encoding utf8
```

## 핵심 요약 – Spring Boot 계층 구조

- 이 장의 결론은 하나 – 역할을 먼저 정하고 어노테이션은 따라온다
- AI 코드도 같은 규칙을 따른다

| 항목 | 한 줄 정리 | 실무 포인트 |
|---|---|---|
| 계층 분리 | 변경의 파급을 가두기 위해 나눈다 | 위→아래로만 호출, 역방향 금지 |
| `@RestController` | 받고·검증하고·돌려준다 | if가 쌓이면 서비스로 옮길 신호 |
| `@Service` | 업무 흐름과 트랜잭션 경계 | 클래스 readOnly, 쓰기만 재정의 |
| `@Repository` | 데이터에 닿는 유일한 곳 | 권한 조건은 쿼리 안에 |
| Mapper(MyBatis) | SQL이 주인공인 조회를 맡는다 | Repository와 같은 자리, 다른 방식 |
| DTO | 계층 사이의 방화벽 | 엔티티를 API로 내보내지 않는다 |
| 생성자 주입 | 의존성이 시그니처에 드러난다 | 5개 넘으면 책임 과다 신호 |
| `@Bean` | 라이브러리 타입을 빈으로 | 파라미터도 주입 – AI 설정의 기본형 |
| AI 배치 | Config·Advisor·Service로 분산 | 컨트롤러는 AI를 모른다 |
| 변환 | 엔티티 ↔ DTO는 한 곳에서 | 입구가 둘이어도 출구는 하나 |
| API 문서 | 코드에서 생성 – Swagger UI로 시험 | 운영 프로파일에서는 닫는다 |

> **체크:** 다음 장으로 넘어가기 전에 – "ChatClient는 어느 클래스에 주입되어야 하는가?" 이 질문에 바로 답할 수 있으면 준비된 것이다.
