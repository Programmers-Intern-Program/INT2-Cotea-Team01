package com.cotea.service.problem.generation;

import java.util.List;

public record ValidationResult(boolean valid, List<String> errors) {
}
