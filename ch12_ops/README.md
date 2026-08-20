# ch12_ops — 교재 13장 · 운영 — 재시도 · 폴백 · 시맨틱 캐시

`@Retryable`/`@Recover` 폴백, 뜻이 같은 질문을 재사용하는 시맨틱 캐시.

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
| `FallbackChatService.java` | 지수 백오프 재시도(1s→2s→4s) → 폴백 모델 → 정직한 안내 |
| `SemanticCacheService.java` | 질문을 임베딩해 유사한 과거 질문의 답을 재사용 |
| `Ch12Application.java` | `@EnableRetry` — 이게 없으면 `@Retryable` 이 그냥 무시된다 |

**재시도 대상은 일시적 오류(타임아웃·5xx·레이트 리밋)뿐이다.**
잘못된 요청(4xx)을 재시도하면 같은 실패를 돈 주고 반복할 뿐이다.

**시맨틱 캐시의 두 가지 주의**
- 임계값을 낮추면 다른 질문에 엉뚱한 답이 나간다. **0.95 이상에서 시작한다.**
- 개인화·실시간 데이터가 섞인 답변("내 주문 상태")은 **절대 캐시하지 않는다.**

---

## 실행해 보기

```bash
# 재시도·폴백
curl 'localhost:8080/ch12/ask?q=배송 지연 시 보상 규정을 알려줘'

# 시맨틱 캐시 — 뜻이 같은 두 질문
curl 'localhost:8080/ch12/semantic?q=배송은 얼마나 걸리나요'
curl 'localhost:8080/ch12/semantic?q=배송 기간 알려주세요'
#   → 두 번째는 즉시 응답, hits 가 오른다

# 뜻이 다른 질문은 적중하지 않는다
curl 'localhost:8080/ch12/semantic?q=환불은 어떻게 하나요'
```

> **확인해 볼 것** — 폴백 경로를 실제로 보려면 `OPENAI_API_KEY` 를 잘못된 값으로
> 두고 `/ch12/ask` 를 불러 보라. 재시도 로그가 찍힌 뒤 마지막에
> `지금은 답변을 드리기 어렵습니다` 가 나온다. **앱은 죽지 않는다** — 그것이 요점이다.

같은 요청이 **`http/ch12_ops.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- 공용 인메모리 `VectorStoreConfig` 는 이 장이 쓰지 않아 뺐다(시맨틱 캐시는 전용 스토어를 직접 만든다). 필요한 장(2·8·9·12·14장)이 각자 갖고 있다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
