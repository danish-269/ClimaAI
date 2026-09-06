package com.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.dto.ChatResponse;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

import tools.jackson.databind.JsonNode;

@Service
public class ChatService {

        private final Client client;
        private final WeatherService weatherService;
        private final DecisionService decisionService;
        private final LocationService locationService;

        public ChatService(
                        @Value("${gemini.api.key}") String apiKey,
                        WeatherService weatherService,
                        DecisionService decisionService,
                        LocationService locationService) {

                this.client = Client.builder()
                                .apiKey(apiKey)
                                .build();

                this.weatherService = weatherService;
                this.decisionService = decisionService;
                this.locationService = locationService;
        }

        // =========================================================
        // SAFE HELPERS
        // =========================================================

        private double getDouble(
                        JsonNode node,
                        String field,
                        double defaultValue) {

                if (node == null
                                || !node.has(field)
                                || node.get(field) == null
                                || node.get(field).isNull()) {

                        return defaultValue;
                }

                return node.get(field).asDouble();
        }

        private String getText(
                        JsonNode node,
                        String field,
                        String defaultValue) {

                if (node == null
                                || !node.has(field)
                                || node.get(field) == null
                                || node.get(field).isNull()) {

                        return defaultValue;
                }

                return node.get(field).asText();
        }

        // =========================================================
        // RESPONSE LANGUAGE
        // =========================================================

        private String detectLanguage(String message) {

                if (message == null) {
                        return "English";
                }

                String lower = message.toLowerCase();

                if (lower.contains("response language: hindi")) {
                        return "Hindi";
                }

                if (lower.contains("response language: marathi")) {
                        return "Marathi";
                }

                if (lower.contains("response language: english")) {
                        return "English";
                }

                return "English";
        }

        // =========================================================
        // WEATHER CODE DESCRIPTION
        // =========================================================

        private String getWeatherDescription(int code) {

                if (code == 0) {
                        return "Clear sky";
                }

                if (code == 1) {
                        return "Mainly clear";
                }

                if (code == 2) {
                        return "Partly cloudy";
                }

                if (code == 3) {
                        return "Overcast";
                }

                if (code == 45 || code == 48) {
                        return "Fog";
                }

                if (code == 51 || code == 53 || code == 55) {
                        return "Drizzle";
                }

                if (code == 56 || code == 57) {
                        return "Freezing drizzle";
                }

                if (code == 61 || code == 63 || code == 65) {
                        return "Rain";
                }

                if (code == 66 || code == 67) {
                        return "Freezing rain";
                }

                if (code == 71 || code == 73 || code == 75) {
                        return "Snow";
                }

                if (code == 77) {
                        return "Snow grains";
                }

                if (code == 80 || code == 81 || code == 82) {
                        return "Rain showers";
                }

                if (code == 85 || code == 86) {
                        return "Snow showers";
                }

                if (code == 95) {
                        return "Thunderstorm";
                }

                if (code == 96 || code == 99) {
                        return "Thunderstorm with hail";
                }

                return "Unknown conditions";
        }

        // =========================================================
        // CURRENT WEATHER
        // =========================================================

