package com.smartcampus.storage;

import com.smartcampus.model.Room;
import com.smartcampus.model.Sensor;
import com.smartcampus.model.SensorReading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataStore {

    private static DataStore instance;

    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, Sensor> sensors = new HashMap<>();
    private final Map<String, List<SensorReading>> readings = new HashMap<>();

    private DataStore() {
        // Pre-load sample rooms
        Room room1 = new Room("LIB-301", "Library Quiet Study", 30);
        Room room2 = new Room("LAB-101", "Computer Lab", 50);
        rooms.put(room1.getId(), room1);
        rooms.put(room2.getId(), room2);

        // Pre-load sample sensors (both linked to LAB-101)
        Sensor sensor1 = new Sensor("TEMP-001", "TEMPERATURE", "ACTIVE", 22.5, "LAB-101");
        Sensor sensor2 = new Sensor("CO2-001", "CO2", "ACTIVE", 400.0, "LAB-101");
        sensors.put(sensor1.getId(), sensor1);
        sensors.put(sensor2.getId(), sensor2);

        // Link sensors to room
        room2.getSensorIds().add("TEMP-001");
        room2.getSensorIds().add("CO2-001");

        // Initialise empty reading lists
        readings.put("TEMP-001", new ArrayList<>());
        readings.put("CO2-001", new ArrayList<>());
    }

    public static synchronized DataStore getInstance() {
        if (instance == null) {
            instance = new DataStore();
        }
        return instance;
    }

    public Map<String, Room> getRooms() { return rooms; }
    public Map<String, Sensor> getSensors() { return sensors; }
    public Map<String, List<SensorReading>> getReadings() { return readings; }
}
