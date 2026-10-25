public class Item {

    public enum Status { AVAILABLE, BORROWED, LOST, DAMAGED }

    // Fixed categories - menu mein dikhane ke liye array use kiya hai
    public static final String[] CATEGORIES = {
        "Book", "Textbook", "Calculator", "Charger", "Sports Equipment", "Lab Accessory", "Other"
    };

    private static int nextId = 1;

    private final int itemId;
    private final String name;
    private final String category;
    private final User owner;
    private Status status;

    public Item(String name, String category, User owner) {
        this.itemId = nextId++;
        this.name = name;
        this.category = category;
        this.owner = owner;
        this.status = Status.AVAILABLE;
    }

    // Saved file se item wapas banane ke liye
    public Item(int itemId, String name, String category, User owner, Status status) {
        this.itemId = itemId;
        this.name = name;
        this.category = category;
        this.owner = owner;
        this.status = status;
        if (itemId >= nextId) {
            nextId = itemId + 1;
        }
    }

    public int getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public User getOwner() {
        return owner;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isAvailable() {
        return status == Status.AVAILABLE;
    }

    // Subclasses isko override karke apni extra details dikhate hain
    public String getDetails() {
        return category;
    }

    // File storage ke liye: kaunsa type hai aur extra fields kya hain
    public String getTypeCode() {
        return "ITEM";
    }

    public String[] getExtraFields() {
        return new String[] { "", "" };
    }

    @Override
    public String toString() {
        return name + " (ID " + itemId + ")";
    }
}