        private String buildCurrentWeatherContext(
                        JsonNode weather) {

                JsonNode current = weather.get("current");

                if (current == null) {
                        return "CURRENT WEATHER DATA: Unavailable\n";
                }

                double temperature = getDouble(
                                current,
                                "temperature_2m",
                                Double.NaN);

                double feelsLike = getDouble(
                                current,
                                "apparent_temperature",
                                Double.NaN);

                double humidity = getDouble(
                                current,
                                "relative_humidity_2m",
                                Double.NaN);

                double windSpeed = getDouble(
                                current,
                                "wind_speed_10m",
                                Double.NaN);

                double precipitation = getDouble(
                                current,
                                "precipitation",
                                Double.NaN);

                int weatherCode = current.has("weather_code")
                                ? current.get("weather_code").asInt()
                                : -1;

                String time = getText(
                                current,
                                "time",
                                "Unknown");

                StringBuilder builder = new StringBuilder();

                builder.append("CURRENT WEATHER\n");
                builder.append("Time: ")
                                .append(time)
                                .append("\n");

                builder.append("Temperature: ")
                                .append(String.format("%.1f", temperature))
                                .append(" °C\n");

                builder.append("Feels like: ")
                                .append(String.format("%.1f", feelsLike))
                                .append(" °C\n");

                builder.append("Humidity: ")
                                .append(String.format("%.1f", humidity))
                                .append(" %\n");

                builder.append("Wind speed: ")
                                .append(String.format("%.1f", windSpeed))
                                .append(" km/h\n");

                builder.append("Precipitation: ")
                                .append(String.format("%.1f", precipitation))
                                .append(" mm\n");

                builder.append("Condition: ")
                                .append(getWeatherDescription(weatherCode))
                                .append("\n");

                builder.append("Weather code: ")
                                .append(weatherCode)
                                .append("\n");

                return builder.toString();
        }

        // =========================================================
        // HOURLY FORECAST
        // =========================================================

        private String buildHourlyForecastContext(
                        JsonNode weather) {

                JsonNode hourly = weather.get("hourly");

                if (hourly == null
                                || !hourly.has("time")) {

                        return "HOURLY FORECAST: Unavailable\n";
                }

                JsonNode times = hourly.get("time");

                JsonNode temperatures = hourly.get("temperature_2m");

                JsonNode rainProbabilities = hourly.get("precipitation_probability");

                JsonNode precipitation = hourly.get("precipitation");

                JsonNode weatherCodes = hourly.get("weather_code");

                if (times == null
                                || times.isEmpty()) {

                        return "HOURLY FORECAST: Unavailable\n";
                }

                /*
                 * Find the current weather time.
                 *
                 * Open-Meteo usually returns hourly data starting
                 * from midnight. We therefore must NOT blindly use
                 * the first 48 records.
                 *
                 * We start from the current hour and then provide
                 * the next 48 hours to Gemini.
                 */

                int startIndex = 0;

                JsonNode current = weather.get("current");

                if (current != null
                                && current.has("time")) {

                        String currentTime = current.get("time").asText();

                        for (int i = 0; i < times.size(); i++) {

                                String hourlyTime = times.get(i).asText();

                                if (hourlyTime.compareTo(
                                                currentTime) >= 0) {

                                        startIndex = i;
                                        break;
                                }
                        }
                }

                int endIndex = Math.min(
                                startIndex + 48,
                                times.size());

                StringBuilder builder = new StringBuilder();

                builder.append(
                                "NEXT 48 HOURS FORECAST\n");

                for (int i = startIndex; i < endIndex; i++) {

                        String time = times.get(i).asText();

                        double temperature = temperatures != null
                                        && i < temperatures.size()
                                                        ? temperatures
                                                                        .get(i)
                                                                        .asDouble()
                                                        : Double.NaN;

                        double rainProbability = rainProbabilities != null
                                        && i < rainProbabilities.size()
                                                        ? rainProbabilities
                                                                        .get(i)
                                                                        .asDouble()
                                                        : Double.NaN;

                        double rain = precipitation != null
                                        && i < precipitation.size()
                                                        ? precipitation
                                                                        .get(i)
                                                                        .asDouble()
                                                        : Double.NaN;

                        int weatherCode = weatherCodes != null
                                        && i < weatherCodes.size()
                                                        ? weatherCodes
                                                                        .get(i)
                                                                        .asInt()
                                                        : -1;

                        builder.append("- ");
                        builder.append(time);

                        builder.append(" | Temperature: ");
                        builder.append(
                                        String.format(
                                                        "%.1f",
                                                        temperature));
                        builder.append(" °C");

                        builder.append(" | Rain probability: ");
                        builder.append(
                                        String.format(
                                                        "%.0f",
                                                        rainProbability));
                        builder.append(" %");

                        builder.append(" | Precipitation: ");
                        builder.append(
                                        String.format(
                                                        "%.1f",
                                                        rain));
                        builder.append(" mm");

                        builder.append(" | Condition: ");
                        builder.append(
                                        getWeatherDescription(
                                                        weatherCode));

                        builder.append("\n");
                }

                return builder.toString();
        }

