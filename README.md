# Smart Campus System

A fully functional RESTful API built for the University of Westminster's **Smart Campus** initiative. The system manages physical **Rooms** and **Sensors** across campus, supporting full CRUD operations, historical sensor reading logs, filtered search, and robust error handling — all built using **JAX-RS (Jersey 2.41)** deployed on **Apache Tomcat 9**.

---

## Tech Stack

![Java](https://img.shields.io/badge/Java-11-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Jersey](https://img.shields.io/badge/Jersey-2.41-4CAF50?style=for-the-badge&logo=java&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.6+-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)
![Tomcat](https://img.shields.io/badge/Apache%20Tomcat-9-F8DC75?style=for-the-badge&logo=apachetomcat&logoColor=black)
![Jackson](https://img.shields.io/badge/Jackson-JSON-yellow?style=for-the-badge&logo=json&logoColor=white)
![REST](https://img.shields.io/badge/REST-API-blue?style=for-the-badge&logo=api&logoColor=white)

| Technology | Role in Project |
| --- | --- |
| **Java 11** | Core programming language |
| **JAX-RS (Jersey 2.41)** | Framework for building RESTful endpoints using annotations like `@GET`, `@POST`, `@Path` |
| **Apache Tomcat 9** | Servlet container — the WAR file is deployed here for serving HTTP requests |
| **Jackson** | Automatic Java object ↔ JSON conversion (serialization/deserialization) |
| **Maven** | Dependency management and project build tool |
| **HashMap** | In-memory data store — simulates a database without any external dependencies |

---

## What This System Does

The Smart Campus API acts as the backend for managing physical infrastructure on a university campus. Here is what it enables:

- **Room management** — Register new rooms, retrieve room details, and delete empty rooms
- **Sensor management** — Install sensors into rooms, filter by type (Temperature, CO2, etc.), and track their status
- **Sensor readings** — Record timestamped measurement values from sensors and retrieve the full reading history
- **Business rule enforcement** — Prevents invalid operations like deleting a room that still has sensors, or posting a reading to a sensor under maintenance
- **Error handling** — Every error returns a structured JSON response with a clear message, never a raw Java stack trace
- **Request/Response logging** — Every incoming request and outgoing response is logged server-side using `java.util.logging`

---

## System Architecture

The project follows a clean layered architecture:

```text
HTTP Request
     ↓
[Apache Tomcat 9]               ← Servlet container, serves the deployed WAR file
     ↓
[JAX-RS / Jersey Router]        ← Matches URL + HTTP method to resource class
     ↓
[Resource Layer]                ← Business logic (RoomResource, SensorResource, etc.)
     ↓
[DataStore (Singleton)]         ← In-memory storage using HashMap
     ↓
[Model Layer]                   ← Plain Java objects: Room, Sensor, SensorReading
     ↑
[Exception Mappers]             ← Intercept thrown exceptions → return JSON error responses
     ↑
[Logging Filter]                ← Logs every request and response
```

### Key Design Patterns Used

#### 1. Singleton Pattern — `DataStore`
The `DataStore` class uses the **Singleton design pattern** — there is exactly one shared instance across the entire application, acting like a shared in-memory database.

```java
public static synchronized DataStore getInstance() {
    if (instance == null) {
        instance = new DataStore();
    }
    return instance;
}
```

All resource classes call `DataStore.getInstance()` to access the same data. This guarantees consistency — a room created via the Rooms endpoint is immediately visible to the Sensors endpoint.

#### 2. Sub-Resource Locator Pattern
The `SensorResource` class uses a **sub-resource locator** to handle nested URLs like `/sensors/{sensorId}/readings`. Rather than mapping all logic into one class, it delegates to a dedicated `SensorReadingResource`:

```java
@Path("{sensorId}/readings")
public SensorReadingResource getReadingsResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

Note: this method has **no** `@GET` or `@POST` annotation — it is purely a locator that delegates routing to the returned class.

#### 3. Exception Mapper Pattern
Custom exceptions are thrown in business logic and **mapped to HTTP responses** by dedicated mapper classes annotated with `@Provider`. Jersey automatically intercepts the exception and calls the mapper:

```text
throw new SensorUnavailableException("...")
       ↓
SensorUnavailableMapper.toResponse(exception)
       ↓
HTTP 403 JSON response
```

Each exception has its own dedicated mapper class — there is no global switch statement.

---

## Data Models

### Room
Represents a physical room on campus (classroom, lab, conference hall, etc.).

| Field | Type | Description |
| --- | --- | --- |
| `id` | String | Unique room identifier (e.g., `"LIB-301"`, `"LAB-101"`) |
| `name` | String | Human-readable display name (e.g., `"Library Quiet Study"`) |
| `capacity` | int | Maximum occupancy of the room |
| `sensorIds` | List\<String\> | IDs of all sensors installed in this room (auto-managed) |

Example JSON:
```json
{
  "id": "LIB-301",
  "name": "Library Quiet Study",
  "capacity": 50,
  "sensorIds": ["TEMP-001"]
}
```

### Sensor
Represents a physical sensor device installed in a room.

| Field | Type | Description |
| --- | --- | --- |
| `id` | String | Unique sensor identifier (e.g., `"TEMP-001"`, `"CO2-001"`) |
| `type` | String | Sensor category: `TEMPERATURE`, `CO2`, `HUMIDITY`, `OCCUPANCY`, etc. |
| `status` | String | Operational state: `ACTIVE`, `MAINTENANCE`, or `OFFLINE` |
| `currentValue` | double | The most recent reading value — auto-updated when a new reading is posted |
| `roomId` | String | The room this sensor belongs to (must reference an existing room) |

Example JSON:
```json
{
  "id": "TEMP-001",
  "type": "TEMPERATURE",
  "status": "ACTIVE",
  "currentValue": 22.5,
  "roomId": "LIB-301"
}
```

**Sensor Status Values:**
- `ACTIVE` — Sensor is operational and accepts new readings
- `MAINTENANCE` — Sensor is under maintenance; posting new readings is **blocked** (returns HTTP 403)
- `OFFLINE` — Sensor is disconnected or not responding

### SensorReading
Represents a single timestamped measurement from a sensor.

| Field | Type | Description |
| --- | --- | --- |
| `id` | String | Auto-generated UUID (created server-side, no need to send it) |
| `timestamp` | long | Unix epoch timestamp in milliseconds (auto-set to server time) |
| `value` | double | The measured value (e.g., `22.5` for temperature in °C) |

When you POST a new reading, you only need to provide `value`. The `id` and `timestamp` are generated automatically by the server.

Example JSON (response):
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1713600000000,
  "value": 24.3
}
```

---

## API Reference

**Base URL:** `http://localhost:8080/smart-campus-api/api/v1`

All requests and responses use `Content-Type: application/json`.

> **Note:** The data store is in-memory only. All data is lost when Tomcat restarts. Create your rooms and sensors fresh each session.

---

### Discovery

#### `GET /api/v1`

Returns metadata about the API — useful for confirming the server is running and discovering available resource collections.

**Response:** `200 OK`
```json
{
  "api": "Smart Campus Sensor & Room Management API",
  "version": "1.0",
  "contact": "admin@smartcampus.ac.uk",
  "status": "running",
  "links": {
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```

---

### Rooms

#### `GET /api/v1/rooms`

Returns a list of all rooms currently registered in the system.

**Response:** `200 OK`

```json
[
  {
    "id": "LAB-101",
    "name": "Computer Lab",
    "capacity": 50,
    "sensorIds": ["TEMP-001"]
  }
]
```

---

#### `POST /api/v1/rooms`

Registers a new room.

**Request body:**
```json
{
  "id": "LAB-101",
  "name": "Computer Lab",
  "capacity": 50
}
```

**Response:** `201 Created` + `Location` header pointing to the new room's URL

```json
{
  "id": "LAB-101",
  "name": "Computer Lab",
  "capacity": 50,
  "sensorIds": []
}
```

**Validation rules:**
- `id` is required — returns `400 Bad Request` if missing
- `id` must be unique — returns `409 Conflict` if a room with that ID already exists

---

#### `GET /api/v1/rooms/{roomId}`

Retrieves a specific room by its ID.

**Response:** `200 OK` — the room object
**Error:** `404 Not Found` if the room does not exist

```json
{
  "status": "404 Not Found",
  "error": "Room Not Found",
  "message": "No room found with ID: XYZ-999"
}
```

---

#### `DELETE /api/v1/rooms/{roomId}`

Deletes a room. The room must have no sensors linked to it.

**Response:** `204 No Content` (empty body — success)

**Business rule enforced:** If the room still has sensors assigned, deletion is **blocked**:
- Returns `409 Conflict` via `RoomNotEmptyException`

**Other errors:**
- `404 Not Found` — room does not exist

---

### Sensors

#### `GET /api/v1/sensors`

Returns all sensors. Optionally filter by sensor type using a query parameter.

**Optional query parameter:** `?type=<value>` — case-insensitive match on sensor type

Examples:
- `GET /api/v1/sensors` — returns all sensors
- `GET /api/v1/sensors?type=CO2` — returns only CO2 sensors
- `GET /api/v1/sensors?type=temperature` — case-insensitive, returns TEMPERATURE sensors

**Response:** `200 OK` — array of sensor objects

---

#### `POST /api/v1/sensors`

Registers a new sensor and links it to an existing room.

**Request body:**
```json
{
  "id": "TEMP-001",
  "type": "TEMPERATURE",
  "status": "ACTIVE",
  "roomId": "LAB-101"
}
```

**Response:** `201 Created` + `Location` header pointing to the new sensor's URL

**What happens internally:**
1. Validates the sensor ID is provided and unique
2. Validates that `roomId` refers to an existing room — throws `LinkedResourceNotFoundException` if not
3. Defaults `status` to `"ACTIVE"` if not provided
4. Adds the sensor to the DataStore and initialises an empty readings list
5. Adds the sensor's ID to the room's `sensorIds` list (bidirectional link)

**Validation rules:**
- `id` is required → `400 Bad Request`
- `id` must be unique → `409 Conflict`
- `roomId` must reference an existing room → `422 Unprocessable Entity`

---

#### `GET /api/v1/sensors/{sensorId}`

Retrieves a specific sensor by its ID.

**Response:** `200 OK` — the sensor object
**Error:** `404 Not Found` if the sensor does not exist

---

### Sensor Readings

Readings are a **sub-resource** of sensors, accessed via the nested path `/sensors/{sensorId}/readings`. This is implemented using a JAX-RS **Sub-Resource Locator** in `SensorResource`.

#### `GET /api/v1/sensors/{sensorId}/readings`

Returns the full reading history for a specific sensor.

**Response:** `200 OK` — array of reading objects (empty array `[]` if no readings yet)
**Error:** `404 Not Found` if the sensor does not exist

```json
[
  {
    "id": "a1b2c3d4-...",
    "timestamp": 1713600000000,
    "value": 22.5
  }
]
```

---

#### `POST /api/v1/sensors/{sensorId}/readings`

Records a new reading for a sensor.

**Request body** (only `value` is required):
```json
{
  "value": 25.7
}
```

**Response:** `201 Created` + `Location` header pointing to the new reading's URL

**What happens internally:**
1. Verifies the sensor exists → `404` if not
2. Checks sensor status — **blocks reading if `MAINTENANCE`** → `403 Forbidden`
3. Auto-generates a UUID for `id` and sets `timestamp` to current server time
4. Appends the reading to the sensor's reading history
5. Updates the sensor's `currentValue` with the new reading value

---

## Error Handling

The API never exposes raw Java stack traces. Every error returns a structured JSON body.

### Custom Exception Mappers

| Exception Class | HTTP Status | When It's Thrown |
| --- | --- | --- |
| `RoomNotEmptyException` | `409 Conflict` | Attempting to delete a room that still has sensors assigned |
| `LinkedResourceNotFoundException` | `422 Unprocessable Entity` | Creating a sensor with a `roomId` that does not exist |
| `SensorUnavailableException` | `403 Forbidden` | Posting a reading to a sensor with `MAINTENANCE` status |
| `Throwable` (catch-all) | `500 Internal Server Error` | Any unexpected runtime error |

### Error Response Format

All error responses follow this consistent JSON structure:

```json
{
  "status": "409 Conflict",
  "error": "Room Not Empty",
  "message": "Cannot delete room 'LAB-101': it still has 2 sensor(s) attached."
}
```

### How Exception Mappers Work

Jersey automatically scans for classes annotated with `@Provider` that implement `ExceptionMapper<T>`. When a resource method throws an exception, Jersey finds the matching mapper and calls its `toResponse()` method. Each exception has its own individual mapper class.

Example flow for a maintenance sensor:

```text
POST /sensors/TEMP-001/readings
        ↓
SensorReadingResource.addReading()
        ↓  sensor.status == "MAINTENANCE"
throw new SensorUnavailableException("Sensor is under MAINTENANCE...")
        ↓  Jersey intercepts it
SensorUnavailableMapper.toResponse(exception)
        ↓
HTTP 403 { "status": "403 Forbidden", "error": "Sensor Unavailable", "message": "..." }
```

### Request/Response Logging

Every HTTP request and response is logged by `RequestResponseLoggingFilter`, which implements both `ContainerRequestFilter` and `ContainerResponseFilter`. Logs appear in the Tomcat console:

```
>>> Incoming Request : [POST] http://localhost:8080/smart-campus-api/api/v1/rooms
<<< Outgoing Response: [POST] http://localhost:8080/smart-campus-api/api/v1/rooms -> HTTP 201
```

---

## Project Structure

```text
smart-campus-api/
├── pom.xml                              ← Maven build config, WAR packaging, all dependencies
└── src/main/
    ├── java/com/smartcampus/
    │   ├── SmartCampusApplication.java  ← JAX-RS Application class (@ApplicationPath "/api/v1")
    │   │
    │   ├── model/                       ← Plain Java data objects (POJOs)
    │   │   ├── Room.java
    │   │   ├── Sensor.java
    │   │   └── SensorReading.java
    │   │
    │   ├── storage/
    │   │   └── DataStore.java           ← Singleton in-memory store using HashMap
    │   │
    │   ├── resource/                    ← REST endpoint handlers (JAX-RS resource classes)
    │   │   ├── DiscoveryResource.java   ← GET /api/v1 — API metadata and HATEOAS links
    │   │   ├── RoomResource.java        ← GET/POST /rooms, GET/DELETE /rooms/{id}
    │   │   ├── SensorResource.java      ← GET/POST /sensors, GET /sensors/{id}, sub-resource locator
    │   │   └── SensorReadingResource.java ← GET/POST /sensors/{id}/readings
    │   │
    │   ├── exception/                   ← Custom exception classes
    │   │   ├── RoomNotEmptyException.java
    │   │   ├── LinkedResourceNotFoundException.java
    │   │   ├── SensorUnavailableException.java
    │   │   └── mapper/                  ← Individual ExceptionMapper per exception
    │   │       ├── RoomNotEmptyMapper.java           ← HTTP 409
    │   │       ├── LinkedResourceNotFoundMapper.java ← HTTP 422
    │   │       ├── SensorUnavailableMapper.java      ← HTTP 403
    │   │       └── GenericExceptionMapper.java       ← HTTP 500 (catch-all)
    │   │
    │   └── filter/
    │       └── RequestResponseLoggingFilter.java     ← Logs all requests and responses
    │
    └── webapp/WEB-INF/
        └── web.xml                      ← Tomcat servlet configuration
```

---

## How to Build and Run

### Prerequisites
- Java JDK 11 or higher
- Apache Maven 3.6+
- Apache Tomcat 9.x
- Postman or curl (for testing)

### Step 1 — Clone the repository
```bash
git clone https://github.com/Bawaperera/smart-campus-api.git
```

### Step 2 — Build the WAR file
```bash
cd smart-campus-api
mvn clean package
```
Look for **BUILD SUCCESS**. The WAR file is generated at `target/smart-campus-api.war`.

### Step 3 — Deploy to Tomcat
Copy the WAR file into Tomcat's webapps folder:

```bash
# Windows
copy target\smart-campus-api.war C:\apache-tomcat-9\webapps\

# Linux / Mac
cp target/smart-campus-api.war /opt/tomcat/webapps/
```

### Step 4 — Start Tomcat
```bash
# Windows
C:\apache-tomcat-9\bin\startup.bat

# Linux / Mac
/opt/tomcat/bin/startup.sh
```

Wait for Tomcat to log:
```
INFO: Server startup in [xxxx] milliseconds
```

### Step 5 — The API is live at:
```
http://localhost:8080/smart-campus-api/api/v1
```

### Step 6 — Stop Tomcat
```bash
# Windows
C:\apache-tomcat-9\bin\shutdown.bat

# Linux / Mac
/opt/tomcat/bin/shutdown.sh
```

> **Redeployment tip:** When redeploying, always delete both the `.war` file and the extracted folder from `webapps/` before copying the new WAR, to ensure Tomcat uses the latest build.

---

## Testing with curl

All commands below assume the server is running on `http://localhost:8080`.

> **Important:** The data store is empty on startup. Create a room first, then sensors, then readings.

### Discovery
```bash
curl -X GET http://localhost:8080/smart-campus-api/api/v1/
```

### Rooms
```bash
# List all rooms
curl -X GET http://localhost:8080/smart-campus-api/api/v1/rooms

# Create a new room
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"LAB-101\",\"name\":\"Computer Lab\",\"capacity\":50}"

# Get a specific room
curl -X GET http://localhost:8080/smart-campus-api/api/v1/rooms/LAB-101

# Delete a room (only works if it has no sensors) → 204 No Content
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LAB-101

# Attempt to delete a room that has sensors → 409 Conflict
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LAB-101
```

### Sensors
```bash
# List all sensors
curl -X GET http://localhost:8080/smart-campus-api/api/v1/sensors

# Filter by type (case-insensitive)
curl -X GET "http://localhost:8080/smart-campus-api/api/v1/sensors?type=TEMPERATURE"
curl -X GET "http://localhost:8080/smart-campus-api/api/v1/sensors?type=temperature"

# Get a specific sensor
curl -X GET http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001

# Register a sensor in an existing room → 201 Created
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"TEMP-001\",\"type\":\"TEMPERATURE\",\"status\":\"ACTIVE\",\"roomId\":\"LAB-101\"}"

# Register sensor in a non-existent room → 422 Unprocessable Entity
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"BAD-001\",\"type\":\"MOTION\",\"roomId\":\"FAKE-999\"}"
```

### Sensor Readings
```bash
# Get all readings (empty list initially)
curl -X GET http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings

# Post a new reading → 201 Created, auto-generates id and timestamp
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d "{\"value\":23.5}"

# Get readings again — confirms the reading is stored
curl -X GET http://localhost:8080/smart-campus-api/api/v1/sensors/TEMP-001/readings
```

### Triggering Error Responses
```bash
# 409 — Delete a room with sensors attached
curl -X DELETE http://localhost:8080/smart-campus-api/api/v1/rooms/LAB-101

# 422 — Sensor references a room that does not exist
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"TEST-001\",\"type\":\"CO2\",\"roomId\":\"GHOST-000\"}"

# 403 — Post a reading to a MAINTENANCE sensor
curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"MAINT-001\",\"type\":\"CO2\",\"status\":\"MAINTENANCE\",\"roomId\":\"LAB-101\"}"

curl -X POST http://localhost:8080/smart-campus-api/api/v1/sensors/MAINT-001/readings \
  -H "Content-Type: application/json" \
  -d "{\"value\":99.9}"

# 500 — Trigger unexpected server error (no stack trace exposed)
curl -X POST http://localhost:8080/smart-campus-api/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d "null"
```

---

## Important Notes

- **In-memory storage only** — all data is lost when Tomcat stops. There is no database or file persistence.
- **No authentication** — this API is designed for academic/coursework purposes and has no security layer.
- **Auto-linking** — when a sensor is created, it is automatically added to the room's `sensorIds` list (bidirectional link maintained in-memory).
- **currentValue auto-update** — every time a new reading is posted to a sensor, the sensor's `currentValue` field is updated to the latest measurement.
- **Location headers** — all successful POST responses include a `Location` header pointing to the URL of the newly created resource.

---

## Module Information

- **Module:** 5COSC022W Client-Server Architectures
- **University:** University of Westminster / Informatics Institute of Technology (IIT)
- **Academic Year:** 2025/26

---

## Conceptual Report — Question Answers

### Part 1.1 — JAX-RS Resource Lifecycle

By default, JAX-RS creates a **new instance** of every resource class for each incoming HTTP request (per-request scope). Jersey instantiates a fresh `RoomResource` object, processes the request, and then discards it.

Since each resource instance is temporary, any data stored as an instance variable would be lost between calls. To maintain state across requests, this project uses the **Singleton design pattern** in the `DataStore` class. A single shared instance is created once at startup and accessed by every resource via `DataStore.getInstance()`. All data — rooms, sensors, and readings — lives inside this singleton.

For thread safety, access to the singleton is protected with `synchronized` on `getInstance()`. Since multiple requests can arrive simultaneously on different threads, unprotected concurrent writes could cause race conditions and data corruption. Using `synchronized` ensures only one thread creates the instance, preventing duplication.

---

### Part 1.2 — HATEOAS and Hypermedia

HATEOAS (Hypermedia as the Engine of Application State) is a REST constraint where API responses include links that guide clients to related resources and actions, rather than requiring clients to memorise every URL.

In this project, the discovery endpoint (`GET /api/v1`) returns links to available resource collections:

```json
{
  "links": {
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```

**Benefits over static documentation:**

- **Discoverability** — Clients can explore the API programmatically without reading external documentation.
- **Reduced coupling** — If URL paths change, clients following embedded links adapt automatically, whereas hardcoded URLs break.
- **Self-describing responses** — The API communicates what actions are possible from the current state.
- **Easier onboarding** — New developers can navigate the API interactively, discovering resources and relationships without separate reference documents.

---

### Part 2.1 — Returning IDs Only vs Full Room Objects

**Returning only IDs:**
- Produces much smaller response payloads — beneficial for large datasets and mobile clients.
- However, clients must make separate `GET /rooms/{id}` requests for every room, creating the **N+1 problem** — 100 rooms would need 100 extra HTTP round trips, dramatically increasing latency.

**Returning full objects (this implementation):**
- A single request returns everything needed in one response, eliminating follow-up requests.
- Increases payload size slightly, but manageable for campus-scale datasets.
- Better user experience when clients need all fields to render their interface.

For this project, full room objects are returned because clients managing campus rooms need all fields for meaningful operations, and the dataset size makes the larger payload acceptable.

---

### Part 2.2 — Idempotency of DELETE

DELETE is **idempotent** in this implementation, consistent with the HTTP specification. Idempotency means sending the same request multiple times produces the same server state as sending it once.

- First `DELETE /rooms/LAB-101` — room exists, has no sensors, is removed → `204 No Content`
- Second `DELETE /rooms/LAB-101` — room no longer exists → `404 Not Found`

The server state after both calls is identical: the room does not exist. The response code differs, but HTTP standards only require identical server **state**, not identical response codes. The resource is gone either way.

---

### Part 3.1 — @Consumes and Content-Type Mismatch

The `@Consumes(MediaType.APPLICATION_JSON)` annotation tells Jersey that POST endpoints accept only request bodies with `Content-Type: application/json`.

If a client sends a different content type (e.g., `text/plain`, `application/xml`), Jersey automatically returns **HTTP 415 Unsupported Media Type** before the resource method is ever invoked. This boundary prevents unexpected data formats from reaching business logic, guaranteeing the resource method always receives a properly deserialised Java object.

- `Content-Type: text/plain` → `415 Unsupported Media Type`
- `Content-Type: application/json` with malformed JSON → passes the content-type check, but Jackson fails to deserialise → `400 Bad Request`

---

### Part 3.2 — @QueryParam vs Path-Based Filtering

**Query parameter approach (`GET /api/v1/sensors?type=CO2`):**
- Filtering is **optional** — `GET /api/v1/sensors` returns all; adding `?type=CO2` narrows results. One endpoint serves both cases.
- Semantically correct — `/api/v1/sensors` identifies the collection; the query string expresses *filtering criteria*, not resource identity.
- Multiple filters combine naturally: `?type=CO2&status=ACTIVE`.

**Path-based approach (`GET /api/v1/sensors/type/CO2`):**
- Creates new routes for every filter combination, leading to route explosion.
- Semantically misleading — implies `type` and `CO2` are resource identifiers, not filter criteria.
- Conflicts with existing routes — `/sensors/{sensorId}` already uses path parameters, creating ambiguity.

Query parameters are the standard REST convention for filtering because they are optional, composable, and semantically distinct from resource identification.

---

### Part 4.1 — Sub-Resource Locator Pattern

The Sub-Resource Locator pattern allows a parent resource class to delegate nested path handling to a dedicated child class. In `SensorResource`:

```java
@Path("{sensorId}/readings")
public SensorReadingResource getReadingsResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

This method has **no HTTP method annotation** — it is purely a locator. Jersey calls it, receives the `SensorReadingResource` instance, then dispatches the actual `@GET` or `@POST` to that class.

**Benefits:**
- **Single Responsibility** — `SensorResource` handles sensor CRUD; `SensorReadingResource` handles reading history. Neither knows the other's internals.
- **Reduced complexity** — Keeps individual classes small and readable instead of one giant controller.
- **Independent testing** — `SensorReadingResource` can be unit tested in isolation by instantiating it directly.
- **Scalability** — Adding further nesting is trivial without modifying existing classes.

---

### Part 5.1 — HTTP 422 vs 404 for Missing roomId

- **HTTP 404 Not Found** means the requested **URL** does not exist — it signals a routing problem.
- **HTTP 422 Unprocessable Entity** means the server understood the request format (valid JSON, correct `Content-Type`), but the **semantic content is invalid** — specifically, it references something nonexistent.

When a client sends `POST /api/v1/sensors` with `"roomId": "GHOST-000"`, the URL `/api/v1/sensors` is valid and found. The problem is not the endpoint — it is the data inside the request body.

Using `404` would mislead clients into thinking the sensors endpoint itself is missing. Using `422` precisely communicates: "Your request is well-formed and the endpoint exists, but the `roomId` value does not reference a real room." Clients know exactly what to fix.

---

### Part 5.2 — Security Risks of Exposing Stack Traces

Exposing raw Java stack traces is a critical security vulnerability (OWASP A05: Security Misconfiguration — Information Disclosure). Stack traces reveal:

1. **Technology fingerprinting** — Package names expose exact frameworks and versions (e.g., `org.glassfish.jersey 2.41`). Attackers search known CVEs for those versions.
2. **Internal project structure** — Package paths like `com.smartcampus.storage.DataStore` reveal codebase organisation and data access patterns.
3. **Business logic details** — Method names in traces reveal internal logic flow, helping attackers identify exploitable code paths.
4. **Error conditions** — The specific exception and line number reveals exactly what input caused the crash, enabling attackers to reproduce unhandled states deliberately.
5. **Server file paths** — Some traces include absolute paths, revealing the server's directory structure.

**Mitigation in this project:** The `GenericExceptionMapper` catches all `Throwable` exceptions, logs the full details **server-side only** (visible to developers in Tomcat logs), and returns a clean generic JSON response to the client — no internal details exposed.
