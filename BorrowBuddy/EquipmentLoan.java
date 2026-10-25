import java.time.LocalDate;

// Calculator, charger, sports gear, lab accessory - inko jaldi wapas chahiye hota hai, isliye limit kam
public class EquipmentLoan extends Loan {

    public static final int MAX_DAYS = 7;

    public EquipmentLoan(User borrower, Item item, LocalDate issueDate, int days) {
        super(borrower, item, issueDate, days);
    }

    public EquipmentLoan(int loanId, User borrower, Item item, LocalDate issueDate, LocalDate dueDate,
                         LocalDate returnDate, Loan.Status status, String problemReason) {
        super(loanId, borrower, item, issueDate, dueDate, returnDate, status, problemReason);
    }

    @Override
    public int getMaxLoanDays() {
        return MAX_DAYS;
    }

    @Override
    public String getLoanType() {
        return "Equipment";
    }
}
