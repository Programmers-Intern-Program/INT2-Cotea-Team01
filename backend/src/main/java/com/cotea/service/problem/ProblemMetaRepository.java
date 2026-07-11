package com.cotea.service.problem;

import com.cotea.service.problem.entity.ProblemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemMetaRepository extends JpaRepository<ProblemEntity, Integer> {
}
