// Item -> Book -> TextBook (multilevel inheritance)
public class Book extends Item {

    private final String author;

    public Book(String name, User owner, String author) {
        this(name, "Book", owner, author);
    }

    // TextBook isi constructor se apni category bhejta hai
    protected Book(String name, String category, User owner, String author) {
        super(name, category, owner);
        this.author = author;
    }

    public Book(int itemId, String name, String category, User owner, Item.Status status, String author) {
        super(itemId, name, category, owner, status);
        this.author = author;
    }

    public String getAuthor() {
        return author;
    }

    @Override
    public String getDetails() {
        return getCategory() + " by " + author;
    }

    @Override
    public String getTypeCode() {
        return "BOOK";
    }

    @Override
    public String[] getExtraFields() {
        return new String[] { author, "" };
    }
}
