# ch04_prompt — 교재 5장 · 프롬프트와 스트리밍

프롬프트 4요소 · Few-shot · CoT · 리소스 템플릿 · 스트리밍 취소/타임아웃.

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
| `PromptService.java` | 역할·맥락·지시·형식 / Few-shot / CoT / `classpath` 템플릿 / 호출별 옵션 |
| `StreamingService.java` | TTFB 계측 · 60초 상한 · 구독 취소 정리 · 오류 폴백 |
| `resources/prompts/code-review.st` | 긴 프롬프트는 파일로 뺀다 — 리뷰되고 diff 가 남는다 |
| `ChatClientConfig.java` | (4장에서 복사) 용도별 빈 |

---

## 실행해 보기

```bash
# Few-shot — 설명하지 말고 예시로 형식을 고정한다
curl 'localhost:8080/ch04/classify?q=결제가 두 번 됐어요'          # BILLING
curl 'localhost:8080/ch04/classify?q=아직도 배송이 안 왔는데요'      # DELIVERY

# CoT — 단계를 밟게 하되 최종 결과만 노출한다
curl 'localhost:8080/ch04/reason?q=주문이 3주째 배송중인데 원인이 뭘까'

# 리소스 파일 템플릿
curl -X POST 'localhost:8080/ch04/review?lang=Java' -H 'Content-Type: text/plain' \
     -d 'public String get(String id){ return repo.findById(id).get().getName(); }'

# 스트리밍 — -N 으로 버퍼링을 끈다
curl -N 'localhost:8080/ch04/stream?q=Spring AI를 소개해줘'
```

> **확인해 볼 것** — 스트리밍 중 <kbd>Ctrl+C</kbd> 로 끊고 서버 로그를 보라.
> `클라이언트 취소 — 스트림 종료` 가 찍힌다. 이 정리가 없으면 사용자가 창을 닫아도
> 모델 호출은 끝까지 진행되고 그대로 비용이 된다.

같은 요청이 **`http/ch04_prompt.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고
- `ChatClientConfig` 를 4장 프로젝트에서 복사해 왔다.

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
