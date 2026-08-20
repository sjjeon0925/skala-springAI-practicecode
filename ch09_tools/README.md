# ch09_tools — 교재 10장 · Tool Calling

`@Tool` · `ToolContext` · 소유자 검증.

**혼자 돈다.** 이 폴더만 열어 `./gradlew bootRun` 하면 끝이고, 다른 장 폴더를 참조하지 않는다.

---

## 실행

```bash
export OPENAI_API_KEY="sk-..."   # 소스·깃에 절대 커밋하지 않는다
./gradlew bootRun          # http://localhost:8080
```

- Swagger UI — <http://localhost:8080/swagger-ui.html> ([Try it out] 으로 curl 없이 호출)
- 포트를 바꾸려면 `./gradlew bootRun --args='--server.port=8081'`

> 키가 없어도 **앱은 뜬다**(`${OPENAI_API_KEY:not-set}`). 모델을 실제로 부르는
> 엔드포인트에서만 401 이 난다 — Swagger 를 열어 구조부터 둘러보는 데는 지장이 없다.

---

## 무엇이 들어 있나

| 파일 | 무엇을 보나 |
| --- | --- |
| `WeatherTools.java` | 가장 단순한 도구 — 모델이 **필요할 때만** 부른다 |
| `OrderTools.java` | `ToolContext` 로 받은 사용자 ID 로 **소유자 검증** |
| `ToolChatService.java` | `.tools(...)` · `.toolContext(...)` · 응답 메타데이터 확인 |

**도구를 만들 때의 세 원칙**

1. `description` 이 곧 모델에게 주는 사용 설명서다. 대충 쓰면 엉뚱하게 부른다.
2. 예외를 던지지 말고 사람이 읽을 메시지를 반환한다 — 대화 전체가 실패하지 않는다.
3. **권한 검증은 도구 안에서** 한다. 모델이 넘긴 ID 를 믿지 않는다.

---

## 실행해 보기

```bash
# 도구가 필요 없으면 그냥 답한다
curl 'localhost:8080/ch09/ask?q=안녕하세요'

# 필요하면 부른다
curl 'localhost:8080/ch09/ask?q=서울 날씨 어때'
curl 'localhost:8080/ch09/ask?q=주문 12345 어디쯤이야'

# 남의 주문 — 차단된다
curl 'localhost:8080/ch09/ask?q=주문 99999 알려줘'
#   → "찾을 수 없습니다" (없는 주문과 구분하지 않는다 — 구분하면 존재가 새어 나간다)

# 도구가 실제로 불렸는지 · 토큰이 얼마나 들었는지
curl 'localhost:8080/ch09/ask-verbose?q=서울 날씨 어때'
```

> **확인해 볼 것** — 서버 로그의 `[TOOL] ...` 줄. `안녕하세요` 에는 아예 찍히지 않는다.
> **도구를 등록해도 모델이 항상 부르는 것은 아니다.**

### 테스트

```bash
./gradlew test
```

`OrderToolsTest` 는 모델 없이 **권한 검증만** 직접 확인한다. 이 검증은 모델을
거치지 않고 보는 편이 확실하다.

같은 요청이 **`http/ch09_tools.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- `ChatClientConfig` 를 4장 프로젝트에서 복사해 왔다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
