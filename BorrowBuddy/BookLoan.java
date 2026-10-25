import java.time.LocalDate;

public class BookLoan extends Loan {

    public static final int MAX_DAYS = 14;

    public BookLoan(User borrower, Item item, LocalDate issueDate, int days) {
        super(borrower, item, issueDate, days);
    }

    public BookLoan(int loanId, User borrower, Item item, LocalDate issueDate, LocalDate dueDate,
                    LocalDate returnDate, Loan.Status status, String problemReason) {
        super(loanId, borrower, item, issueDate, dueDate, returnDate, status, problemReason);
    }

    @Override
    public int getMaxLoanDays() {
        return MAX_DAYS;
    }

    @Override
    public String getLoanType() {
        return "Book";
    }
}
