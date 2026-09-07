package com.example.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.service.DecisionService;
import com.example.service.WeatherService;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class WeatherController {

    private final WeatherService weatherService;
    private final DecisionService decisionService;

    public WeatherController(
            WeatherService weatherService,
            DecisionService decisionService) {

        this.weatherService = weatherService;
        this.decisionService = decisionService;
    }

    @GetMapping("/weather")
    public JsonNode getWeather(
            @RequestParam String city) throws Exception {

        return weatherService.getWeather(city);
    }

    @GetMapping("/weather/analyze")
    public String analyzeWeather(
            @RequestParam String city) throws Exception {

        JsonNode weather = weatherService.getWeather(city);

        return decisionService.analyzeWeather(weather);
    }
}