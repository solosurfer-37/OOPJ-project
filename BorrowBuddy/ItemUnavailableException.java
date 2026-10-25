// InvalidLoanException ka special case: item mojood hai par abhi borrow nahi ho sakta.
public class ItemUnavailableException extends InvalidLoanException {

    private final Item item;

    public ItemUnavailableException(Item item, String reason) {
        super("Item '" + item.getName() + "' (ID " + item.getItemId() + ") is not available: " + reason);
        this.item = item;
    }

    public Item getItem() {
        return item;
    }
}
