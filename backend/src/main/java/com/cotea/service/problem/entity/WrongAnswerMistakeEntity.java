package com.cotea.service.problem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "wrong_answer_mistake")
@NoArgsConstructor
public class WrongAnswerMistakeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "problem_id")
    private Integer problemId;

    private String symptom;

    @Column(name = "likely_cause")
    private String likelyCause;

    @Column(name = "direction_hint")
    private String directionHint;
}
