package com.example.dto;

public class ChatResponse {

    private String city;
    private double temperature;
    private double humidity;
    private double windSpeed;
    private double precipitation;
    private String rainRisk;
    private String heatRisk;
    private String windRisk;
    private String answer;

    public ChatResponse() {
    }

    public ChatResponse(
            String city,
            double temperature,
            double humidity,
            double windSpeed,
            double precipitation,
            String rainRisk,
            String heatRisk,
            String windRisk,
            String answer) {

        this.city = city;
        this.temperature = temperature;
        this.humidity = humidity;
        this.windSpeed = windSpeed;
        this.precipitation = precipitation;
        this.rainRisk = rainRisk;
        this.heatRisk = heatRisk;
        this.windRisk = windRisk;
        this.answer = answer;
    }

    public String getCity() {
        return city;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getHumidity() {
        return humidity;
    }

    public double getWindSpeed() {
        return windSpeed;
    }

    public double getPrecipitation() {
        return precipitation;
    }

    public String getRainRisk() {
        return rainRisk;
    }

    public String getHeatRisk() {
        return heatRisk;
    }

    public String getWindRisk() {
        return windRisk;
    }

    public String getAnswer() {
        return answer;
    }
}