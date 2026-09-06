package com.example.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WeatherService {

        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        public WeatherService() {
                this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();

                this.objectMapper = new ObjectMapper();
        }

        public JsonNode getWeather(String city) throws Exception {

                // Clean the city name
                String cleanCity = city == null
                                ? ""
                                : city.trim();

                if (cleanCity.isEmpty()) {
                        throw new RuntimeException(
                                        "City name cannot be empty.");
                }

                /*
                 * GLOBAL LOCATION SEARCH
                 */
                String encodedCity = URLEncoder.encode(
                                cleanCity,
                                StandardCharsets.UTF_8);

                // Step 1: Global geocoding
                String geocodingUrl = "https://geocoding-api.open-meteo.com/v1/search"
                                + "?name=" + encodedCity
                                + "&count=10"
                                + "&language=en"
                                + "&format=json";

                HttpRequest geocodingRequest = HttpRequest.newBuilder()
                                .uri(URI.create(geocodingUrl))
                                .timeout(Duration.ofSeconds(8))
                                .GET()
                                .build();

                HttpResponse<String> geocodingResponse = httpClient.send(
                                geocodingRequest,
                                HttpResponse.BodyHandlers.ofString());

                if (geocodingResponse.statusCode() != 200) {
                        throw new RuntimeException(
                                        "Location service is unavailable right now.");
                }

                JsonNode locationData = objectMapper.readTree(
                                geocodingResponse.body());

                /*
                 * Check whether Open-Meteo returned results.
                 */
                if (!locationData.has("results")
                                || locationData.get("results").isEmpty()) {

                        throw new RuntimeException(
                                        "City not found: " + cleanCity);
                }

                /*
                 * Use the best matching result returned by Open-Meteo.
                 */
                JsonNode location = locationData.get("results").get(0);

                // Extract coordinates safely
                if (!location.has("latitude")
                                || !location.has("longitude")) {

                        throw new RuntimeException(
                                        "Location coordinates could not be determined for: "
                                                        + cleanCity);
                }

                double latitude = location.get("latitude").asDouble();

                double longitude = location.get("longitude").asDouble();

                // Extract location name
                String locationName = location.has("name")
                                ? location.get("name").asText()
                                : cleanCity;

                // Extract country
                String country = location.has("country")
                                ? location.get("country").asText()
                                : "";

                // ============================================================
                // Step 2: Weather API
                // ============================================================

                String weatherUrl = "https://api.open-meteo.com/v1/forecast"
                                + "?latitude=" + latitude
                                + "&longitude=" + longitude
                                + "&current="
                                + "temperature_2m,"
                                + "relative_humidity_2m,"
                                + "apparent_temperature,"
                                + "precipitation,"
                                + "weather_code,"
                                + "wind_speed_10m"
                                + "&hourly="
                                + "temperature_2m,"
                                + "precipitation_probability,"
                                + "precipitation,"
                                + "weather_code"
                                + "&daily="
                                + "weather_code,"
                                + "temperature_2m_max,"
                                + "temperature_2m_min,"
                                + "precipitation_probability_max,"
                                + "precipitation_sum"
                                + "&timezone=auto"
                                + "&forecast_days=7";

                HttpRequest weatherRequest = HttpRequest.newBuilder()
                                .uri(URI.create(weatherUrl))
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build();

                // ============================================================
                // Step 3: Air Quality API
                // ============================================================

                String airQualityUrl = "https://air-quality-api.open-meteo.com/v1/air-quality"
                                + "?latitude=" + latitude
                                + "&longitude=" + longitude
                                + "&current="
                                + "us_aqi,"
                                + "pm2_5,"
                                + "pm10"
                                + "&timezone=auto";

                HttpRequest airQualityRequest = HttpRequest.newBuilder()
                                .uri(URI.create(airQualityUrl))
                                .timeout(Duration.ofSeconds(8))
                                .GET()
                                .build();

                /*
                 * IMPORTANT:
                 *
                 * Weather and AQI requests start at the same time.
                 * This keeps the additional AQI feature fast.
                 */
                CompletableFuture<HttpResponse<String>> weatherFuture = httpClient.sendAsync(
                                weatherRequest,
                                HttpResponse.BodyHandlers.ofString());

                CompletableFuture<HttpResponse<String>> airQualityFuture = httpClient.sendAsync(
                                airQualityRequest,
                                HttpResponse.BodyHandlers.ofString());

                // ============================================================
                // Step 4: Get weather result
                // ============================================================

                HttpResponse<String> weatherResponse = weatherFuture.join();

                if (weatherResponse.statusCode() != 200) {
                        System.err.println("========================================");
                        System.err.println("OPEN-METEO WEATHER API ERROR");
                        System.err.println("Status: " + weatherResponse.statusCode());
                        System.err.println("Response: " + weatherResponse.body());
                        System.err.println("========================================");

                        throw new RuntimeException(
                                        "Weather service returned HTTP "
                                                        + weatherResponse.statusCode());
                }

                JsonNode weatherData = objectMapper.readTree(
                                weatherResponse.body());

                /*
                 * Make sure the weather API actually returned
                 * the required sections.
                 */
                if (!weatherData.has("current")) {
                        throw new RuntimeException(
                                        "Current weather data is unavailable for: "
                                                        + locationName);
                }

                // ============================================================
                // Step 5: Build final response
                // ============================================================

                var result = objectMapper.createObjectNode();

                result.put(
                                "city",
                                locationName);

                result.put(
                                "country",
                                country);

                result.put(
                                "latitude",
                                latitude);

                result.put(
                                "longitude",
                                longitude);

                result.set(
                                "current",
                                weatherData.get("current"));

                if (weatherData.has("hourly")) {
                        result.set(
                                        "hourly",
                                        weatherData.get("hourly"));
                }

                if (weatherData.has("daily")) {
                        result.set(
                                        "daily",
                                        weatherData.get("daily"));
                }

                // ============================================================
                // Step 6: Add Air Quality
                // ============================================================

                var airQuality = objectMapper.createObjectNode();

                try {

                        HttpResponse<String> airQualityResponse = airQualityFuture.join();

                        if (airQualityResponse.statusCode() == 200) {

                                JsonNode airQualityData = objectMapper.readTree(
                                                airQualityResponse.body());

                                JsonNode currentAirQuality = airQualityData.get("current");

                                if (currentAirQuality != null) {

                                        double aqi = currentAirQuality.has("us_aqi")
                                                        ? currentAirQuality
                                                                        .get("us_aqi")
                                                                        .asDouble()
                                                        : -1;

                                        double pm25 = currentAirQuality.has("pm2_5")
                                                        ? currentAirQuality
                                                                        .get("pm2_5")
                                                                        .asDouble()
                                                        : 0.0;

                                        double pm10 = currentAirQuality.has("pm10")
                                                        ? currentAirQuality
                                                                        .get("pm10")
                                                                        .asDouble()
                                                        : 0.0;

                                        airQuality.put(
                                                        "us_aqi",
                                                        aqi);

                                        airQuality.put(
                                                        "pm2_5",
                                                        pm25);

                                        airQuality.put(
                                                        "pm10",
                                                        pm10);

                                        airQuality.put(
                                                        "status",
                                                        getAqiStatus(aqi));
                                }
                        }

                } catch (Exception e) {

                        /*
                         * AQI is an enhancement.
                         *
                         * If the AQI service fails, weather should
                         * continue working normally.
                         */
                        System.err.println(
                                        "AQI request failed: "
                                                        + e.getMessage());

                        airQuality.put(
                                        "us_aqi",
                                        -1);

                        airQuality.put(
                                        "pm2_5",
                                        0.0);

                        airQuality.put(
                                        "pm10",
                                        0.0);

                        airQuality.put(
                                        "status",
                                        "Unavailable");
                }

                result.set(
                                "airQuality",
                                airQuality);

                return result;
        }

        // ================================================================
        // AQI classification
        // ================================================================

        private String getAqiStatus(double aqi) {

                if (aqi < 0) {
                        return "Unavailable";
                }

                if (aqi <= 50) {
                        return "Good";
                }

                if (aqi <= 100) {
                        return "Moderate";
                }

                if (aqi <= 150) {
                        return "Unhealthy for Sensitive Groups";
                }

                if (aqi <= 200) {
                        return "Unhealthy";
                }

                if (aqi <= 300) {
                        return "Very Unhealthy";
                }

                return "Hazardous";
        }
}