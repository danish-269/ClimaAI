package com.example.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.ChatRequest;
import com.example.dto.ChatResponse;
import com.example.service.ChatService;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request)
            throws Exception {

        if (request.getMessage() == null
                || request.getMessage().isBlank()) {

            return ResponseEntity.badRequest().build();
        }

        ChatResponse response =
                chatService.processMessage(
                        request.getMessage()
                );

        return ResponseEntity.ok(response);
    }

    // Keep our old GET endpoint for testing
    @GetMapping("/ask")
    public String ask(
            @RequestParam String message)
            throws Exception {

        return chatService
                .askWeatherAssistantFromMessage(message);
    }
}