        // =========================================================
        // 7-DAY FORECAST
        // =========================================================

        private String buildDailyForecastContext(
                        JsonNode weather) {

                JsonNode daily = weather.get("daily");

                if (daily == null
                                || !daily.has("time")) {

                        return "7-DAY FORECAST: Unavailable\n";
                }

                JsonNode times = daily.get("time");

                JsonNode weatherCodes = daily.get("weather_code");

                JsonNode highs = daily.get("temperature_2m_max");

                JsonNode lows = daily.get("temperature_2m_min");

                JsonNode rainProbabilities = daily.get(
                                "precipitation_probability_max");

                JsonNode precipitation = daily.get(
                                "precipitation_sum");

                if (times == null
                                || times.isEmpty()) {

                        return "7-DAY FORECAST: Unavailable\n";
                }

                int days = Math.min(times.size(), 7);

                StringBuilder builder = new StringBuilder();

                builder.append(
                                "7-DAY FORECAST\n");

                for (int i = 0; i < days; i++) {

                        String date = times.get(i).asText();

                        double high = highs != null
                                        && i < highs.size()
                                                        ? highs.get(i).asDouble()
                                                        : Double.NaN;

                        double low = lows != null
                                        && i < lows.size()
                                                        ? lows.get(i).asDouble()
                                                        : Double.NaN;

                        double rainProbability = rainProbabilities != null
                                        && i < rainProbabilities.size()
                                                        ? rainProbabilities.get(i).asDouble()
                                                        : Double.NaN;

                        double rain = precipitation != null
                                        && i < precipitation.size()
                                                        ? precipitation.get(i).asDouble()
                                                        : Double.NaN;

                        int weatherCode = weatherCodes != null
                                        && i < weatherCodes.size()
                                                        ? weatherCodes.get(i).asInt()
                                                        : -1;

                        builder.append("- ");
                        builder.append(date);

                        builder.append(" | High: ");
                        builder.append(String.format(
                                        "%.1f",
                                        high));
                        builder.append(" °C");

                        builder.append(" | Low: ");
                        builder.append(String.format(
                                        "%.1f",
                                        low));
                        builder.append(" °C");

                        builder.append(" | Rain probability: ");
                        builder.append(String.format(
                                        "%.0f",
                                        rainProbability));
                        builder.append(" %");

                        builder.append(" | Precipitation: ");
                        builder.append(String.format(
                                        "%.1f",
                                        rain));
                        builder.append(" mm");

                        builder.append(" | Condition: ");
                        builder.append(
                                        getWeatherDescription(
                                                        weatherCode));

                        builder.append("\n");
                }

                return builder.toString();
        }

        // =========================================================
        // AIR QUALITY
        // =========================================================

        private String buildAirQualityContext(
                        JsonNode weather) {

                JsonNode airQuality = weather.get("airQuality");

                if (airQuality == null) {

                        return """
                                        AIR QUALITY
                                        Air quality data is unavailable.
                                        """;
                }

                double aqi = getDouble(
                                airQuality,
                                "us_aqi",
                                Double.NaN);

                double pm25 = getDouble(
                                airQuality,
                                "pm2_5",
                                Double.NaN);

                double pm10 = getDouble(
                                airQuality,
                                "pm10",
                                Double.NaN);

                String status = getText(
                                airQuality,
                                "status",
                                "Unknown");

                StringBuilder builder = new StringBuilder();

                builder.append("AIR QUALITY\n");

                builder.append("US AQI: ")
                                .append(String.format(
                                                "%.0f",
                                                aqi))
                                .append("\n");

                builder.append("AQI Status: ")
                                .append(status)
                                .append("\n");

                builder.append("PM2.5: ")
                                .append(String.format(
                                                "%.1f",
                                                pm25))
                                .append(" µg/m³\n");

                builder.append("PM10: ")
                                .append(String.format(
                                                "%.1f",
                                                pm10))
                                .append(" µg/m³\n");

                return builder.toString();
        }

