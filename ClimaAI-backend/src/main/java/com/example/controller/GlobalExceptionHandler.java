package com.example.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> handleBadRequest(
                        IllegalArgumentException e) {

                System.err.println("BAD REQUEST: " + e.getMessage());
                e.printStackTrace();

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(Map.of(
                                                "error",
                                                e.getMessage()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, String>> handleGeneralError(
                        Exception e) {

                System.err.println("========================================");
                System.err.println("CLIMAAI BACKEND ERROR");
                System.err.println("Exception type: " + e.getClass().getName());
                System.err.println("Message: " + e.getMessage());
                System.err.println("========================================");

                e.printStackTrace();

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                                "error",
                                                "Unable to process the weather request right now."));
        }
}