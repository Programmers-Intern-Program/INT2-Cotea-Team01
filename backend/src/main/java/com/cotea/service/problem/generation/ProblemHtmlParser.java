package com.cotea.service.problem.generation;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/**
 * 프로그래머스 문제 상세 페이지(예: https://school.programmers.co.kr/learn/courses/30/lessons/{id})의
 * raw HTML을 받아 생성 파이프라인 입력으로 쓸 수 있는 형태로 파싱한다.
 *
 * <p>problemId/title/level은 문제 본문을 읽고 추론하는 게 아니라, 페이지 마크업 자체에 있는
 * data 속성({@code div.lesson-content}의 data-lesson-id/data-lesson-title/data-challenge-level)을
 * 그대로 읽는다. 2026-07-23에 로그인/비로그인 두 상태의 raw HTML로 직접 확인함 — JS 실행이나
 * 헤드리스 브라우저 없이 단순 HTTP GET + HTML 파싱만으로 충분하다.
 */
@Component
public class ProblemHtmlParser {

    private static final String LESSON_URL_PREFIX = "https://school.programmers.co.kr/learn/courses/30/lessons/";

    public ParsedProblem parse(String rawHtml) {
        Document doc = Jsoup.parse(rawHtml);

        Element lessonContent = doc.selectFirst("div.lesson-content");
        if (lessonContent == null) {
            throw new ProblemHtmlParseException(
                    "div.lesson-content를 찾을 수 없습니다 — 프로그래머스 문제 상세 페이지 HTML이 맞는지 확인하세요.");
        }

        int problemId = parseProblemId(lessonContent);
        String title = requireAttr(lessonContent, "data-lesson-title", problemId);
        String level = "Lv" + requireAttr(lessonContent, "data-challenge-level", problemId);

        Element descriptionNode = doc.selectFirst("div.guide-section-description div.markdown");
        if (descriptionNode == null) {
            throw new ProblemHtmlParseException(
                    "문제 설명 영역(div.guide-section-description div.markdown)을 찾을 수 없습니다. problemId=" + problemId);
        }

        return new ParsedProblem(
                problemId,
                title,
                level,
                LESSON_URL_PREFIX + problemId,
                descriptionNode.text(),
                descriptionNode.html(),
                extractImageUrls(descriptionNode));
    }

    private int parseProblemId(Element lessonContent) {
        String raw = lessonContent.attr("data-lesson-id");
        if (raw.isBlank()) {
            throw new ProblemHtmlParseException("data-lesson-id 속성이 비어있습니다.");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ProblemHtmlParseException("data-lesson-id 값이 숫자가 아닙니다: " + raw);
        }
    }

    private String requireAttr(Element element, String attrName, int problemId) {
        String value = element.attr(attrName);
        if (value.isBlank()) {
            throw new ProblemHtmlParseException(attrName + " 속성이 비어있습니다. problemId=" + problemId);
        }
        return value;
    }

    private List<String> extractImageUrls(Element descriptionNode) {
        List<String> imageUrls = new ArrayList<>();
        Elements images = descriptionNode.select("img[src]");
        for (Element img : images) {
            String absoluteUrl = img.absUrl("src");
            imageUrls.add(absoluteUrl.isBlank() ? img.attr("src") : absoluteUrl);
        }
        return imageUrls;
    }
}
