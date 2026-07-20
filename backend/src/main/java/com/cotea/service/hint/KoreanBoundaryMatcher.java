package com.cotea.service.hint;

import java.util.Set;

/**
 * 자바 정규식의 단어 경계(\b)는 한글을 단어 문자로 인식하지 못해, 한국어 문장에서
 * 단순 {@code contains()} 검사는 "큐브"에서 "큐"가 매칭되는 것처럼 전혀 다른 단어를
 * term이 실제로 쓰인 것으로 오인한다.
 *
 * <p>term 뒤에 오는 글자가 문장 끝/공백/구두점이거나, 미리 정의한 한국어 조사로 시작할 때만
 * "term이 단독으로 쓰였다"고 인정한다. term 앞쪽도 같은 기준(한글/영숫자가 아닌 경계)으로 확인한다.
 */
final class KoreanBoundaryMatcher {

    private static final Set<String> KOREAN_PARTICLES = Set.of(
            "는", "은", "이", "가", "을", "를", "와", "과", "도", "만",
            "에서", "에", "으로", "로", "이나", "나", "이란", "란", "이라",
            "이랑", "랑", "까지", "부터", "마다", "처럼", "보다", "이고", "고",
            // "때문"처럼 조사가 아니라 서술격 조사(이다) 활용형으로 끝나는 의존명사 대응
            "이다", "이었다", "였다", "이야", "일"
    );

    private KoreanBoundaryMatcher() {
    }

    static boolean containsAsStandaloneTerm(String text, String term) {
        if (text == null || term == null || term.isEmpty()) {
            return false;
        }
        int from = 0;
        int idx;
        while ((idx = text.indexOf(term, from)) != -1) {
            if (isBoundaryBefore(text, idx) && isBoundaryAfter(text, idx + term.length())) {
                return true;
            }
            from = idx + 1;
        }
        return false;
    }

    private static boolean isBoundaryBefore(String text, int start) {
        if (start == 0) {
            return true;
        }
        return isBoundaryChar(text.charAt(start - 1));
    }

    private static boolean isBoundaryAfter(String text, int end) {
        if (end >= text.length()) {
            return true;
        }
        if (isBoundaryChar(text.charAt(end))) {
            return true;
        }
        return startsWithParticle(text, end);
    }

    private static boolean startsWithParticle(String text, int from) {
        for (String particle : KOREAN_PARTICLES) {
            if (text.startsWith(particle, from)) {
                int after = from + particle.length();
                if (after >= text.length() || isBoundaryChar(text.charAt(after))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isBoundaryChar(char c) {
        return !isHangulSyllable(c) && !Character.isLetterOrDigit(c);
    }

    private static boolean isHangulSyllable(char c) {
        return c >= 0xAC00 && c <= 0xD7A3;
    }
}
