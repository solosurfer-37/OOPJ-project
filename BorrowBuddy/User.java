public class User {

    // Rules of the system - ek jagah define kiye taaki README ke formula se match rahe
    public static final int MAX_ACTIVE_LOANS = 3;
    public static final int START_SCORE = 100;
    public static final int LATE_PENALTY = 5;
    public static final int LOST_PENALTY = 30;
    public static final int DAMAGED_PENALTY = 15;

    private static int nextId = 1;

    private final int userId;
    private String name;
    private String room;

    // New user (ID automatically milti hai)
    public User(String name, String room) {
        this.userId = nextId++;
        this.name = name;
        this.room = room;
    }

    // Room na diya ho toh default value
    public User(String name) {
        this(name, "Not given");
    }

    // File se load karte time purani ID wapas use hoti hai
    public User(int userId, String name, String room) {
        this.userId = userId;
        this.name = name;
        this.room = room;
        if (userId >= nextId) {
            nextId = userId + 1;
        }
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getRoom() {
        return room;
    }

    // Score sirf in teen counts se banta hai, isliye koi bhi explain kar sakta hai ki score kyu kam hua
    public static int computeScore(int lateReturns, int lostItems, int damagedItems) {
        int score = START_SCORE
                - lateReturns * LATE_PENALTY
                - lostItems * LOST_PENALTY
                - damagedItems * DAMAGED_PENALTY;
        return Math.max(0, Math.min(100, score));
    }

    @Override
    public String toString() {
        return name + " (ID " + userId + ", " + room + ")";
    }
}
