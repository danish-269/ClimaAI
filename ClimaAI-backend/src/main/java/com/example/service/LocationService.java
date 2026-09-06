package com.example.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class LocationService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LocationService() {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    // =========================================================
    // EXTRACT CITY FROM USER MESSAGE
    // =========================================================

    public String extractCity(String message) {

        if (message == null || message.isBlank()) {
            return null;
        }

        String text = message.trim();

        // -----------------------------------------------------
        // Remove internal language instructions.
        // -----------------------------------------------------

        text = text.replaceAll(
                "(?i)response\\s+language\\s*:\\s*"
                        + "(english|hindi|marathi)\\.?",
                " ");

        text = text.replaceAll(
                "(?i)please\\s+answer\\s+in\\s+"
                        + "(english|hindi|marathi)\\.?",
                " ");

        text = text.trim();

        // -----------------------------------------------------
        // Explicit location phrases.
        //
        // Examples:
        //
        // weather in Akola
        // air quality in Akola and is it safe
        // forecast for Mumbai tomorrow
        // weather near Pune
        // temperature at London
        // -----------------------------------------------------

        String[] patterns = {

                "(?i)\\bin\\s+([A-Za-z][A-Za-z .'-]{1,60}?)"
                        + "(?=\\s+(?:and|but|or|is|are|"
                        + "will|would|should|can|could|"
                        + "please|today|tomorrow|tonight|now)\\b"
                        + "|\\?|\\.|,|!|$)",

                "(?i)\\bfor\\s+([A-Za-z][A-Za-z .'-]{1,60}?)"
                        + "(?=\\s+(?:and|but|or|is|are|"
                        + "will|would|should|can|could|"
                        + "please|today|tomorrow|tonight|now)\\b"
                        + "|\\?|\\.|,|!|$)",

                "(?i)\\bat\\s+([A-Za-z][A-Za-z .'-]{1,60}?)"
                        + "(?=\\s+(?:and|but|or|is|are|"
                        + "will|would|should|can|could|"
                        + "please|today|tomorrow|tonight|now)\\b"
                        + "|\\?|\\.|,|!|$)",

                "(?i)\\bnear\\s+([A-Za-z][A-Za-z .'-]{1,60}?)"
                        + "(?=\\s+(?:and|but|or|is|are|"
                        + "will|would|should|can|could|"
                        + "please|today|tomorrow|tonight|now)\\b"
                        + "|\\?|\\.|,|!|$)",

                "(?i)\\baround\\s+([A-Za-z][A-Za-z .'-]{1,60}?)"
                        + "(?=\\s+(?:and|but|or|is|are|"
                        + "will|would|should|can|could|"
                        + "please|today|tomorrow|tonight|now)\\b"
                        + "|\\?|\\.|,|!|$)"
        };

        for (String regex : patterns) {

            Matcher matcher = Pattern.compile(regex)
                    .matcher(text);

            while (matcher.find()) {

                String candidate = cleanCandidate(
                        matcher.group(1));

                if (!isValidCandidate(candidate)) {
                    continue;
                }

                String resolved = resolveLocation(candidate);

                if (resolved != null) {
                    return resolved;
                }
            }
        }

        // -----------------------------------------------------
        // Special handling for:
        //
        // "in Akola and ..."
        //
        // This catches cases where the sentence continues
        // after the city.
        // -----------------------------------------------------

        Matcher inWithContinuation = Pattern.compile(
                "(?i)\\bin\\s+"
                        + "([A-Za-z][A-Za-z .'-]{1,60}?)"
                        + "\\s+(?:and|but|or)\\b")
                .matcher(text);

        while (inWithContinuation.find()) {

            String candidate = cleanCandidate(
                    inWithContinuation.group(1));

            if (!isValidCandidate(candidate)) {
                continue;
            }

            String resolved = resolveLocation(candidate);

            if (resolved != null) {
                return resolved;
            }
        }

        // -----------------------------------------------------
        // Handle simple "in CITY" fallback.
        // -----------------------------------------------------

        Matcher simpleIn = Pattern.compile(
                "(?i)\\bin\\s+(.+)$")
                .matcher(text);

        if (simpleIn.find()) {

            String candidate = cleanCandidate(
                    simpleIn.group(1));

            if (isValidCandidate(candidate)) {

                String resolved = resolveLocation(candidate);

                if (resolved != null) {
                    return resolved;
                }
            }
        }

        // -----------------------------------------------------
        // Try a Latin city at the end of the sentence.
        //
        // Example:
        //
        // क्या मुझे छाता लेना चाहिए Akola?
        // -----------------------------------------------------

        String endCandidate = extractTrailingLatinLocation(text);

        if (endCandidate != null
                && isValidCandidate(endCandidate)) {

            String resolved = resolveLocation(endCandidate);

            if (resolved != null) {
                return resolved;
            }
        }

        // -----------------------------------------------------
        // Last fallback.
        //
        // Remove common weather/question words and try
        // remaining Latin text as a possible location.
        // -----------------------------------------------------

        String fallback = text.replaceAll(
                "(?i)weather|forecast|temperature|"
                        + "conditions|today|tomorrow|"
                        + "tonight|now|please|should|"
                        + "carry|bring|umbrella|rain|"
                        + "hot|cold|safe|travel|"
                        + "outdoor|activities|"
                        + "air|quality|aqi|"
                        + "exercise|outside|"
                        + "is|are|will|would|"
                        + "can|could|and|but|or",
                " ");

        fallback = cleanCandidate(fallback);

        if (isValidCandidate(fallback)) {

            String resolved = resolveLocation(fallback);

            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    // =========================================================
    // EXTRACT TRAILING LATIN CITY
    // =========================================================

    private String extractTrailingLatinLocation(
            String text) {

        Matcher matcher = Pattern.compile(
                "([A-Za-z][A-Za-z .'-]{1,60})"
                        + "\\s*[?!.,]?$")
                .matcher(text);

        if (!matcher.find()) {
            return null;
        }

        String candidate = cleanCandidate(
                matcher.group(1));

        if (isStopWord(candidate)) {
            return null;
        }

        return candidate;
    }

    // =========================================================
    // CLEAN LOCATION CANDIDATE
    // =========================================================

    private String cleanCandidate(
            String candidate) {

        if (candidate == null) {
            return null;
        }

        String cleaned = candidate.trim();

        cleaned = cleaned.replaceAll(
                "^[\\s,.:;!?]+",
                "");

        cleaned = cleaned.replaceAll(
                "[\\s,.:;!?]+$",
                "");

        // Remove common time words.
        cleaned = cleaned.replaceAll(
                "(?i)\\s+(today|tomorrow|"
                        + "tonight|now)$",
                "");

        // Remove common weather words.
        cleaned = cleaned.replaceAll(
                "(?i)\\s+(weather|forecast|"
                        + "temperature|conditions)$",
                "");

        return cleaned.trim();
    }

    // =========================================================
    // VALIDATE LOCATION CANDIDATE
    // =========================================================

    private boolean isValidCandidate(
            String candidate) {

        if (candidate == null
                || candidate.isBlank()) {

            return false;
        }

        if (candidate.length() < 2
                || candidate.length() > 60) {

            return false;
        }

        if (isStopWord(candidate)) {
            return false;
        }

        return true;
    }

    // =========================================================
    // COMMON NON-CITY WORDS
    // =========================================================

    private boolean isStopWord(
            String candidate) {

        String value = candidate.trim()
                .toLowerCase();

        return value.equals("hindi")
                || value.equals("marathi")
                || value.equals("english")
                || value.equals("today")
                || value.equals("tomorrow")
                || value.equals("tonight")
                || value.equals("now")
                || value.equals("weather")
                || value.equals("forecast")
                || value.equals("temperature")
                || value.equals("conditions")
                || value.equals("rain")
                || value.equals("umbrella")
                || value.equals("outside")
                || value.equals("outdoor")
                || value.equals("travel")
                || value.equals("air")
                || value.equals("quality")
                || value.equals("aqi")
                || value.equals("exercise")
                || value.equals("safe")
                || value.equals("hot")
                || value.equals("cold");
    }

    // =========================================================
    // RESOLVE LOCATION USING OPEN-METEO
    // =========================================================

    private String resolveLocation(
            String candidate) {

        try {

            String encoded = URLEncoder.encode(
                    candidate,
                    StandardCharsets.UTF_8);

            String url = "https://geocoding-api.open-meteo.com/v1/search"
                    + "?name=" + encoded
                    + "&count=10"
                    + "&language=en"
                    + "&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers
                            .ofString());

            if (response.statusCode() != 200) {
                return null;
            }

            JsonNode data = objectMapper.readTree(
                    response.body());

            if (!data.has("results")
                    || data.get("results") == null
                    || data.get("results").isEmpty()) {

                return null;
            }

            // -------------------------------------------------
            // Prefer exact city-name match.
            // -------------------------------------------------

            for (JsonNode result : data.get("results")) {

                if (!result.has("name")) {
                    continue;
                }

                String name = result.get("name").asText();

                if (name.equalsIgnoreCase(
                        candidate.trim())) {

                    return name;
                }
            }

            // -------------------------------------------------
            // Otherwise use first valid result.
            // -------------------------------------------------

            JsonNode first = data.get("results").get(0);

            if (first.has("name")) {

                return first
                        .get("name")
                        .asText();
            }

        } catch (Exception e) {

            System.err.println(
                    "Location resolution failed: "
                            + e.getMessage());
        }

        return null;
    }
}