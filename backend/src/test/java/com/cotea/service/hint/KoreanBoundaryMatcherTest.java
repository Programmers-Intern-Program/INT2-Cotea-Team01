package com.cotea.service.hint;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KoreanBoundaryMatcherTest {

    @Test
    void doesNotMatchWhenTermIsPartOfAnotherWord() {
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("큐브 모양으로 생각해보세요", "큐")).isFalse();
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("스택형 자료구조가 아니어도", "스택")).isFalse();
    }

    @Test
    void matchesWhenTermIsFollowedByParticleWithoutSpace() {
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("이 문제는 큐를 사용하면 됩니다", "큐")).isTrue();
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("BFS로 접근해보세요", "BFS")).isTrue();
    }

    @Test
    void matchesMultiWordTermFollowedByParticle() {
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("유니온 파인드를 써보세요", "유니온 파인드")).isTrue();
    }

    @Test
    void matchesWhenTermIsAtEndOfSentence() {
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("이 문제는 DFS", "DFS")).isTrue();
    }

    @Test
    void matchesWhenTermIsFollowedBySpaceOrPunctuation() {
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("큐 자료구조를 써보세요", "큐")).isTrue();
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("스택, 큐 둘 다 가능합니다", "스택")).isTrue();
    }

    @Test
    void matchesBoundNounFollowedByCopulaForm() {
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("시간 초과는 반복문 때문이다.", "때문")).isTrue();
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("경계 조건 때문일 수도 있어요.", "때문")).isTrue();
    }

    @Test
    void returnsFalseWhenTermOrTextIsBlank() {
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm(null, "큐")).isFalse();
        assertThat(KoreanBoundaryMatcher.containsAsStandaloneTerm("큐를 써보세요", "")).isFalse();
    }
}
