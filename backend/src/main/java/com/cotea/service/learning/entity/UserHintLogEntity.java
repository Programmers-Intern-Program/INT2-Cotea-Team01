package com.cotea.service.learning.entity;

import com.cotea.service.learning.DetectedIntent;
import com.cotea.service.learning.WeaknessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_hint_log")
@NoArgsConstructor
public class UserHintLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "problem_id", nullable = false)
    private Integer problemId;

    @Column(name = "problem_title", length = 255)
    private String problemTitle;

    @Column(name = "problem_level", length = 50)
    private String problemLevel;

    @Lob
    @Column(name = "problem_tags")
    private String problemTags;

    @Column(length = 30, nullable = false)
    private String stage;

    @Column(name = "hint_level")
    private Integer hintLevel;

    @Column(name = "question_type", length = 30)
    private String questionType;

    @Column(name = "button_id", length = 80)
    private String buttonId;

    @Lob
    @Column(name = "question_text")
    private String questionText;

    @Column(name = "submission_result", length = 50)
    private String submissionResult;

    @Column(length = 50)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "weakness_type", length = 30)
    private WeaknessType weaknessType;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_intent", length = 50)
    private DetectedIntent detectedIntent;

    @Column(name = "code_length")
    private Integer codeLength;

    @Column(name = "code_line_count")
    private Integer codeLineCount;

    @Column(name = "prompt_policy_version", length = 30)
    private String promptPolicyVersion;

    @Lob
    @Column(name = "selected_problem_fields")
    private String selectedProblemFields;

    @Column(length = 30)
    private String route;

    @Column(name = "llm_provider", length = 50)
    private String llmProvider;

    @Column
    private Boolean solved;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static UserHintLogEntity create(CreateCommand command) {
        UserHintLogEntity log = new UserHintLogEntity();
        log.userId = command.userId();
        log.problemId = command.problemId();
        log.problemTitle = command.problemTitle();
        log.problemLevel = command.problemLevel();
        log.problemTags = command.problemTags();
        log.stage = command.stage();
        log.hintLevel = command.hintLevel();
        log.questionType = command.questionType();
        log.buttonId = command.buttonId();
        log.questionText = command.questionText();
        log.submissionResult = command.submissionResult();
        log.language = command.language();
        log.weaknessType = command.weaknessType();
        log.detectedIntent = command.detectedIntent();
        log.codeLength = command.codeLength();
        log.codeLineCount = command.codeLineCount();
        log.promptPolicyVersion = command.promptPolicyVersion();
        log.selectedProblemFields = command.selectedProblemFields();
        log.route = command.route();
        log.llmProvider = command.llmProvider();
        log.solved = command.solved();
        return log;
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public record CreateCommand(
            Long userId,
            Integer problemId,
            String problemTitle,
            String problemLevel,
            String problemTags,
            String stage,
            Integer hintLevel,
            String questionType,
            String buttonId,
            String questionText,
            String submissionResult,
            String language,
            WeaknessType weaknessType,
            DetectedIntent detectedIntent,
            Integer codeLength,
            Integer codeLineCount,
            String promptPolicyVersion,
            String selectedProblemFields,
            String route,
            String llmProvider,
            Boolean solved
    ) {
    }
}
