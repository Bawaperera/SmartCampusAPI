package com.smartcampus.resource;

import com.smartcampus.exception.RoomNotEmptyException;
import com.smartcampus.model.Room;
import com.smartcampus.storage.DataStore;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Path("/rooms")
public class RoomResource {

    private final DataStore dataStore = DataStore.getInstance();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRooms() {
        Collection<Room> rooms = dataStore.getRooms().values();
        return Response.ok(rooms).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createRoom(Room room) {
        if (room.getId() == null || room.getId().isBlank()) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "400 Bad Request");
            error.put("error", "Missing Room ID");
            error.put("message", "Room ID is required and cannot be blank.");
            return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
        }
        if (dataStore.getRooms().containsKey(room.getId())) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "409 Conflict");
            error.put("error", "Duplicate Room ID");
            error.put("message", "A room with ID '" + room.getId() + "' already exists.");
            return Response.status(Response.Status.CONFLICT).entity(error).build();
        }
        dataStore.getRooms().put(room.getId(), room);
        return Response.status(Response.Status.CREATED).entity(room).build();
    }

    @GET
    @Path("{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoomById(@PathParam("roomId") String roomId) {
        Room room = dataStore.getRooms().get(roomId);
        if (room == null) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "404 Not Found");
            error.put("error", "Room Not Found");
            error.put("message", "No room found with ID: " + roomId);
            return Response.status(Response.Status.NOT_FOUND).entity(error).build();
        }
        return Response.ok(room).build();
    }

    @DELETE
    @Path("{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = dataStore.getRooms().get(roomId);
        if (room == null) {
            Map<String, String> error = new HashMap<>();
            error.put("status", "404 Not Found");
            error.put("error", "Room Not Found");
            error.put("message", "No room found with ID: " + roomId);
            return Response.status(Response.Status.NOT_FOUND).entity(error).build();
        }
        if (!room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException(
                "Cannot delete room '" + roomId + "': it still has " +
                room.getSensorIds().size() + " sensor(s) attached. Remove all sensors first."
            );
        }
        dataStore.getRooms().remove(roomId);
        return Response.noContent().build();
    }
}
