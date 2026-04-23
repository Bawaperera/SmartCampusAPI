# Smart Campus REST API

A JAX-RS REST API built with Jersey and deployed on Apache Tomcat, managing Rooms, Sensors, and Sensor Readings for a university Smart Campus system.

---

## API Design Overview

The API follows REST principles and is versioned under `/api/v1`. It manages three resources:

- **Rooms** — physical campus spaces (libraries, labs)
- **Sensors** — hardware sensors (temperature, CO2, occupancy) linked to rooms
- **Sensor Readings** — time-series data captured by each sensor

Sensor Readings are a **sub-resource** of Sensors, accessed via `/api/v1/sensors/{sensorId}/readings`. This is implemented using a JAX-RS **Sub-Resource Locator** pattern where `SensorResource` delegates to `SensorReadingResource`.

All responses use `application/json`. Errors always return a structured JSON body — never raw stack traces.

---

## Endpoint Reference

| Method | Path | Description | Status Codes |
|--------|------|-------------|--------------|
| GET | `/api/v1/` | Discovery — API metadata and links | 200 |
| GET | `/api/v1/rooms` | List all rooms | 200 |
| POST | `/api/v1/rooms` | Create a new room | 201, 400, 409 |
| GET | `/api/v1/rooms/{roomId}` | Get a specific room | 200, 404 |
| DELETE | `/api/v1/rooms/{roomId}` | Delete a room (must have no sensors) | 204, 404, 409 |
| GET | `/api/v1/sensors` | List all sensors (optional `?type=` filter) | 200 |
| POST | `/api/v1/sensors` | Register a new sensor | 201, 400, 409, 422 |
| GET | `/api/v1/sensors/{sensorId}` | Get a specific sensor | 200, 404 |
| GET | `/api/v1/sensors/{sensorId}/readings` | Get all readings for a sensor | 200, 404 |
| POST | `/api/v1/sensors/{sensorId}/readings` | Add a new reading | 201, 403, 404 |

---

## Build and Run Instructions

### Prerequisites
- Java 11+
- Maven 3.6+
- Apache Tomcat 9.x

### Step 1 — Build the WAR
```bash
mvn clean package
```
This produces `target/smart-campus-api.war`.

### Step 2 — Deploy to Tomcat
Copy the WAR into Tomcat's webapps folder:
```bash
cp target/smart-campus-api.war /path/to/tomcat/webapps/
```

### Step 3 — Start Tomcat
```bash
/path/to/tomcat/bin/startup.sh        # Linux/Mac
/path/to/tomcat/bin/startup.bat       # Windows
```

### Step 4 — Test the API
Open Postman or use curl. Base URL:
```
http://localhost:8080/smart-campus-api/api/v1/
```

### Step 5 — Stop Tomcat
```bash
/path/to/tomcat/bin/shutdown.sh       # Linux/Mac
/path/to/tomcat/bin/shutdown.bat      # Windows
```

---

## Sample curl Commands

### 1. Discovery endpoint
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1/
```

### 2. List all rooms
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1/rooms
```

### 3. Create a new room
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"HALL-A1","name":"Main Hall","capacity":200}'
```

### 4. Create a new sensor (linked to an existing room)
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"HUM-001","type":"HUMIDITY","status":"ACTIVE","roomId":"LIB-301"}'
```

### 5. Filter sensors by type
```bash
curl -X GET "http://localhost:8080/smart-campus-api/api/v1/sensors?type=CO2"
```

### 6. Post a sensor reading
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.7}'
```

### 7. Get readings history for a sensor
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings
```

### 8. Delete a room that has no sensors
```bash
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LIB-301
```

### 9. Attempt to delete a room with sensors (triggers 409)
```bash
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LAB-101
```

### 10. Register sensor with non-existent room (triggers 422)
```bash
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"BAD-001","type":"MOTION","roomId":"FAKE-999"}'
```

---

## Conceptual Questions & Answers

### Part 1 — Setup & Discovery

**Q: What is HATEOAS and what are its benefits?**

HATEOAS (Hypermedia As The Engine Of Application State) is a REST constraint where the API response includes links to related resources. For example, the discovery endpoint at `GET /api/v1/` returns `"links": {"rooms": "/api/v1/rooms", "sensors": "/api/v1/sensors"}`.

Benefits:
- **Self-documenting**: Clients can discover available resources without reading external documentation.
- **Loose coupling**: Clients do not hard-code URLs — if the server changes a path, clients follow the new link.
- **Guided navigation**: Like a website with hyperlinks, clients traverse the API by following links rather than guessing paths.

**Q: Why does `@ApplicationPath("/api/v1")` matter?**

`@ApplicationPath` sets the root URL prefix for the entire JAX-RS application. All `@Path` annotations on resource classes are relative to this prefix. It provides versioning (`/api/v1`) so future breaking changes can be released as `/api/v2` without disrupting existing clients.

---

### Part 2 — Room Management

**Q: Should list responses return full objects or just IDs?**

Returning **full objects** is more convenient for the client (one request gets all data) but increases payload size, especially for large collections. Returning **only IDs** saves bandwidth but forces the client to make N additional GET requests for details (N+1 problem).

