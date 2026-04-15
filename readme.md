# SkyDelayForecast ⛈️✈️

> **Predicting Spain's airport bottlenecks before they happen by crossing OpenWeatherMap insights with real-time flight scraping.**

## 📌 Project Overview
`SkyDelayForecast` is a data-driven platform designed to identify and forecast congestion at Spain's major airports. The system operates by correlating two critical data streams:
1. **Detailed Weather:** Capture of microclimates and forecasts via the *OpenWeatherMap API*.
2. **Flight Status:** Real-time web scraping of flight boards to detect delays and cancellations.

By cross-referencing these variables, the system visualizes how specific weather phenomena impact the operational efficiency of Spanish aerial nodes.

## 🏗️ System Architecture
The project follows an Event-Driven Architecture (EDA) divided into independent modules:

* **`openweathermap-feeder`**: Ingests high-resolution weather data.
* **`flight-delays-feeder`**: Dynamic scraper for real-time flight statuses.
* **`Event Store`**: Persistent storage for historical event analysis and pattern recognition.

### Data Flow Diagram
```mermaid
flowchart LR
    classDef producer fill:#5cb85c,stroke:#4cae4c,color:white,stroke-width:1px
    classDef broker fill:#428bca,stroke:#357ebd,color:white,stroke-width:1px
    classDef queue fill:#5bc0de,stroke:#46b8da,color:white,stroke-width:1px,font-style:italic
    classDef subscriber fill:#f0ad4e,stroke:#eea236,color:white,stroke-width:1px
    classDef storage fill:#d9534f,stroke:#d43f3a,color:white,stroke-width:1px
    classDef Predictor fill:#8754C9,stroke:#4C277C,color:white,stroke-width:1px
    classDef Datamart fill:#AA7E55,stroke:#110D09,color:white,stroke-width:1px

    subgraph Producers
        weather["OpenWeatherMap<br/>Feeder"]:::producer
        flights["Flight-Status<br/>Feeder"]:::producer
    end
    
    subgraph ActiveMQ["Broker (ActiveMQ)"]
        temp["weather.Conditions"]:::queue
        forecast["weather.Forecast"]:::queue
        f_status["flights.Status"]:::queue
        f_delays["flights.Delays"]:::queue
    end
    
    subgraph Subscriber
        esBuilder["Event Store<br/>Builder"]:::subscriber
    end
    
    eventStore[("Event Store<br/>(History DB)")]:::storage
    
    delayPredictor["Delay<br/>Predictor"]:::Predictor
    dataMart[("Datamart<br/>(Final Stats)")]:::Datamart
    
    weather --> temp
    weather --> forecast
    flights --> f_status
    flights --> f_delays
    
    temp --> esBuilder
    forecast --> esBuilder
    f_status --> esBuilder
    f_delays --> esBuilder
    
    esBuilder --> eventStore
    eventStore --> delayPredictor
    
    delayPredictor <--> dataMart
    
    ActiveMQ --> delayPredictor
    
    linkStyle default stroke:#666,stroke-width:2px;
```

## ⚙️ Technical Design

### Class Diagram: `flight-status-feeder`
The following diagram illustrates the internal structure of the flight scraping module:

```mermaid
classDiagram
    direction LR

    class Controller {
        -FlightCrawler crawler
        -FlightScraper scraper
        -FlightStore store
        -String linksPath
        +execute()
    }

    class FlightCrawler {
        <<interface>>
        +getDomesticFlightLinks() Map
    }

    class FlighteraCrawler {
        -List~String~ ICAO_SPAIN_AIRPORTS
        +getDomesticFlightLinks() Map
        +crawlAirport(String airportUrl) List~String~
    }

    class FlightScraper {
        <<interface>>
        +startCapture(FlightStore store, String path)
    }

    class FlighteraScraper {
        -int BATCH_SIZE
        +startCapture(FlightStore store, String path)
        -scrapFlight(String url, Page page) Flight
        -handleCookies(Page page)
    }

    class FlightStore {
        <<interface>>
        +save(Flight flight)
    }

    class SqliteFlightStore {
        -String dbPath
        -initDatabase()
        +save(Flight flight)
    }

    class LinkManager {
        -Path path
        +getPendingLinks() List~String~
        +saveUniqueLinks(List~String~ links)
        +removeProcessedLinks(List~String~ processed)
        -overwrite(List~String~ links)
    }

    class FlightMapper {
        <<utility>>
        +parseDelay(String text) int
        +parseDistance(String text) int
        +extractTimeUTC(String text) String
        +cleanAircraft(String text) String
    }

    class Flight {
        <<record>>
        +String flightId
        +String origin
        +String destination
        +String date
        +String departureTimeUTC
        +String arrivalTimeUTC
        +String status
        +int departureDelay
        +int arrivalDelay
        +int distanceKm
        +String aircraftModel
        -toString() String
    }

    class Main {
        +main(args: String[])
    }

    FlightCrawler <|.. FlighteraCrawler
    FlightScraper <|.. FlighteraScraper
    FlightStore <|.. SqliteFlightStore

    Controller --> FlightCrawler : coordinates
    Controller --> FlightScraper : coordinates
    Controller --> FlightStore : coordinates
    Controller ..> LinkManager : uses to sync files
    
    FlighteraScraper ..> Flight : creates
    FlighteraScraper ..> FlightMapper : uses
    FlighteraScraper ..> FlightStore : persists via
    
    SqliteFlightStore ..> Flight : stores

    Main ..> Controller : starts
```

### Class Diagram: `openweathermap-feeder`
The following diagram illustrates the internal structure of the weather API module:

````mermaid
---
config:
  layout: elk
---
classDiagram
direction LR
    class Airport {
        +String icao
        +String name
        +double lat
        +double lon
    }

    class Weather {
        +String icao
        +String airport
        +String description
        +double temp
        +double feelsLike
        +int humidity
        +int visibility
        +double windSpeed
        +double windGust
        +int cloudsPct
        +long timestamp
    }

    class WeatherFeeder {
        +fetch() List~Weather~
    }

    class WeatherStore {
        +save(Weather w) void
    }

    class OpenWeatherMapFeeder {
        -String apiKey
        -HttpClient client
        -WeatherParser parser
        +fetch() List~Weather~
        -fetchRawJson(double lat, double lon) String
    }

    class SqliteWeatherStore {
        -String connectionUrl
        +save(Weather w) void
        -initTable() void
    }

    class WeatherParser {
        +parse(String jsonRaw, String icao, String airportName) Weather
    }

    class AirportsReader {
        -String csvPath
        +read() List~Airport~
    }

    class Control {
        -WeatherFeeder feeder
        -WeatherStore store
        +execute() void
    }


    class Main {
        +main(String[] args) static
    }

    <<record>> Airport
    <<record>> Weather
    <<interface>> WeatherFeeder
    <<interface>> WeatherStore

    OpenWeatherMapFeeder ..|> WeatherFeeder : implements
    SqliteWeatherStore ..|> WeatherStore : implements
    
    Control --> WeatherFeeder : uses
    Control --> WeatherStore : uses
    
    OpenWeatherMapFeeder --> AirportsReader : uses
    OpenWeatherMapFeeder --> WeatherParser : uses
    
    AirportsReader ..> Airport : creates
    WeatherParser ..> Weather : creates
    
    Main ..> Control : orchestrates
    Main ..> OpenWeatherMapFeeder : uses
````