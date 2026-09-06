package com.example.service;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

@Service
public class DecisionService {

    public String analyzeWeather(JsonNode weather) {

        JsonNode current = weather.get("current");
        JsonNode hourly = weather.get("hourly");

        double temperature =
                current.get("temperature_2m").asDouble();

        double windSpeed =
                current.get("wind_speed_10m").asDouble();

        // Analyze next 6 hours
        double maxRainProbability = 0;

        JsonNode rainProbabilities =
                hourly.get("precipitation_probability");

        int hoursToCheck =
                Math.min(6, rainProbabilities.size());

        for (int i = 0; i < hoursToCheck; i++) {

            double probability =
                    rainProbabilities.get(i).asDouble();

            if (probability > maxRainProbability) {
                maxRainProbability = probability;
            }
        }

        StringBuilder result = new StringBuilder();

        result.append("Weather Decision Analysis\n\n");

        // Rain analysis
        if (maxRainProbability >= 70) {

            result.append("Rain Risk: HIGH\n");
            result.append("Recommendation: Carry an umbrella. ");
            result.append("Rain is highly likely in the next few hours.\n");

        } else if (maxRainProbability >= 40) {

            result.append("Rain Risk: MODERATE\n");
            result.append("Recommendation: Carry an umbrella if possible.\n");

        } else {

            result.append("Rain Risk: LOW\n");
            result.append("Recommendation: Umbrella probably not required.\n");
        }

        // Temperature analysis
        if (temperature >= 35) {

            result.append("Heat Risk: HIGH\n");
            result.append("Recommendation: Avoid prolonged outdoor activity and stay hydrated.\n");

        } else if (temperature >= 30) {

            result.append("Heat Risk: MODERATE\n");
            result.append("Recommendation: Stay hydrated during outdoor activities.\n");

        } else {

            result.append("Heat Risk: LOW\n");
        }

        // Wind analysis
        if (windSpeed >= 40) {

            result.append("Wind Risk: HIGH\n");
            result.append("Recommendation: Be cautious during outdoor activities.\n");

        } else if (windSpeed >= 25) {

            result.append("Wind Risk: MODERATE\n");

        } else {

            result.append("Wind Risk: LOW\n");
        }

        return result.toString();
    }
    public String getRainRisk(JsonNode weather) {

    JsonNode hourly = weather.get("hourly");

    JsonNode probabilities =
            hourly.get("precipitation_probability");

    double maxRainProbability = 0;

    int hoursToCheck =
            Math.min(6, probabilities.size());

    for (int i = 0; i < hoursToCheck; i++) {

        double probability =
                probabilities.get(i).asDouble();

        if (probability > maxRainProbability) {
            maxRainProbability = probability;
        }
    }

    if (maxRainProbability >= 70) {
        return "HIGH";
    }

    if (maxRainProbability >= 40) {
        return "MODERATE";
    }

    return "LOW";
}
public String getHeatRisk(JsonNode weather) {

    double temperature =
            weather.get("current")
                    .get("temperature_2m")
                    .asDouble();

    if (temperature >= 35) {
        return "HIGH";
    }

    if (temperature >= 30) {
        return "MODERATE";
    }

    return "LOW";
}
public String getWindRisk(JsonNode weather) {

    double windSpeed =
            weather.get("current")
                    .get("wind_speed_10m")
                    .asDouble();

    if (windSpeed >= 40) {
        return "HIGH";
    }

    if (windSpeed >= 25) {
        return "MODERATE";
    }

    return "LOW";
}
}
