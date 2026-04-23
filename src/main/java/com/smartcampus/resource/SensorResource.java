package com.smartcampus.resource;

import com.smartcampus.exception.LinkedResourceNotFoundException;
import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.storage.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

@Path("/sensors")
public class SensorResource {

    private final DataStore dataStore = DataStore.getInstance();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSensors(@QueryParam("type") String type) {
        Collection<Sensor> allSensors = dataStore.getSensors().values();

        if (type != null && !type.isBlank()) {
            List<Sensor> filtered = allSensors.stream()
                    .filter(s -> s.getType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
            return Response.ok(filtered).build();
        }

        return Response.ok(allSensors).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSensor(Sensor sensor) {
        if (sensor.getId() == null || sensor.getId().isBlank()) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "400 Bad Request");
            error.put("error", "Missing Sensor ID");
            error.put("message", "Sensor ID is required and cannot be blank.");
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
        if (dataStore.getSensors().containsKey(sensor.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "409 Conflict");
            error.put("error", "Duplicate Sensor ID");
            error.put("message", "A sensor with ID '" + sensor.getId() + "' already exists.");
            return Response.status(Response.Status.CONFLICT).entity(error).build();
        }

        // Validate that the referenced room exists
        if (sensor.getRoomId() != null && !sensor.getRoomId().isBlank()) {
            Room room = dataStore.getRooms().get(sensor.getRoomId());
            if (room == null) {
                throw new LinkedResourceNotFoundException(
                    "Cannot register sensor: room with ID '" + sensor.getRoomId() + "' does not exist."
                );
            }
            room.getSensorIds().add(sensor.getId());
        }

        if (sensor.getStatus() == null || sensor.getStatus().isBlank()) {
            sensor.setStatus("ACTIVE");
        }

        dataStore.getSensors().put(sensor.getId(), sensor);
        dataStore.getReadings().put(sensor.getId(), new ArrayList<>());

        return Response.status(Response.Status.CREATED).entity(sensor).build();
    }

    @GET
    @Path("{sensorId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSensorById(@PathParam("sensorId") String sensorId) {
        Sensor sensor = dataStore.getSensors().get(sensorId);
        if (sensor == null) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "404 Not Found");
            error.put("error", "Sensor Not Found");
            error.put("message", "No sensor found with ID: " + sensorId);
            return Response.status(Response.Status.NOT_FOUND).entity(error).build();
        }
        return Response.ok(sensor).build();
    }

    // Sub-Resource Locator — delegates /sensors/{sensorId}/readings to SensorReadingResource
    // Note: NO @GET or @POST here — this is a locator, not an endpoint
    @Path("{sensorId}/readings")
    public SensorReadingResource getReadingsResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }
}
