package person.notfresh.readingshare.eventlog;

public class EventLogException extends RuntimeException {

    public EventLogException(String message) {
        super(message);
    }

    public EventLogException(String message, Throwable cause) {
        super(message, cause);
    }
}
