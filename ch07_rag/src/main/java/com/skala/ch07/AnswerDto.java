package com.skala.ch07;

import java.util.List;

public record AnswerDto(String answer, List<String> sources, boolean grounded) {

    public static AnswerDto unknown() {
        return new AnswerDto("확인되지 않습니다.", List.of(), false);
    }
}
