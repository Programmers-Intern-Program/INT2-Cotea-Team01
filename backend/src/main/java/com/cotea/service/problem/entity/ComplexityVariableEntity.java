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
@Table(name = "complexity_variable")
@NoArgsConstructor
public class ComplexityVariableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "problem_id")
    private Integer problemId;

    @Column(name = "variable_name")
    private String variableName;

    @Column(name = "variable_description")
    private String variableDescription;
}
