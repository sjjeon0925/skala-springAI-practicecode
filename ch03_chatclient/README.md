# ch03_chatclient — 교재 4장 · ChatClient 와 용도별 빈

용도별 ChatClient 빈, 템플릿 파라미터 바인딩.

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
| `ChatClientConfig.java` | 추출용(temperature 0) · 상담용(0.7) 빈을 나눠 만든다 |
| `HelloAiService.java` | `@Qualifier` 주입 · 호출 시점 system 추가 · `{변수}` 바인딩 |
| `ChatClientController.java` | `/ch03/**` |

**왜 빈을 나누나** — 하나의 ChatClient 로 모든 일을 시키면 기본값이 서로 충돌한다.
추출은 흔들리면 안 되고(0), 상담은 자연스러워야 한다(0.7). 빈으로 나누면
호출부가 옵션을 매번 덮어쓸 필요가 없다.

> 이 `ChatClientConfig` 는 4장 이후 대부분의 장이 그대로 쓴다.
> 각 장 프로젝트가 같은 파일을 복사해 갖고 있는 이유다.

---

## 실행해 보기

```bash
curl 'localhost:8080/ch03/ask?q=Spring AI를 한 문장으로 설명해줘'
curl 'localhost:8080/ch03/ask-as?role=보안 담당자&q=RAG 도입 시 주의점은'
curl 'localhost:8080/ch03/translate?text=오늘 배송이 지연되었습니다&lang=영어'
```

> **확인해 볼 것** — `translate` 는 문자열을 이어 붙이지 않고 `{lang}`·`{text}` 로
> 바인딩한다. 사용자 입력을 그대로 이어 붙이면 프롬프트 인젝션 표면이 넓어진다.

같은 요청이 **`http/ch03_chatclient.http`** 에 들어 있다 — VS Code 의 REST Client 확장에서
각 요청 위의 **[Send Request]** 를 누르면 curl 없이 응답을 볼 수 있다.

---

## 참고

- 버전은 Spring Boot 3.5.16 · Spring AI 1.1.8(`build.gradle` 의 `springAiVersion` 한 줄).
- 다른 장은 `../chNN_*/` 에, 실습 과제는 `../../01_*` ~ `../../17_*` 에 있다.
