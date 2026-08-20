package com.skala.ch03;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 2장 — ChatClient 기본 사용법.
 *
 * <p>같은 타입의 빈이 여러 개이므로 {@code @Qualifier} 로 이름을 지정해 주입한다.
 */
@Service
public class HelloAiService {

    private final ChatClient summary;
    private final ChatClient idea;

    public HelloAiService(@Qualifier("summaryChatClient") ChatClient summary,
                          @Qualifier("ideaChatClient") ChatClient idea) {
        this.summary = summary;
        this.idea = idea;
    }

    /** 요약 요청 */
    public String summarize(String text) {
        return summary.prompt()
                .user(u -> u.text("다음 문장을 요약하라. 요약문만 출력한다.\n{text}")
                        .param("text", text))
                .call()
                .content();
    }

    // /** 호출 시점에 system 메시지를 덧붙일 수도 있다(빈 기본값에 더해진다). */
    // public String askAs(String role, String question) {
    //     return summary.prompt()
    //             .system("이번 답변에 한해 너는 " + role + " 관점으로 설명한다.")
    //             .user(question)
    //             .call()
    //             .content();
    // }

    /**
     * 템플릿 파라미터 바인딩 — 문자열 연결 대신 {@code {변수}} 를 쓴다.
     * 사용자 입력을 그대로 이어 붙이면 프롬프트 인젝션 표면이 넓어진다.
     */
    public String ideating(String theme, String keywords) {
        return idea.prompt()
                .user(u -> u.text("다음 키워드들에 대해 {theme} 관련 아이디어를 뽑아내 봐라. 키워드가 영어여도 한글로 출력한다. \n{keywords}")
                        .param("theme", theme)
                        .param("keywords", keywords))
                .call()
                .content();
    }
}