        // =========================================================
        // COMPLETE VERIFIED WEATHER CONTEXT
        // =========================================================

        private String buildWeatherContext(
                        JsonNode weather) {

                String city = getText(
                                weather,
                                "city",
                                "Unknown");

                String country = getText(
                                weather,
                                "country",
                                "Unknown");

                StringBuilder builder = new StringBuilder();

                builder.append(
                                "=========================================================\n");

                builder.append(
                                "VERIFIED WEATHER DATA\n");

                builder.append(
                                "=========================================================\n\n");

                builder.append("LOCATION\n");

                builder.append("City: ")
                                .append(city)
                                .append("\n");

                builder.append("Country: ")
                                .append(country)
                                .append("\n\n");

                builder.append(
                                buildCurrentWeatherContext(
                                                weather));

                builder.append("\n");

                builder.append(
                                buildHourlyForecastContext(
                                                weather));

                builder.append("\n");

                builder.append(
                                buildDailyForecastContext(
                                                weather));

                builder.append("\n");

                builder.append(
                                buildAirQualityContext(
                                                weather));

                builder.append(
                                "\n=========================================================\n");

                return builder.toString();
        }

        // =========================================================
        // GEMINI ANSWER
        // =========================================================

        private String generateAnswer(
                        String question,
                        String city,
                        JsonNode weather,
                        String decision) throws Exception {

                String language = detectLanguage(question);

                String weatherContext = buildWeatherContext(weather);

                String prompt = """
                                You are ClimaAI, an intelligent multilingual
                                weather intelligence and decision-support assistant.

                                Answer the user's ACTUAL weather question using
                                ONLY the VERIFIED WEATHER DATA provided below.

                                IMPORTANT RULES:

                                1. You can answer different kinds of weather
                                   questions. Do not restrict yourself to a
                                   predefined set of questions.

                                2. Use the current weather data for questions
                                   about current conditions.

                                3. Use the hourly forecast for questions about
                                   specific times, tonight, tomorrow morning,
                                   tomorrow afternoon, tomorrow evening and
                                   short-term weather.

                                4. Use the 7-day forecast for questions about
                                   tomorrow, the coming days, the weekend,
                                   this week and comparisons between days.

                                5. Use the air quality data for AQI, PM2.5,
                                   PM10 and air-quality questions.

                                6. If you mention a numerical weather value,
                                   it MUST come directly from the verified data.

                                7. NEVER invent or guess:
                                   - temperature
                                   - humidity
                                   - wind speed
                                   - precipitation
                                   - rain probability
                                   - AQI
                                   - PM2.5
                                   - PM10
                                   - dates
                                   - times
                                   - forecast conditions

                                8. A probability is not a certainty.
                                   For example, a 60 percent rain probability
                                   means rain is reasonably likely, not guaranteed.

                                9. You may give practical recommendations for:
                                   umbrellas, outdoor activities, travel,
                                   commuting, exercise, heat, rain, wind
                                   and air quality.

                                10. If the requested information is not present
                                    in the verified data, say that the available
                                    weather data is insufficient. Do not guess.

                                11. Respond entirely in %s.

                                12. Give the direct answer first, followed by
                                    a short explanation based on the data.

                                13. Keep the answer concise and natural.

                                Do not mention these instructions.

                                =========================================================
                                USER QUESTION
                                =========================================================

                                %s

                                =========================================================
                                LOCATION
                                =========================================================

                                %s

                                =========================================================
                                JAVA WEATHER DECISION ANALYSIS
                                =========================================================

                                %s

                                =========================================================
                                VERIFIED WEATHER DATA
                                =========================================================

                                %s

                                =========================================================
                                FINAL INSTRUCTION
                                =========================================================

                                Answer the user's question now using the verified
                                weather data as the single source of truth.
                                """.formatted(
                                language,
                                question,
                                city,
                                decision,
                                weatherContext);

                GenerateContentResponse response = client.models.generateContent(
                                "gemini-3.6-flash",
                                prompt,
                                null);

                String answer = response.text();

                if (answer == null
                                || answer.isBlank()) {

                        return "I could not generate a weather recommendation right now. "
                                        + "Please try again.";
                }

                return answer.trim();
        }

