package person.notfresh.readingshare.eventlog;

import java.util.Objects;

public final class EventRecord {

    private final String id;
    private final String topic;
    private final String processTime;
    private final String eventTime;
    private final String deviceId;
    private final String entityId;
    private final EventAction action;
    private final String data;

    public EventRecord(String id,
                       String topic,
                       String processTime,
                       String eventTime,
                       String deviceId,
                       String entityId,
                       EventAction action,
                       String data) {
        this.id = Objects.requireNonNull(id, "id");
        this.topic = Objects.requireNonNull(topic, "topic");
        this.processTime = Objects.requireNonNull(processTime, "processTime");
        this.eventTime = Objects.requireNonNull(eventTime, "eventTime");
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId");
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.action = Objects.requireNonNull(action, "action");
        this.data = data;
    }

    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getProcessTime() { return processTime; }
    public String getEventTime() { return eventTime; }
    public String getDeviceId() { return deviceId; }
    public String getEntityId() { return entityId; }
    public EventAction getAction() { return action; }
    public String getData() { return data; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventRecord)) return false;
        EventRecord that = (EventRecord) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "EventRecord{" +
                "id='" + id + '\'' +
                ", topic='" + topic + '\'' +
                ", processTime='" + processTime + '\'' +
                ", eventTime='" + eventTime + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", entityId='" + entityId + '\'' +
                ", action=" + action +
                ", data='" + data + '\'' +
                '}';
    }
}
