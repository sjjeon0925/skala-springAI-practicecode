package com.skala.ch05;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

/**
 * 4장 — 멀티모달(이미지 입력).
 *
 * <p>이미지는 토큰을 많이 쓴다. 보내기 전에 해상도를 줄이는 것만으로 비용이 눈에 띄게 준다.
 * 그리고 모든 모델이 이미지를 받는 것은 아니다 — 비전 지원 모델인지 먼저 확인한다.
 */
@Service
public class MultimodalService {

    private final ChatClient chat;

    public MultimodalService(@Qualifier("supportClient") ChatClient chat) {
        this.chat = chat;
    }

    public record ReceiptInfo(String merchant, String date, Integer totalAmount, List<String> items) {}

    /** 이미지에 대해 묻는다 — 텍스트 프롬프트 + 이미지 미디어. */
    public String describe(Resource image, MimeType mimeType, String question) {
        return chat.prompt()
                .user(u -> u.text(question).media(mimeType, image))
                .call()
                .content();
    }

    /** 업로드된 영수증에서 구조화된 정보를 뽑는다 — 멀티모달 + 구조화 출력의 결합. */
    public ReceiptInfo readReceipt(MultipartFile file) {
        MimeType mimeType = file.getContentType() == null
                ? MediaType.IMAGE_PNG
                : MediaType.parseMediaType(file.getContentType());

        return chat.prompt()
                .user(u -> u.text("""
                                이 영수증 이미지에서 상호명·날짜·총액·품목을 추출하라.
                                - 총액은 원 단위 정수만 쓴다(쉼표·통화기호 제외).
                                - 읽을 수 없는 값은 null 로 둔다. 추측하지 않는다.""")
                        .media(mimeType, file.getResource()))
                .call()
                .entity(ReceiptInfo.class);
    }
}
