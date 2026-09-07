package com.example.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class WeatherService {

        private final HttpClient httpClient;
        private final ObjectMapper objectMapper;

        @Value("${WEATHERAPI_KEY:}")
        private String weatherApiKey;

        // Cache weather results for 10 minutes.
        private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

        private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

        public WeatherService() {
                this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();

                this.objectMapper = new ObjectMapper();
        }

        public JsonNode getWeather(String city) throws Exception {

                String cleanCity = city == null ? "" : city.trim();

                if (cleanCity.isEmpty()) {
                        throw new IllegalArgumentException("City name cannot be empty.");
                }

                String cacheKey = cleanCity.toLowerCase();

                // =========================================================
                // 1. CHECK CACHE
                // =========================================================

                CacheEntry cached = cache.get(cacheKey);

                if (cached != null
                                && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {

                        System.out.println("Weather cache HIT: " + cleanCity);

                        ObjectNode cachedData = (ObjectNode) cached.data.deepCopy();

                        cachedData.put("cached", true);

                        return cachedData;
                }

                System.out.println("Weather cache MISS: " + cleanCity);

                // =========================================================
                // 2. PRIMARY PROVIDER - OPEN-METEO
                // =========================================================

                try {

                        ObjectNode result = getFromOpenMeteo(cleanCity);

                        result.put("provider", "Open-Meteo");
                        result.put("cached", false);

                        cache.put(
                                        cacheKey,
                                        new CacheEntry(
                                                        result.deepCopy(),
                                                        System.currentTimeMillis()));

                        System.out.println(
                                        "Weather provider: Open-Meteo");

                        return result;

                } catch (Exception openMeteoException) {

                        System.err.println(
                                        "========================================");

                        System.err.println(
                                        "OPEN-METEO FAILED");

                        System.err.println(
                                        "City: " + cleanCity);

                        System.err.println(
                                        "Reason: "
                                                        + openMeteoException.getMessage());

                        System.err.println(
                                        "Switching to WeatherAPI fallback...");

                        System.err.println(
                                        "========================================");
                }

                // =========================================================
                // 3. FALLBACK PROVIDER - WEATHERAPI
                // =========================================================

                try {

                        ObjectNode result = getFromWeatherApi(cleanCity);

                        result.put("provider", "WeatherAPI");
                        result.put("cached", false);

                        cache.put(
                                        cacheKey,
                                        new CacheEntry(
                                                        result.deepCopy(),
                                                        System.currentTimeMillis()));

                        System.out.println(
                                        "Weather provider: WeatherAPI FALLBACK");

                        return result;

                } catch (Exception weatherApiException) {

                        System.err.println(
                                        "========================================");

                        System.err.println(
                                        "WEATHERAPI FALLBACK FAILED");

                        System.err.println(
                                        "City: " + cleanCity);

                        System.err.println(
                                        "Reason: "
                                                        + weatherApiException.getMessage());

                        System.err.println(
                                        "========================================");

                        // =====================================================
                        // 4. LAST RESORT - STALE CACHE
                        // =====================================================

                        if (cached != null) {

                                System.err.println(
                                                "Returning stale cached weather for: "
                                                                + cleanCity);

                                ObjectNode staleData = (ObjectNode) cached.data.deepCopy();

                                staleData.put("cached", true);
                                staleData.put("stale", true);

                                return staleData;
                        }

                        throw new RuntimeException(
                                        "Weather services are temporarily unavailable.");
                }
        }

        // =============================================================
        // OPEN-METEO PRIMARY
        // =============================================================

        private ObjectNode getFromOpenMeteo(
                        String city) throws Exception {

                String encodedCity = URLEncoder.encode(
                                city,
                                StandardCharsets.UTF_8);

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
                                        "Open-Meteo geocoding HTTP "
                                                        + geocodingResponse.statusCode());
                }

                JsonNode locationData = objectMapper.readTree(
                                geocodingResponse.body());

                if (!locationData.has("results")
                                || locationData.get("results").isEmpty()) {

                        throw new RuntimeException(
                                        "City not found: " + city);
                }

                JsonNode location = locationData.get("results").get(0);

                if (!location.has("latitude")
                                || !location.has("longitude")) {

                        throw new RuntimeException(
                                        "Location coordinates unavailable.");
                }

                double latitude = location.get("latitude").asDouble();

                double longitude = location.get("longitude").asDouble();

                String locationName = location.has("name")
                                ? location.get("name").asText()
                                : city;

                String country = location.has("country")
                                ? location.get("country").asText()
                                : "";

                // =========================================================
                // OPEN-METEO FORECAST
                // =========================================================

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

                HttpResponse<String> weatherResponse = httpClient.send(
                                weatherRequest,
                                HttpResponse.BodyHandlers.ofString());

                if (weatherResponse.statusCode() != 200) {

                        System.err.println(
                                        "Open-Meteo forecast status: "
                                                        + weatherResponse.statusCode());

                        System.err.println(
                                        "Open-Meteo response: "
                                                        + weatherResponse.body());

                        throw new RuntimeException(
                                        "Open-Meteo forecast HTTP "
                                                        + weatherResponse.statusCode());
                }

                JsonNode weatherData = objectMapper.readTree(
                                weatherResponse.body());

                if (!weatherData.has("current")) {

                        throw new RuntimeException(
                                        "Current weather data unavailable.");
                }

                ObjectNode result = objectMapper.createObjectNode();

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

                // =========================================================
                // OPEN-METEO AQI
                // Only called after forecast succeeds.
                // =========================================================

                result.set(
                                "airQuality",
                                getOpenMeteoAirQuality(
                                                latitude,
                                                longitude));

                return result;
        }

        // =============================================================
        // OPEN-METEO AQI
        // =============================================================

        private ObjectNode getOpenMeteoAirQuality(
                        double latitude,
                        double longitude) {

                ObjectNode airQuality = objectMapper.createObjectNode();

                try {

                        String airQualityUrl = "https://air-quality-api.open-meteo.com/v1/air-quality"
                                        + "?latitude=" + latitude
                                        + "&longitude=" + longitude
                                        + "&current="
                                        + "us_aqi,"
                                        + "pm2_5,"
                                        + "pm10"
                                        + "&timezone=auto";

                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(airQualityUrl))
                                        .timeout(Duration.ofSeconds(8))
                                        .GET()
                                        .build();

                        HttpResponse<String> response = httpClient.send(
                                        request,
                                        HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() == 200) {

                                JsonNode data = objectMapper.readTree(
                                                response.body());

                                JsonNode current = data.get("current");

                                if (current != null) {

                                        double aqi = current.has("us_aqi")
                                                        ? current.get("us_aqi").asDouble()
                                                        : -1;

                                        double pm25 = current.has("pm2_5")
                                                        ? current.get("pm2_5").asDouble()
                                                        : 0.0;

                                        double pm10 = current.has("pm10")
                                                        ? current.get("pm10").asDouble()
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

                                        return airQuality;
                                }
                        }

                } catch (Exception e) {

                        System.err.println(
                                        "Open-Meteo AQI failed: "
                                                        + e.getMessage());
                }

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

                return airQuality;
        }

        // =============================================================
        // WEATHERAPI FALLBACK
        // =============================================================

        private ObjectNode getFromWeatherApi(
                        String city) throws Exception {

                if (weatherApiKey == null
                                || weatherApiKey.trim().isEmpty()) {

                        throw new RuntimeException(
                                        "WEATHERAPI_KEY is not configured.");
                }

                String encodedCity = URLEncoder.encode(
                                city,
                                StandardCharsets.UTF_8);

                String encodedApiKey = URLEncoder.encode(
                                weatherApiKey,
                                StandardCharsets.UTF_8);

                String url = "https://api.weatherapi.com/v1/forecast.json"
                                + "?key=" + encodedApiKey
                                + "&q=" + encodedCity
                                + "&days=3"
                                + "&aqi=yes"
                                + "&alerts=no";

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build();

                HttpResponse<String> response = httpClient.send(
                                request,
                                HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {

                        System.err.println(
                                        "WeatherAPI status: "
                                                        + response.statusCode());

                        System.err.println(
                                        "WeatherAPI response: "
                                                        + response.body());

                        throw new RuntimeException(
                                        "WeatherAPI HTTP "
                                                        + response.statusCode());
                }

                JsonNode data = objectMapper.readTree(
                                response.body());

                if (!data.has("current")
                                || !data.has("location")
                                || !data.has("forecast")) {

                        throw new RuntimeException(
                                        "Invalid WeatherAPI response.");
                }

                JsonNode location = data.get("location");

                JsonNode current = data.get("current");

                // =========================================================
                // MAIN RESULT
                // =========================================================

                ObjectNode result = objectMapper.createObjectNode();

                result.put(
                                "city",
                                location.has("name")
                                                ? location.get("name").asText()
                                                : city);

                result.put(
                                "country",
                                location.has("country")
                                                ? location.get("country").asText()
                                                : "");

                result.put(
                                "latitude",
                                location.has("lat")
                                                ? location.get("lat").asDouble()
                                                : 0.0);

                result.put(
                                "longitude",
                                location.has("lon")
                                                ? location.get("lon").asDouble()
                                                : 0.0);

                // =========================================================
                // CURRENT WEATHER
                // =========================================================

                ObjectNode currentNode = objectMapper.createObjectNode();

                currentNode.put(
                                "time",
                                current.has("last_updated")
                                                ? current.get("last_updated").asText()
                                                : "");

                currentNode.put(
                                "temperature_2m",
                                getDouble(
                                                current,
                                                "temp_c"));

                currentNode.put(
                                "relative_humidity_2m",
                                getDouble(
                                                current,
                                                "humidity"));

                currentNode.put(
                                "apparent_temperature",
                                getDouble(
                                                current,
                                                "feelslike_c"));

                currentNode.put(
                                "precipitation",
                                getDouble(
                                                current,
                                                "precip_mm"));

                currentNode.put(
                                "weather_code",
                                mapWeatherApiCode(
                                                getInt(
                                                                current,
                                                                "condition",
                                                                "code")));

                currentNode.put(
                                "wind_speed_10m",
                                getDouble(
                                                current,
                                                "wind_kph"));

                result.set(
                                "current",
                                currentNode);

                // =========================================================
                // HOURLY FORECAST
                // =========================================================

                ObjectNode hourly = objectMapper.createObjectNode();

                var hourlyTimes = objectMapper.createArrayNode();

                var hourlyTemperatures = objectMapper.createArrayNode();

                var hourlyRainProbability = objectMapper.createArrayNode();

                var hourlyPrecipitation = objectMapper.createArrayNode();

                var hourlyWeatherCodes = objectMapper.createArrayNode();

                JsonNode forecastDays = data.get("forecast")
                                .get("forecastday");

                for (JsonNode forecastDay : forecastDays) {

                        JsonNode hours = forecastDay.get("hour");

                        if (hours == null) {
                                continue;
                        }

                        for (JsonNode hour : hours) {

                                hourlyTimes.add(
                                                getText(
                                                                hour,
                                                                "time"));

                                hourlyTemperatures.add(
                                                getDouble(
                                                                hour,
                                                                "temp_c"));

                                hourlyRainProbability.add(
                                                getDouble(
                                                                hour,
                                                                "chance_of_rain"));

                                hourlyPrecipitation.add(
                                                getDouble(
                                                                hour,
                                                                "precip_mm"));

                                int conditionCode = getInt(
                                                hour,
                                                "condition",
                                                "code");

                                hourlyWeatherCodes.add(
                                                mapWeatherApiCode(
                                                                conditionCode));
                        }
                }

                hourly.set(
                                "time",
                                hourlyTimes);

                hourly.set(
                                "temperature_2m",
                                hourlyTemperatures);

                hourly.set(
                                "precipitation_probability",
                                hourlyRainProbability);

                hourly.set(
                                "precipitation",
                                hourlyPrecipitation);

                hourly.set(
                                "weather_code",
                                hourlyWeatherCodes);

                result.set(
                                "hourly",
                                hourly);

                // =========================================================
                // DAILY FORECAST
                // =========================================================

                ObjectNode daily = objectMapper.createObjectNode();

                var dailyTimes = objectMapper.createArrayNode();

                var dailyWeatherCodes = objectMapper.createArrayNode();

                var dailyMax = objectMapper.createArrayNode();

                var dailyMin = objectMapper.createArrayNode();

                var dailyRainProbability = objectMapper.createArrayNode();

                var dailyPrecipitation = objectMapper.createArrayNode();

                for (JsonNode forecastDay : forecastDays) {

                        JsonNode day = forecastDay.get("day");

                        if (day == null) {
                                continue;
                        }

                        dailyTimes.add(
                                        getText(
                                                        forecastDay,
                                                        "date"));

                        dailyWeatherCodes.add(
                                        mapWeatherApiCode(
                                                        getInt(
                                                                        day,
                                                                        "condition",
                                                                        "code")));

                        dailyMax.add(
                                        getDouble(
                                                        day,
                                                        "maxtemp_c"));

                        dailyMin.add(
                                        getDouble(
                                                        day,
                                                        "mintemp_c"));

                        dailyRainProbability.add(
                                        getDouble(
                                                        day,
                                                        "daily_chance_of_rain"));

                        dailyPrecipitation.add(
                                        getDouble(
                                                        day,
                                                        "totalprecip_mm"));
                }

                daily.set(
                                "time",
                                dailyTimes);

                daily.set(
                                "weather_code",
                                dailyWeatherCodes);

                daily.set(
                                "temperature_2m_max",
                                dailyMax);

                daily.set(
                                "temperature_2m_min",
                                dailyMin);

                daily.set(
                                "precipitation_probability_max",
                                dailyRainProbability);

                daily.set(
                                "precipitation_sum",
                                dailyPrecipitation);

                result.set(
                                "daily",
                                daily);

                // =========================================================
                // WEATHERAPI AIR QUALITY
                // =========================================================

                result.set(
                                "airQuality",
                                getWeatherApiAirQuality(
                                                current));

                return result;
        }

        // =============================================================
        // WEATHERAPI AIR QUALITY
        // =============================================================

        private ObjectNode getWeatherApiAirQuality(
                        JsonNode current) {

                ObjectNode airQuality = objectMapper.createObjectNode();

                try {

                        JsonNode aq = current.get("air_quality");

                        if (aq != null) {

                                double pm25 = aq.has("pm2_5")
                                                ? aq.get("pm2_5").asDouble()
                                                : 0.0;

                                double pm10 = aq.has("pm10")
                                                ? aq.get("pm10").asDouble()
                                                : 0.0;

                                double aqi = aq.has("us-epa-index")
                                                ? aq.get("us-epa-index").asDouble()
                                                : -1;

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
                                                getWeatherApiAqiStatus(
                                                                aqi));

                                return airQuality;
                        }

                } catch (Exception e) {

                        System.err.println(
                                        "WeatherAPI AQI parsing failed: "
                                                        + e.getMessage());
                }

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

                return airQuality;
        }

        // =============================================================
        // WEATHERAPI → WMO WEATHER CODE
        // =============================================================

        private int mapWeatherApiCode(int code) {

                // Clear
                if (code == 1000) {
                        return 0;
                }

                // Partly cloudy
                if (code == 1003) {
                        return 2;
                }

                // Cloudy / overcast
                if (code == 1006
                                || code == 1009) {
                        return 3;
                }

                // Mist / fog
                if (code == 1030
                                || code == 1135
                                || code == 1147) {
                        return 45;
                }

                // Thunderstorms
                if (code == 1087
                                || code == 1273
                                || code == 1276
                                || code == 1279
                                || code == 1282) {
                        return 95;
                }

                // Snow
                if (code == 1066
                                || code == 1114
                                || code == 1117
                                || code == 1210
                                || code == 1213
                                || code == 1216
                                || code == 1219
                                || code == 1222
                                || code == 1225
                                || code == 1255
                                || code == 1258) {
                        return 73;
                }

                // Sleet / ice pellets
                if (code == 1069
                                || code == 1204
                                || code == 1207
                                || code == 1237
                                || code == 1249
                                || code == 1252
                                || code == 1261
                                || code == 1264) {
                        return 77;
                }

                // Freezing rain / drizzle
                if (code == 1072
                                || code == 1168
                                || code == 1171
                                || code == 1198
                                || code == 1201) {
                        return 56;
                }

                // Rain showers
                if (code == 1240
                                || code == 1243
                                || code == 1245) {
                        return 80;
                }

                // Rain
                if (code == 1063
                                || code == 1150
                                || code == 1153
                                || code == 1180
                                || code == 1183
                                || code == 1186
                                || code == 1189
                                || code == 1192
                                || code == 1195) {
                        return 63;
                }

                // Default
                return 3;
        }

        // =============================================================
        // OPEN-METEO AQI STATUS
        // =============================================================

        private String getAqiStatus(
                        double aqi) {

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

        // =============================================================
        // WEATHERAPI AQI STATUS
        // =============================================================

        private String getWeatherApiAqiStatus(
                        double index) {

                if (index < 0) {
                        return "Unavailable";
                }

                switch ((int) index) {

                        case 1:
                                return "Good";

                        case 2:
                                return "Moderate";

                        case 3:
                                return "Unhealthy for Sensitive Groups";

                        case 4:
                                return "Unhealthy";

                        case 5:
                                return "Very Unhealthy";

                        case 6:
                                return "Hazardous";

                        default:
                                return "Unavailable";
                }
        }

        // =============================================================
        // JSON HELPERS
        // =============================================================

        private double getDouble(
                        JsonNode node,
                        String field) {

                if (node == null
                                || !node.has(field)
                                || node.get(field).isNull()) {

                        return 0.0;
                }

                return node.get(field).asDouble();
        }

        private int getInt(
                        JsonNode node,
                        String parent,
                        String field) {

                if (node == null
                                || !node.has(parent)
                                || node.get(parent) == null
                                || !node.get(parent).has(field)) {

                        return 0;
                }

                return node.get(parent)
                                .get(field)
                                .asInt();
        }

        private String getText(
                        JsonNode node,
                        String field) {

                if (node == null
                                || !node.has(field)
                                || node.get(field).isNull()) {

                        return "";
                }

                return node.get(field).asText();
        }

        // =============================================================
        // CACHE ENTRY
        // =============================================================

        private static class CacheEntry {

                private final JsonNode data;
                private final long timestamp;

                private CacheEntry(
                                JsonNode data,
                                long timestamp) {

                        this.data = data;
                        this.timestamp = timestamp;
                }
        }
}