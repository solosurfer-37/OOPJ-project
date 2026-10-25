public class TextBook extends Book {

    private final String subject;

    public TextBook(String name, User owner, String author, String subject) {
        super(name, "Textbook", owner, author);
        this.subject = subject;
    }

    public TextBook(int itemId, String name, User owner, Item.Status status, String author, String subject) {
        super(itemId, name, "Textbook", owner, status, author);
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }

    @Override
    public String getDetails() {
        return super.getDetails() + " (" + subject + ")";
    }

    @Override
    public String getTypeCode() {
        return "TEXTBOOK";
    }

    @Override
    public String[] getExtraFields() {
        return new String[] { getAuthor(), subject };
    }
}
