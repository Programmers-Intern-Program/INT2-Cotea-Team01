package com.cotea.controller;

import com.cotea.controller.dto.HintRequest;
import com.cotea.controller.dto.HintResponse;
import com.cotea.service.hint.HintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HintController {

    private final HintService hintService;

    @PostMapping("/hint")
    public HintResponse hint(@Valid @RequestBody HintRequest request) {
        return hintService.generate(request);
    }
}