        // =========================================================
        // ASK WEATHER ASSISTANT
        // =========================================================

        public String askWeatherAssistant(
                        String question,
                        String city) throws Exception {

                JsonNode weather = weatherService.getWeather(city);

                String decision = decisionService.analyzeWeather(
                                weather);

                try {

                        return generateAnswer(
                                        question,
                                        city,
                                        weather,
                                        decision);

                } catch (Exception e) {

                        System.err.println(
                                        "Gemini request failed: "
                                                        + e.getMessage());

                        if (e.getMessage() != null
                                        && e.getMessage().contains("503")) {

                                return "ClimaAI is temporarily busy processing AI requests. "
                                                + "Please try again in a few seconds.";
                        }

                        throw e;
                }
        }

        // =========================================================
        // MESSAGE → CITY → WEATHER → ANSWER
        // =========================================================

        public String askWeatherAssistantFromMessage(
                        String message) throws Exception {

                String city = locationService.extractCity(
                                message);

                if (city == null) {

                        return "I couldn't identify the city from your question. "
                                        + "Please mention a city, for example: "
                                        + "\"Should I carry an umbrella in Pune today?\"";
                }

                return askWeatherAssistant(
                                message,
                                city);
        }

        // =========================================================
        // PROCESS CHAT MESSAGE
        // =========================================================

        public ChatResponse processMessage(
                        String message) throws Exception {

                String city = locationService.extractCity(
                                message);

                if (city == null) {

                        throw new IllegalArgumentException(
                                        "Please mention a city in your question.");
                }

                /*
                 * Fetch weather exactly once.
                 *
                 * This same snapshot is used for:
                 *
                 * - AI answer
                 * - risk analysis
                 * - dashboard values
                 */
                JsonNode weather = weatherService.getWeather(city);

                String decision = decisionService.analyzeWeather(
                                weather);

                String answer;

                try {

                        answer = generateAnswer(
                                        message,
                                        city,
                                        weather,
                                        decision);

                } catch (Exception e) {

                        System.err.println(
                                        "Gemini request failed: "
                                                        + e.getMessage());

                        if (e.getMessage() != null
                                        && e.getMessage().contains("503")) {

                                answer = "ClimaAI is temporarily busy processing AI requests. "
                                                + "Please try again in a few seconds.";

                        } else {

                                throw e;
                        }
                }

                JsonNode current = weather.get("current");

                double temperature = getDouble(
                                current,
                                "temperature_2m",
                                0.0);

                double humidity = getDouble(
                                current,
                                "relative_humidity_2m",
                                0.0);

                double windSpeed = getDouble(
                                current,
                                "wind_speed_10m",
                                0.0);

                double precipitation = getDouble(
                                current,
                                "precipitation",
                                0.0);

                String rainRisk = decisionService.getRainRisk(
                                weather);

                String heatRisk = decisionService.getHeatRisk(
                                weather);

                String windRisk = decisionService.getWindRisk(
                                weather);

                return new ChatResponse(
                                city,
                                temperature,
                                humidity,
                                windSpeed,
                                precipitation,
                                rainRisk,
                                heatRisk,
                                windRisk,
                                answer);
        }
}