Best practice: return full objects for small collections (as done here) and use pagination or ID-only responses for very large datasets.

**Q: Is DELETE idempotent? Justify your answer.**

Yes. DELETE is idempotent: calling `DELETE /rooms/LIB-301` multiple times produces the same end state — the room is gone. The first call removes it and returns `204 No Content`. Every subsequent call returns `404 Not Found`. Although the status codes differ, the **effect on the server state** is identical (room does not exist), satisfying the idempotency requirement.

---

### Part 3 — Sensors & Filtering

**Q: How does `@Consumes(MediaType.APPLICATION_JSON)` handle a content-type mismatch?**

If a client sends a POST request with `Content-Type: text/plain` to a method annotated with `@Consumes("application/json")`, JAX-RS automatically returns **HTTP 415 Unsupported Media Type** without invoking the method at all. This is handled entirely by the framework before the developer's code runs.

**Q: Why use `@QueryParam` for filtering instead of a path parameter?**

- `@PathParam` (`/sensors/CO2`) implies CO2 is a specific resource identified by that path — it would conflict with sensor IDs.
- `@QueryParam` (`/sensors?type=CO2`) expresses an **optional filter on a collection**. The path `/sensors` always refers to the full collection; the query string modifies how it is retrieved.

Query params are superior for filtering because they are optional (the endpoint still works without them), they can be combined (`?type=CO2&status=ACTIVE`), and they clearly communicate "I am searching within a collection."

---

### Part 4 — Sub-Resources

**Q: How does the Sub-Resource Locator pattern manage complexity?**

Without sub-resource locators, all logic for `/sensors` and `/sensors/{id}/readings` would live in one giant `SensorResource` class. As the API grows, this becomes difficult to maintain and test.

A sub-resource locator (`@Path("{sensorId}/readings")` without `@GET`/`@POST`) delegates the nested path to a separate `SensorReadingResource` class. Benefits:
- **Separation of concerns**: Readings logic is entirely in `SensorReadingResource`.
- **Independent testing**: Each class can be unit-tested in isolation.
- **Scalability**: New sub-resources (e.g., `/readings/{id}/alerts`) can be added without modifying `SensorResource`.
- **Reduced complexity**: No single class handles too many responsibilities.

---

### Part 5 — Error Handling

**Q: Why is HTTP 422 Unprocessable Entity more accurate than 404 Not Found when a sensor references a non-existent room?**

- `404 Not Found` means the **requested URL** does not exist — it signals a routing problem.
- `422 Unprocessable Entity` means the request was syntactically valid JSON and the endpoint was found, but the **semantic content** is invalid (the referenced `roomId` does not exist).

Using 422 is more semantically precise: we are saying "we understood your request, but the data within it refers to something that does not exist." A client receiving 404 might think the `/sensors` endpoint itself is missing, which is incorrect.

**Q: What are the cybersecurity risks of exposing raw stack traces in API error responses?**

Exposing stack traces is a serious security vulnerability (OWASP A05: Security Misconfiguration). Risks include:

1. **Internal path disclosure**: Stack traces reveal file paths and package names (e.g., `com.smartcampus.storage.DataStore`), helping attackers map the application's internal structure.
2. **Library version exposure**: Exception messages often reveal framework and library versions, which attackers use to search for known CVEs.
3. **Logic disclosure**: Traces reveal method call sequences, helping attackers understand business logic and identify exploitable paths.
4. **Information for targeted attacks**: Knowing which database driver, ORM, or framework is in use narrows the attack surface significantly.

The `GenericExceptionMapper` prevents this by catching all `Throwable` exceptions, logging them server-side, and returning only a generic `500 Internal Server Error` JSON body to the client.

---

## Project Structure

```
src/
└── main/
    ├── java/com/smartcampus/
    │   ├── SmartCampusApplication.java       # JAX-RS application entry point
    │   ├── model/
    │   │   ├── Room.java
    │   │   ├── Sensor.java
    │   │   └── SensorReading.java
    │   ├── storage/
    │   │   └── DataStore.java                # In-memory singleton data store
    │   ├── resource/
    │   │   ├── DiscoveryResource.java        # GET /api/v1/
    │   │   ├── RoomResource.java             # /api/v1/rooms
    │   │   ├── SensorResource.java           # /api/v1/sensors (+ sub-resource locator)
    │   │   └── SensorReadingResource.java    # /api/v1/sensors/{id}/readings
    │   ├── exception/
    │   │   ├── RoomNotEmptyException.java
    │   │   ├── LinkedResourceNotFoundException.java
    │   │   ├── SensorUnavailableException.java
    │   │   └── mapper/
    │   │       ├── RoomNotEmptyMapper.java          # 409 Conflict
    │   │       ├── LinkedResourceNotFoundMapper.java # 422 Unprocessable Entity
    │   │       ├── SensorUnavailableMapper.java     # 403 Forbidden
    │   │       └── GenericExceptionMapper.java      # 500 Internal Server Error
    │   └── filter/
    │       └── RequestResponseLoggingFilter.java    # Logs all requests and responses
    └── webapp/
        └── WEB-INF/
            └── web.xml                       # Tomcat servlet configuration
```
