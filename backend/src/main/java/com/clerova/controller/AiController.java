package com.clerova.controller;

import com.clerova.ai.AiService;
import com.clerova.dto.AiChatRequest;
import com.clerova.dto.AiChatResponse;
import com.clerova.dto.ExtractionRequest;
import com.clerova.dto.ExtractionResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {

        String response = aiService.chat(request.message());
        return new AiChatResponse(response);
    }

    @PostMapping("/extract")
    public ExtractionResult extract(
            @Valid @RequestBody ExtractionRequest request
    ) {
        return aiService.extract(request.text());
    }
}
