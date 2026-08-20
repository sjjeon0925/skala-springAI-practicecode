package com.skala.ch04;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 3장 — 프롬프트 엔지니어링.
 *
 * <p>프롬프트는 가장 값싼 지렛대다. 모델을 바꾸기 전에 프롬프트부터 손본다.
 */
@Service
public class PromptService {

    private final ChatClient chat;

    /** 긴 프롬프트는 리소스 파일로 뺀다 — 리뷰되고, diff 가 남는다. */
    @Value("classpath:/prompts/code-review.st")
    private Resource codeReviewPrompt;

    public PromptService(@Qualifier("supportClient") ChatClient chat) {
        this.chat = chat;
    }

    /** ① 좋은 프롬프트의 네 요소 — 역할 · 맥락 · 지시 · 형식. */
    public String structuredPrompt(String article) {
        return chat.prompt()
                .system("""
                        [역할] 너는 10년차 기술 에디터다.
                        [형식] 반드시 아래 형식만 출력한다.
                          제목: <한 줄>
                          요약: <3문장>
                          키워드: <쉼표로 구분한 5개>""")
                .user(u -> u.text("""
                        [맥락] 아래는 사내 기술 블로그에 올릴 초고다.
                        [지시] 제목·요약·키워드를 만들어라.
                        ---
                        {article}""").param("article", article))
                .call()
                .content();
    }

    /** ② Few-shot — 설명하지 말고 예시로 형식을 고정한다. */
    public String classifyWithExamples(String inquiry) {
        List<Message> messages = List.of(
                new SystemMessage("문의를 BILLING · DELIVERY · REFUND · ETC 중 하나로 분류한다. 라벨만 출력한다."),
                new UserMessage("카드가 두 번 결제됐어요"), new AssistantMessage("BILLING"),
                new UserMessage("아직도 배송이 안 왔는데요"), new AssistantMessage("DELIVERY"),
                new UserMessage("반품하고 돈 돌려받고 싶어요"), new AssistantMessage("REFUND"),
                new UserMessage(inquiry));

        return chat.prompt()
                .messages(messages)
                .options(ChatOptions.builder().temperature(0.0).build())
                .call()
                .content();
    }

    /**
     * ③ Chain-of-Thought — 단계를 밟게 하되 최종 결과만 노출한다.
     * 사고 과정을 그대로 보여 주면 길고 비싸며, 내부 판단이 새어 나간다.
     */
    public String reasonThenAnswer(String problem) {
        return chat.prompt()
                .system("""
                        먼저 단계별로 근거를 정리한 뒤 결론을 내려라.
                        단, 출력은 아래 형식만 사용한다. 중간 사고 과정은 출력하지 않는다.
                          결론: <한 문장>
                          핵심근거: <3개 불릿>""")
                .user(problem)
                .call()
                .content();
    }

    /** ④ 리소스 파일 템플릿 사용 — 프롬프트를 코드 밖으로. */
    public String reviewCode(String code, String language) {
        return chat.prompt()
                .user(u -> u.text(codeReviewPrompt)
                        .param("code", code)
                        .param("lang", language))
                .call()
                .content();
    }

    /** ⑤ 작업별 옵션 — 같은 클라이언트라도 호출마다 조절할 수 있다. */
    public String creative(String topic, double temperature) {
        return chat.prompt()
                .user("다음 주제로 짧은 카피 3개를 써라: " + topic)
                .options(ChatOptions.builder()
                        .temperature(temperature)
                        .maxTokens(300)
                        .build())
                .call()
                .content();
    }
}
