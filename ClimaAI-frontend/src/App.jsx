import { useEffect, useRef, useState } from "react";
import "./App.css";

const API_BASE = "https://climaai-backend.onrender.com";

function getWeatherInfo(code) {
  if (code === 0) return { icon: "☀️", text: "Clear sky" };

  if ([1, 2, 3].includes(code))
    return { icon: "🌤️", text: "Partly cloudy" };

  if ([45, 48].includes(code))
    return { icon: "🌫️", text: "Foggy" };

  if ([51, 53, 55].includes(code))
    return { icon: "🌦️", text: "Drizzle" };

  if ([61, 63, 65].includes(code))
    return { icon: "🌧️", text: "Rain" };

  if ([71, 73, 75].includes(code))
    return { icon: "🌨️", text: "Snow" };

  if ([80, 81, 82].includes(code))
    return { icon: "🌧️", text: "Rain showers" };

  if ([95, 96, 99].includes(code))
    return { icon: "⛈️", text: "Thunderstorm" };

  return { icon: "🌡️", text: "Unknown conditions" };
}

function App() {
  const [city, setCity] = useState("");
  const [weather, setWeather] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const [question, setQuestion] = useState("");
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResponse, setAiResponse] = useState(null);
  const [aiError, setAiError] = useState("");

  // ================================
  // VOICE INPUT
  // ================================

  const [isListening, setIsListening] = useState(false);
  const [voiceSupported, setVoiceSupported] = useState(true);
  const [language, setLanguage] = useState("en-IN");

  const recognitionRef = useRef(null);

  const languageOptions = {
    "en-IN": {
      name: "English",
      shortName: "English",
      instruction: "English",

      quickQuestions: [
        {
          label: "☔ Do I need an umbrella?",
          question:
            "Should I carry an umbrella today?",
        },
        {
          label: "🏃 Outdoor activities",
          question:
            "Is it good for outdoor activities today?",
        },
        {
          label: "✈️ Is it safe to travel?",
          question:
            "Is it safe to travel today?",
        },
        {
          label: "🌡️ Will it be hot?",
          question:
            "Will it be hot today?",
        },
      ],
    },

    "hi-IN": {
      name: "हिंदी",
      shortName: "हिंदी",
      instruction: "Hindi",

      quickQuestions: [
        {
          label: "☔ क्या छाता चाहिए?",
          question:
            "क्या मुझे आज छाता लेकर जाना चाहिए?",
        },
        {
          label: "🏃 बाहर की गतिविधियाँ",
          question:
            "क्या आज बाहर की गतिविधियों के लिए मौसम अच्छा है?",
        },
        {
          label: "✈️ क्या यात्रा सुरक्षित है?",
          question:
            "क्या आज यात्रा करना सुरक्षित है?",
        },
        {
          label: "🌡️ क्या आज गर्मी होगी?",
          question:
            "क्या आज बहुत गर्मी होगी?",
        },
      ],
    },

    "mr-IN": {
      name: "मराठी",
      shortName: "मराठी",
      instruction: "Marathi",

      quickQuestions: [
        {
          label: "☔ छत्री घ्यावी का?",
          question:
            "आज मला छत्री घेऊन जावे का?",
        },
        {
          label: "🏃 बाहेरील उपक्रम",
          question:
            "आज बाहेरील उपक्रमांसाठी हवामान चांगले आहे का?",
        },
        {
          label: "✈️ प्रवास सुरक्षित आहे का?",
          question:
            "आज प्रवास करणे सुरक्षित आहे का?",
        },
        {
          label: "🌡️ आज उष्णता असेल का?",
          question:
            "आज खूप उष्णता असेल का?",
        },
      ],
    },
  };

  useEffect(() => {
    const SpeechRecognition =
      window.SpeechRecognition ||
      window.webkitSpeechRecognition;

    if (!SpeechRecognition) {
      setVoiceSupported(false);
      return;
    }

    const recognition = new SpeechRecognition();

    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.lang = language;

    recognition.onstart = () => {
      setIsListening(true);
      setAiError("");
    };

    recognition.onresult = (event) => {
      const transcript =
        event.results[0][0].transcript;

      console.log(
        "Voice transcript:",
        transcript
      );

      setQuestion(transcript);
    };

    recognition.onerror = (event) => {
      console.error(
        "Speech recognition error:",
        event.error
      );

      setIsListening(false);

      if (event.error === "not-allowed") {
        setAiError(
          "Microphone permission was denied. Please allow microphone access in your browser."
        );
      } else if (event.error === "no-speech") {
        setAiError(
          "I couldn't hear anything. Please try speaking again."
        );
      } else {
        setAiError(
          "Voice input failed. Please try again."
        );
      }
    };

    recognition.onend = () => {
      setIsListening(false);
    };

    recognitionRef.current = recognition;

    return () => {
      try {
        recognition.stop();
      } catch (err) {
        // Recognition may already be stopped.
      }
    };
  }, []);

  // Update speech recognition language
  useEffect(() => {
    if (recognitionRef.current) {
      recognitionRef.current.lang = language;
    }
  }, [language]);

  const startVoiceInput = () => {
    if (!voiceSupported) {
      setAiError(
        "Voice input is not supported by this browser. Please use Google Chrome or Microsoft Edge."
      );
      return;
    }

    if (!recognitionRef.current) {
      setAiError(
        "Voice recognition is not available right now."
      );
      return;
    }

    if (isListening) {
      try {
        recognitionRef.current.stop();
      } catch (err) {
        console.error(
          "Could not stop voice input:",
          err
        );
      }

      return;
    }

    setAiError("");

    try {
      recognitionRef.current.lang = language;
      recognitionRef.current.start();
    } catch (err) {
      console.error(
        "Could not start voice input:",
        err
      );
    }
  };

  // ================================
  // WEATHER SEARCH
  // ================================

  const searchWeather = async (e) => {
    e.preventDefault();

    const trimmedCity = city.trim();

    if (!trimmedCity) {
      setError("Please enter a city.");
      return;
    }

    const searchCity =
      trimmedCity.toLowerCase() === "bangalore"
        ? "Bengaluru"
        : trimmedCity;

    setLoading(true);
    setError("");
    setAiError("");

    // Clear previous AI result immediately.
    setAiResponse(null);

    try {
      const response = await fetch(
        `${API_BASE}/api/weather?city=${encodeURIComponent(searchCity)}`
      );

      if (!response.ok) {
        throw new Error(
          `Weather request failed: ${response.status}`
        );
      }

      const data = await response.json();

      console.log(
        "Weather response:",
        data
      );

      setWeather(data);

      // Keep the searched city synchronized.
      setCity(data.city || searchCity);
    } catch (err) {
      console.error(
        "Weather error:",
        err
      );

      setWeather(null);
      setAiResponse(null);

      setError(
        "Could not load weather. Make sure the ClimaAI backend is running."
      );
    } finally {
      setLoading(false);
    }
  };

  // ================================
  // BUILD AI QUESTION
  // ================================

  const buildAIQuestion = (rawQuestion) => {
    const message = rawQuestion.trim();

    if (!message) {
      return "";
    }

    const selectedCity =
      weather?.city?.trim();

    if (!selectedCity) {
      return message;
    }

    const cityAlreadyIncluded =
      message
        .toLowerCase()
        .includes(
          selectedCity.toLowerCase()
        );

    let weatherQuestion = message;

    /*
     * Keep the city in the normal format expected
     * by the backend location resolver.
     */
    if (!cityAlreadyIncluded) {
      if (message.endsWith("?")) {
        weatherQuestion =
          `${message.slice(0, -1)} in ${selectedCity}?`;
      } else {
        weatherQuestion =
          `${message} in ${selectedCity}`;
      }
    }

    /*
     * Language instruction comes AFTER the normal
     * weather question so the backend finds the
     * correct city first.
     */
    const languageInstruction =
      `Response language: ${languageOptions[language].instruction}.`;

    return `${weatherQuestion}

${languageInstruction}`;
  };

  // ================================
  // CLIMAAI
  // ================================

  const askClimaAI = async (
    e,
    presetQuestion = null
  ) => {
    if (e) {
      e.preventDefault();
    }

    const originalQuestion = (
      presetQuestion ?? question
    ).trim();

    if (!originalQuestion) {
      setAiError(
        "Please enter a question."
      );
      return;
    }

    if (!weather?.city) {
      setAiError(
        "Please search for a city first, then ask ClimaAI a question."
      );
      return;
    }

    const message =
      buildAIQuestion(originalQuestion);

    setQuestion(originalQuestion);
    setAiLoading(true);
    setAiError("");
    setAiResponse(null);

    try {
      console.log(
        "Selected language:",
        languageOptions[language].name
      );

      console.log(
        "Sending to ClimaAI:",
        message
      );

      const response = await fetch(
        `${API_BASE}/api/chat`,
        {
          method: "POST",

          headers: {
            "Content-Type": "application/json",
          },

          body: JSON.stringify({
            message: message,
          }),
        }
      );

      const responseText =
        await response.text();

      if (!response.ok) {
        console.error(
          "AI backend error:",
          response.status,
          responseText
        );

        throw new Error(
          `AI request failed with status ${response.status}`
        );
      }

      let data;

      try {
        data = JSON.parse(responseText);
      } catch (parseError) {
        console.error(
          "Invalid AI JSON response:",
          responseText
        );

        throw new Error(
          "Backend returned an invalid AI response."
        );
      }

      console.log(
        "ClimaAI response:",
        data
      );

      /*
       * IMPORTANT:
       *
       * The AI analysis cards should always agree
       * with the live weather dashboard.
       *
       * Therefore, use the currently displayed
       * weather values for the numeric cards.
       *
       * Gemini's natural-language answer is still
       * taken from the backend.
       */
      const currentWeatherSnapshot =
        weather?.current;

      const synchronizedResponse = {
        ...data,

        city:
          weather?.city ||
          data.city,

        temperature:
          currentWeatherSnapshot?.temperature_2m ??
          data.temperature,

        humidity:
          currentWeatherSnapshot?.relative_humidity_2m ??
          data.humidity,

        windSpeed:
          currentWeatherSnapshot?.wind_speed_10m ??
          data.windSpeed,

        precipitation:
          currentWeatherSnapshot?.precipitation ??
          data.precipitation,

        /*
         * Keep risk values calculated by the backend.
         */
        rainRisk: data.rainRisk,
        heatRisk: data.heatRisk,
        windRisk: data.windRisk,

        /*
         * Keep AQI synchronized with the live
         * weather dashboard as well.
         */
        airQuality:
          weather?.airQuality ??
          data.airQuality,
      };

      console.log(
        "Synchronized ClimaAI response:",
        synchronizedResponse
      );

      setAiResponse(
        synchronizedResponse
      );

    } catch (err) {
      console.error(
        "ClimaAI error:",
        err
      );

      setAiError(
        `ClimaAI error: ${err.message}`
      );
    } finally {
      setAiLoading(false);
    }
  };

  // ================================
  // DATA
  // ================================

  const current =
    weather?.current;

  const daily =
    weather?.daily;

  const airQuality =
    weather?.airQuality;

  const currentWeather =
    current
      ? getWeatherInfo(
        current.weather_code
      )
      : null;

  const todayRainProbability =
    daily?.precipitation_probability_max?.[0] ??
    0;

  return (
    <div className="app">

      {/* ================================
          HEADER
      ================================ */}

      <header className="header">

        <div className="brand">

          <img
            src="ClimaAI-logo1.png"
            alt="ClimaAI - Conversational Weather Intelligence"
            className="brand-logo"
          />

        </div>

        <div className="header-status">

          <span className="status-dot"></span>

          Weather Intelligence Online

        </div>

      </header>

      <main className="main">

        {/* ================================
            HERO
        ================================ */}

        <section className="hero">

          <p className="eyebrow">
            SMART WEATHER ASSISTANT
          </p>

          <h2>
            Understand the weather.
            <br />

            <span>
              Plan your day smarter.
            </span>

          </h2>

          <p className="hero-text">
            Get accurate weather insights,
            forecasts and AI-powered
            recommendations for any location.
          </p>

          <form
            className="search-box"
            onSubmit={searchWeather}
          >

            <span className="search-icon">
              ⌕
            </span>

            <input
              type="text"
              placeholder="Search for a city..."
              value={city}
              onChange={(e) =>
                setCity(e.target.value)
              }
            />

            <button
              type="submit"
              disabled={loading}
            >
              {loading
                ? "Loading..."
                : "Search"}
            </button>

          </form>

          {error && (
            <p className="error-message">
              {error}
            </p>
          )}

        </section>

        {/* ================================
            WEATHER SECTION
        ================================ */}

        {weather && current && (
          <>

            <section className="weather-grid">

              {/* CURRENT WEATHER */}

              <div className="weather-card main-weather">

                <div className="card-top">

                  <div>

                    <p className="label">
                      CURRENT WEATHER
                    </p>

                    <h3>
                      {weather.city}
                    </h3>

                    <p className="muted">
                      {weather.country} · Updated{" "}
                      {current.time
                        ? current.time.substring(
                          11,
                          16
                        )
                        : "now"}
                    </p>

                  </div>

                  <div className="weather-icon">
                    {currentWeather.icon}
                  </div>

                </div>

                <div className="temperature">

                  {Number(
                    current.temperature_2m
                  ).toFixed(1)}

                  <span>
                    °C
                  </span>

                </div>

                <p className="condition">
                  {currentWeather.text}
                </p>

                <div className="weather-details">

                  <div>

                    <span>
                      Feels like
                    </span>

                    <strong>
                      {Number(
                        current.apparent_temperature
                      ).toFixed(1)}
                      °C
                    </strong>

                  </div>

                  <div>

                    <span>
                      Humidity
                    </span>

                    <strong>
                      {Number(
                        current.relative_humidity_2m
                      ).toFixed(0)}
                      %
                    </strong>

                  </div>

                  <div>

                    <span>
                      Wind
                    </span>

                    <strong>
                      {Number(
                        current.wind_speed_10m
                      ).toFixed(1)}{" "}
                      km/h
                    </strong>

                  </div>

                </div>

                <div className="extra-weather">

                  <div>

                    <span>
                      Current precipitation
                    </span>

                    <strong>
                      {Number(
                        current.precipitation
                      ).toFixed(1)}{" "}
                      mm
                    </strong>

                  </div>

                  <div>

                    <span>
                      Coordinates
                    </span>

                    <strong>
                      {Number(
                        weather.latitude
                      ).toFixed(2)},{" "}
                      {Number(
                        weather.longitude
                      ).toFixed(2)}
                    </strong>

                  </div>

                </div>

              </div>

              {/* WEATHER STATUS */}

              <div className="weather-card ai-card">

                <div className="ai-title">

                  <div className="ai-icon">
                    ✦
                  </div>

                  <div>

                    <p className="label">
                      WEATHER STATUS
                    </p>

                    <h3>
                      Current conditions
                    </h3>

                  </div>

                </div>

                <p className="ai-message">

                  ClimaAI is using live weather
                  information for{" "}

                  <strong>
                    {weather.city}
                  </strong>.

                </p>

                <div className="recommendation">

                  <span>
                    ✓
                  </span>

                  Current weather data loaded
                  successfully

                </div>

                <div className="risk-list">

                  <div>

                    <span>
                      Weather code
                    </span>

                    <strong>
                      {current.weather_code}
                    </strong>

                  </div>

                  <div>

                    <span>
                      Current precipitation
                    </span>

                    <strong>
                      {Number(
                        current.precipitation
                      ).toFixed(1)}{" "}
                      mm
                    </strong>

                  </div>

                  <div>

                    <span>
                      Rain probability
                    </span>

                    <strong>
                      {todayRainProbability}%
                    </strong>

                  </div>

                </div>

              </div>

              {/* AIR QUALITY */}

              <div className="weather-card air-quality-card">

                <div className="ai-title">

                  <div className="ai-icon">
                    🌫️
                  </div>

                  <div>

                    <p className="label">
                      AIR QUALITY
                    </p>

                    <h3>
                      Air Quality Index
                    </h3>

                  </div>

                </div>

                {airQuality?.us_aqi >= 0 ? (

                  <>

                    <div className="aqi-main">

                      <div className="aqi-number">
                        {Number(
                          airQuality.us_aqi
                        ).toFixed(0)}
                      </div>

                      <div className="aqi-status">
                        {airQuality.status}
                      </div>

                    </div>

                    <div className="aqi-details">

                      <div>

                        <span>
                          PM2.5
                        </span>

                        <strong>
                          {Number(
                            airQuality.pm2_5
                          ).toFixed(1)}

                          <small>
                            {" "}µg/m³
                          </small>
                        </strong>

                      </div>

                      <div>

                        <span>
                          PM10
                        </span>

                        <strong>
                          {Number(
                            airQuality.pm10
                          ).toFixed(1)}

                          <small>
                            {" "}µg/m³
                          </small>
                        </strong>

                      </div>

                    </div>

                    <p className="aqi-note">
                      Based on the current air quality
                      conditions for {weather.city}.
                    </p>

                  </>

                ) : (

                  <div className="aqi-unavailable">
                    Air quality data is temporarily
                    unavailable.
                  </div>

                )}

              </div>

            </section>

            {/* ================================
                7 DAY FORECAST
            ================================ */}

            {daily && daily.time && (

              <section className="section forecast-section">

                <div className="section-heading">

                  <div>

                    <p className="label">
                      FORECAST
                    </p>

                    <h3>
                      Next 7 days
                    </h3>

                  </div>

                </div>

                <div className="forecast-grid">

                  {daily.time.map(
                    (date, index) => {

                      const info =
                        getWeatherInfo(
                          daily.weather_code?.[
                          index
                          ]
                        );

                      const rainChance =
                        daily
                          .precipitation_probability_max?.[
                        index
                        ] ?? 0;

                      const high =
                        daily
                          .temperature_2m_max?.[
                        index
                        ];

                      const low =
                        daily
                          .temperature_2m_min?.[
                        index
                        ];

                      return (

                        <div
                          className="forecast-card"
                          key={date}
                        >

                          <span className="forecast-day">

                            {index === 0
                              ? "Today"
                              : new Date(
                                `${date}T00:00:00`
                              ).toLocaleDateString(
                                "en-US",
                                {
                                  weekday:
                                    "short",
                                }
                              )}

                          </span>

                          <div className="forecast-icon">
                            {info.icon}
                          </div>

                          <div className="forecast-temp">

                            <strong>
                              {Number(
                                high
                              ).toFixed(0)}
                              °
                            </strong>

                            <span>
                              /{" "}
                              {Number(
                                low
                              ).toFixed(0)}
                              °
                            </span>

                          </div>

                          <small>
                            {info.text}
                          </small>

                          <div className="rain-info">

                            <span>
                              🌧
                            </span>

                            <div className="rain-bar">

                              <div
                                className="rain-fill"
                                style={{
                                  width: `${Math.min(
                                    Number(
                                      rainChance
                                    ),
                                    100
                                  )}%`,
                                }}
                              ></div>

                            </div>

                            <strong>
                              {rainChance}%
                            </strong>

                          </div>

                          <p className="rain-label">
                            Rain chance
                          </p>

                        </div>

                      );
                    }
                  )}

                </div>

                {/* TEMPERATURE TREND */}

                <div className="temperature-trend">

                  <div className="trend-header">

                    <div>

                      <p className="label">
                        TEMPERATURE TREND
                      </p>

                      <h4>
                        7-day temperature outlook
                      </h4>

                    </div>

                    <span>
                      °C
                    </span>

                  </div>

                  {daily?.temperature_2m_max &&
                    daily?.temperature_2m_min && (

                      <div className="trend-chart">

                        {(() => {

                          const highs =
                            daily.temperature_2m_max.map(
                              Number
                            );

                          const lows =
                            daily.temperature_2m_min.map(
                              Number
                            );

                          const chartMin =
                            Math.floor(
                              Math.min(
                                ...lows
                              ) - 2
                            );

                          const chartMax =
                            Math.ceil(
                              Math.max(
                                ...highs
                              ) + 2
                            );

                          const range =
                            Math.max(
                              chartMax -
                              chartMin,
                              1
                            );

                          const chartWidth =
                            900;

                          const chartHeight =
                            230;

                          const paddingX =
                            55;

                          const paddingTop =
                            30;

                          const paddingBottom =
                            40;

                          const usableWidth =
                            chartWidth -
                            paddingX * 2;

                          const usableHeight =
                            chartHeight -
                            paddingTop -
                            paddingBottom;

                          const getX =
                            (index) =>
                              paddingX +
                              (index *
                                usableWidth) /
                              Math.max(
                                highs.length -
                                1,
                                1
                              );

                          const getY =
                            (temperature) =>
                              paddingTop +
                              ((chartMax -
                                temperature) /
                                range) *
                              usableHeight;

                          const highPoints =
                            highs
                              .map(
                                (
                                  temp,
                                  index
                                ) =>
                                  `${getX(
                                    index
                                  )},${getY(
                                    temp
                                  )}`
                              )
                              .join(" ");

                          const lowPoints =
                            lows
                              .map(
                                (
                                  temp,
                                  index
                                ) =>
                                  `${getX(
                                    index
                                  )},${getY(
                                    temp
                                  )}`
                              )
                              .join(" ");

                          return (

                            <svg
                              className="trend-svg"
                              viewBox={`0 0 ${chartWidth} ${chartHeight}`}
                              preserveAspectRatio="none"
                            >

                              <line
                                x1={paddingX}
                                y1={getY(
                                  chartMax
                                )}
                                x2={
                                  chartWidth -
                                  paddingX
                                }
                                y2={getY(
                                  chartMax
                                )}
                                className="chart-grid-line"
                              />

                              <line
                                x1={paddingX}
                                y1={getY(
                                  chartMin +
                                  range *
                                  0.25
                                )}
                                x2={
                                  chartWidth -
                                  paddingX
                                }
                                y2={getY(
                                  chartMin +
                                  range *
                                  0.25
                                )}
                                className="chart-grid-line"
                              />

                              <line
                                x1={paddingX}
                                y1={getY(
                                  chartMin +
                                  range *
                                  0.5
                                )}
                                x2={
                                  chartWidth -
                                  paddingX
                                }
                                y2={getY(
                                  chartMin +
                                  range *
                                  0.5
                                )}
                                className="chart-grid-line"
                              />

                              <line
                                x1={paddingX}
                                y1={getY(
                                  chartMin +
                                  range *
                                  0.75
                                )}
                                x2={
                                  chartWidth -
                                  paddingX
                                }
                                y2={getY(
                                  chartMin +
                                  range *
                                  0.75
                                )}
                                className="chart-grid-line"
                              />

                              <polyline
                                points={highPoints}
                                className="temperature-line high-line"
                                fill="none"
                              />

                              <polyline
                                points={lowPoints}
                                className="temperature-line low-line"
                                fill="none"
                              />

                              {highs.map(
                                (
                                  temp,
                                  index
                                ) => (

                                  <g
                                    key={`high-${index}`}
                                  >

                                    <circle
                                      cx={getX(
                                        index
                                      )}
                                      cy={getY(
                                        temp
                                      )}
                                      r="5"
                                      className="temperature-dot high-dot"
                                    />

                                    <text
                                      x={getX(
                                        index
                                      )}
                                      y={
                                        getY(
                                          temp
                                        ) -
                                        13
                                      }
                                      textAnchor="middle"
                                      className="temperature-label high-label"
                                    >
                                      {temp.toFixed(
                                        0
                                      )}
                                      °
                                    </text>

                                  </g>

                                )
                              )}

                              {lows.map(
                                (
                                  temp,
                                  index
                                ) => (

                                  <g
                                    key={`low-${index}`}
                                  >

                                    <circle
                                      cx={getX(
                                        index
                                      )}
                                      cy={getY(
                                        temp
                                      )}
                                      r="4"
                                      className="temperature-dot low-dot"
                                    />

                                    <text
                                      x={getX(
                                        index
                                      )}
                                      y={
                                        getY(
                                          temp
                                        ) +
                                        20
                                      }
                                      textAnchor="middle"
                                      className="temperature-label low-label"
                                    >
                                      {temp.toFixed(
                                        0
                                      )}
                                      °
                                    </text>

                                  </g>

                                )
                              )}

                              {daily.time.map(
                                (
                                  date,
                                  index
                                ) => (

                                  <text
                                    key={date}
                                    x={getX(
                                      index
                                    )}
                                    y={
                                      chartHeight -
                                      10
                                    }
                                    textAnchor="middle"
                                    className="chart-day"
                                  >
                                    {index === 0
                                      ? "Today"
                                      : new Date(
                                        `${date}T00:00:00`
                                      ).toLocaleDateString(
                                        "en-US",
                                        {
                                          weekday:
                                            "short",
                                        }
                                      )}
                                  </text>

                                )
                              )}

                            </svg>

                          );

                        })()}

                      </div>

                    )}

                </div>

              </section>

            )}

          </>
        )}

        {/* ================================
            EMPTY STATE
        ================================ */}

        {!weather &&
          !loading &&
          !error && (

            <section className="empty-state">

              <div>
                🌍
              </div>

              <h3>
                Search for a location
              </h3>

              <p>
                Enter any city above to see its
                live weather and 7-day forecast.
              </p>

            </section>

          )}

        {/* ================================
            AI ASSISTANT
        ================================ */}

        <section className="assistant">

          <div className="assistant-content">

            <p className="label">
              ASK ClimaAI
            </p>

            <h3>
              Have a weather question?
            </h3>

            <p>
              Ask ClimaAI about weather, travel
              plans, outdoor activities, rain, heat
              and more.
            </p>

            {/* LANGUAGE SELECTOR */}

            <div className="language-selector">

              <label htmlFor="language">
                🌐 Language
              </label>

              <select
                id="language"
                value={language}
                onChange={(e) =>
                  setLanguage(
                    e.target.value
                  )
                }
                disabled={
                  isListening ||
                  aiLoading
                }
              >

                <option value="en-IN">
                  English
                </option>

                <option value="hi-IN">
                  हिंदी
                </option>

                <option value="mr-IN">
                  मराठी
                </option>

              </select>

            </div>

            <form
              className="ai-form"
              onSubmit={askClimaAI}
            >

              <div className="voice-input-wrapper">

                <input
                  type="text"
                  placeholder={
                    weather?.city
                      ? `Ask in ${languageOptions[language].shortName}...`
                      : "Search a city first, then ask ClimaAI..."
                  }
                  value={question}
                  onChange={(e) =>
                    setQuestion(e.target.value)
                  }
                />

                {voiceSupported && (

                  <button
                    type="button"
                    className={`voice-button ${isListening ? "listening" : ""
                      }`}
                    onClick={startVoiceInput}
                    title={
                      isListening
                        ? "Stop listening"
                        : `Speak in ${languageOptions[language].name}`
                    }
                    disabled={aiLoading}
                    aria-label={
                      isListening
                        ? "Stop voice input"
                        : "Start voice input"
                    }
                  >

                    {isListening ? (
                      <span className="mic-listening-icon">
                        ●
                      </span>
                    ) : (
                      <svg
                        className="mic-svg"
                        viewBox="0 0 24 24"
                        aria-hidden="true"
                      >
                        <path
                          d="M12 14.5a3.5 3.5 0 0 0 3.5-3.5V6a3.5 3.5 0 0 0-7 0v5a3.5 3.5 0 0 0 3.5 3.5Z"
                        />

                        <path
                          d="M18.5 11a6.5 6.5 0 0 1-13 0"
                        />

                        <path
                          d="M12 17.5V21"
                        />

                        <path
                          d="M8.5 21h7"
                        />
                      </svg>
                    )}

                  </button>

                )}

              </div>

              <button
                type="submit"
                className="ai-submit-button"
                disabled={
                  aiLoading ||
                  isListening
                }
              >

                {aiLoading
                  ? "Thinking..."
                  : "Ask ClimaAI ✦"}

              </button>

            </form>

            {isListening && (

              <p className="voice-status">

                🎙️ Listening in{" "}
                <strong>
                  {languageOptions[
                    language
                  ].name}
                </strong>
                ... Speak your weather
                question.

              </p>

            )}

            {/* QUICK QUESTIONS */}

            <div className="quick-questions">

              <span className="quick-label">
                {language === "en-IN"
                  ? "TRY ASKING"
                  : language === "hi-IN"
                    ? "पूछकर देखें"
                    : "विचारून पहा"}
              </span>

              <div className="quick-question-list">

                {languageOptions[
                  language
                ].quickQuestions.map(
                  (item, index) => (

                    <button
                      key={index}
                      type="button"
                      onClick={() =>
                        askClimaAI(
                          null,
                          item.question
                        )
                      }
                      disabled={
                        aiLoading
                      }
                    >
                      {item.label}
                    </button>

                  )
                )}

              </div>

            </div>

            {aiError && (

              <p className="error-message">
                {aiError}
              </p>

            )}

            {/* ================================
                AI RESPONSE
            ================================ */}

            {aiResponse && (

              <div className="ai-response">

                <div className="response-header">

                  <span>
                    ✦ ClimaAI Analysis
                  </span>

                  <span>
                    {aiResponse.city}
                  </span>

                </div>

                <div className="ai-answer-box">

                  <div className="answer-icon">
                    ☔
                  </div>

                  <div>

                    <p className="answer-label">
                      AI RECOMMENDATION
                    </p>

                    <p className="response-answer">
                      {aiResponse.answer
                        ?.replace(
                          /\*\*/g,
                          ""
                        )
                        .replace(
                          /#/g,
                          ""
                        )}
                    </p>

                  </div>

                </div>

                <div className="response-stats">

                  <div>

                    <span>
                      Temperature
                    </span>

                    <strong>
                      {Number(
                        aiResponse.temperature
                      ).toFixed(1)}
                      °C
                    </strong>

                  </div>

                  <div>

                    <span>
                      Humidity
                    </span>

                    <strong>
                      {Number(
                        aiResponse.humidity
                      ).toFixed(0)}
                      %
                    </strong>

                  </div>

                  <div
                    className={`risk-box ${String(
                      aiResponse.rainRisk
                    ).toLowerCase()}`}
                  >

                    <span>
                      Rain risk
                    </span>

                    <strong>
                      {aiResponse.rainRisk}
                    </strong>

                  </div>

                  <div
                    className={`risk-box ${String(
                      aiResponse.heatRisk
                    ).toLowerCase()}`}
                  >

                    <span>
                      Heat risk
                    </span>

                    <strong>
                      {aiResponse.heatRisk}
                    </strong>

                  </div>

                  <div
                    className={`risk-box ${String(
                      aiResponse.windRisk
                    ).toLowerCase()}`}
                  >

                    <span>
                      Wind risk
                    </span>

                    <strong>
                      {aiResponse.windRisk}
                    </strong>

                  </div>

                </div>

                {/* WEATHER INTELLIGENCE */}

                <div className="risk-overview">

                  <div className="risk-overview-header">

                    <div>

                      <p className="label">
                        WEATHER INTELLIGENCE
                      </p>

                      <h4>
                        Risk Overview
                      </h4>

                    </div>

                    <span className="risk-live">

                      <span></span>

                      LIVE

                    </span>

                  </div>

                  <div className="risk-overview-grid">

                    <div
                      className={`overview-risk ${String(
                        aiResponse.rainRisk
                      ).toLowerCase()}`}
                    >

                      <div className="overview-icon">
                        🌧️
                      </div>

                      <div>

                        <span>
                          Rain Risk
                        </span>

                        <strong>
                          {aiResponse.rainRisk}
                        </strong>

                      </div>

                    </div>

                    <div
                      className={`overview-risk ${String(
                        aiResponse.heatRisk
                      ).toLowerCase()}`}
                    >

                      <div className="overview-icon">
                        🔥
                      </div>

                      <div>

                        <span>
                          Heat Risk
                        </span>

                        <strong>
                          {aiResponse.heatRisk}
                        </strong>

                      </div>

                    </div>

                    <div
                      className={`overview-risk ${String(
                        aiResponse.windRisk
                      ).toLowerCase()}`}
                    >

                      <div className="overview-icon">
                        💨
                      </div>

                      <div>

                        <span>
                          Wind Risk
                        </span>

                        <strong>
                          {aiResponse.windRisk}
                        </strong>

                      </div>

                    </div>

                  </div>

                </div>

              </div>

            )}

          </div>

        </section>

      </main>

      {/* ================================
          FOOTER
      ================================ */}

      <footer>

        <span>
          ClimaAI
        </span>

        <span>
          Intelligent weather • Smarter decisions
        </span>

      </footer>

    </div>
  );
}

export default App;
