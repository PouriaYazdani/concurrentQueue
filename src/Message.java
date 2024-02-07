import java.io.Serializable;

public class Message implements Serializable {

    private final String content;
    private final long timestamp;
    private final int priority;

    public Message(String content,int priority){
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.priority = priority;
    }

    public int getPriority() {
        return priority;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString(){
        return getContent() + " with priority " + getPriority();
    }
}
