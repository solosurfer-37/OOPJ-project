import java.time.LocalDate;

public class BorrowRequest {

    public enum Status { PENDING, APPROVED, REJECTED }

    private static int nextId = 1;

    private final int requestId;
    private final User borrower;
    private final Item item;
    private final LocalDate requestDate;
    private final int requestedDays;
    private Status status;

    public BorrowRequest(User borrower, Item item, int requestedDays) {
        this.requestId = nextId++;
        this.borrower = borrower;
        this.item = item;
        this.requestDate = LocalDate.now();
        this.requestedDays = requestedDays;
        this.status = Status.PENDING;
    }

    public BorrowRequest(int requestId, User borrower, Item item, LocalDate requestDate,
                         int requestedDays, Status status) {
        this.requestId = requestId;
        this.borrower = borrower;
        this.item = item;
        this.requestDate = requestDate;
        this.requestedDays = requestedDays;
        this.status = status;
        if (requestId >= nextId) {
            nextId = requestId + 1;
        }
    }

    public int getRequestId() {
        return requestId;
    }

    public User getBorrower() {
        return borrower;
    }

    public Item getItem() {
        return item;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public int getRequestedDays() {
        return requestedDays;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }
}
