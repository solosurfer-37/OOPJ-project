import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;

// Abstract kyunki "Loan" akela koi real cheez nahi hai - ya toh BookLoan hoga ya EquipmentLoan
public abstract class Loan {

    public enum Status { ACTIVE, RETURNED, LOST, DAMAGED }

    // uuuu + STRICT isliye ki 31-02-2025 jaisi galat date accept na ho
    public static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

    private static int nextId = 1;

    private final int loanId;
    private final User borrower;
    private final Item item;
    private final LocalDate issueDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;      // null jab tak return nahi hua
    private Status status;
    private String problemReason;      // sirf lost/damaged ke liye

    // Naya loan: due date = issue date + days
    protected Loan(User borrower, Item item, LocalDate issueDate, int days) {
        this.loanId = nextId++;
        this.borrower = borrower;
        this.item = item;
        this.issueDate = issueDate;
        this.dueDate = issueDate.plusDays(days);
        this.status = Status.ACTIVE;
        this.problemReason = "";
    }

    // File se purana loan load karne ke liye
    protected Loan(int loanId, User borrower, Item item, LocalDate issueDate, LocalDate dueDate,
                   LocalDate returnDate, Status status, String problemReason) {
        this.loanId = loanId;
        this.borrower = borrower;
        this.item = item;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.status = status;
        this.problemReason = problemReason;
        if (loanId >= nextId) {
            nextId = loanId + 1;
        }
    }

    // Har loan type apna rule/naam khud batata hai
    public abstract int getMaxLoanDays();

    public abstract String getLoanType();

    public int getLoanId() {
        return loanId;
    }

    public User getBorrower() {
        return borrower;
    }

    public Item getItem() {
        return item;
    }

    public User getOwner() {
        return item.getOwner();
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public Status getStatus() {
        return status;
    }

    public String getProblemReason() {
        return problemReason;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    // Overdue hamesha date se calculate hota hai, kabhi manually mark nahi hota
    public boolean isOverdue(LocalDate today) {
        return status == Status.ACTIVE && today.isAfter(dueDate);
    }

    public boolean isOverdue() {
        return isOverdue(LocalDate.now());
    }

    public long getDaysOverdue(LocalDate today) {
        if (!isOverdue(today)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, today);
    }

    public boolean wasReturnedLate() {
        return status == Status.RETURNED && returnDate != null && returnDate.isAfter(dueDate);
    }

    public void markReturned(LocalDate date) throws InvalidLoanException {
        if (status != Status.ACTIVE) {
            throw new InvalidLoanException("Loan " + loanId + " is already closed (" + status
                    + "), so it cannot be returned again.");
        }
        if (date.isBefore(issueDate)) {
            throw new InvalidLoanException("Return date cannot be before the issue date ("
                    + formatDate(issueDate) + ").");
        }
        this.returnDate = date;
        this.status = Status.RETURNED;
    }

    // Overloaded version: aaj ki date par return
    public void markReturned() throws InvalidLoanException {
        markReturned(LocalDate.now());
    }

    public void markLost(String reason) throws InvalidLoanException {
        closeWithProblem(Status.LOST, reason);
    }

    public void markDamaged(String reason) throws InvalidLoanException {
        closeWithProblem(Status.DAMAGED, reason);
    }

    private void closeWithProblem(Status newStatus, String reason) throws InvalidLoanException {
        if (status != Status.ACTIVE) {
            throw new InvalidLoanException("Loan " + loanId + " is already closed (" + status + ").");
        }
        this.status = newStatus;
        this.problemReason = reason;
    }

    public String getStatusText() {
        LocalDate today = LocalDate.now();
        if (isOverdue(today)) {
            return "OVERDUE by " + getDaysOverdue(today) + " day(s)";
        }
        if (status == Status.ACTIVE) {
            return "Active";
        }
        if (status == Status.RETURNED) {
            if (wasReturnedLate()) {
                return "Returned late on " + formatDate(returnDate);
            }
            return "Returned on time (" + formatDate(returnDate) + ")";
        }
        return status + " - " + problemReason;
    }

    // Ek line ka summary (history mein use hota hai)
    public String getSummary() {
        return "Loan #" + loanId + " [" + getLoanType() + "] " + item.getName()
                + " | borrower: " + borrower.getName()
                + " | owner: " + getOwner().getName()
                + " | " + formatDate(issueDate) + " to " + formatDate(dueDate)
                + " | " + getStatusText();
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "-";
        }
        return date.format(DATE_FORMAT);
    }

    // Galat date par DateTimeParseException aayega, caller usse handle karega
    public static LocalDate parseDate(String text) {
        return LocalDate.parse(text.trim(), DATE_FORMAT);
    }
}
