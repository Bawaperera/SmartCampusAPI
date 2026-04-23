package com.smartcampus.resource;

import com.smartcampus.exception.SensorUnavailableException;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;
import com.smartcampus.storage.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;

public class SensorReadingResource {

    private final String sensorId;
    private final DataStore dataStore = DataStore.getInstance();

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReadings() {
        Sensor sensor = dataStore.getSensors().get(sensorId);
        if (sensor == null) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "404 Not Found");
            error.put("error", "Sensor Not Found");
            error.put("message", "No sensor found with ID: " + sensorId);
            return Response.status(Response.Status.NOT_FOUND).entity(error).build();
        }
        List<SensorReading> sensorReadings = dataStore.getReadings()
                .getOrDefault(sensorId, new ArrayList<>());
        return Response.ok(sensorReadings).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading reading) {
        Sensor sensor = dataStore.getSensors().get(sensorId);
        if (sensor == null) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "404 Not Found");
            error.put("error", "Sensor Not Found");
            error.put("message", "No sensor found with ID: " + sensorId);
            return Response.status(Response.Status.NOT_FOUND).entity(error).build();
        }

        // Reject readings for sensors under maintenance
        if ("MAINTENANCE".equalsIgnoreCase(sensor.getStatus())) {
            throw new SensorUnavailableException(
                "Sensor '" + sensorId + "' is currently under MAINTENANCE. No readings can be submitted."
            );
        }

        // Auto-generate ID and timestamp if not provided
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading.setId(UUID.randomUUID().toString());
        }
        if (reading.getTimestamp() == 0) {
            reading.setTimestamp(System.currentTimeMillis());
        }

        // Update parent sensor's currentValue to reflect latest reading
        sensor.setCurrentValue(reading.getValue());

        dataStore.getReadings()
                .computeIfAbsent(sensorId, k -> new ArrayList<>())
                .add(reading);

        return Response.status(Response.Status.CREATED).entity(reading).build();
    }
}
