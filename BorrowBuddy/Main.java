import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Main {

    private static final Scanner scanner = new Scanner(System.in);
    private static final LendingManager manager = new LendingManager(new FileStorage("data"));

    // Menu ko array se print karte hain, loop se
    private static final String[] MENU_OPTIONS = {
        "Register a new user",
        "Add an item for lending",
        "View all available items",
        "Send a borrowing request",
        "View pending borrowing requests",
        "Approve or reject a borrowing request",
        "View active loans and due dates",
        "Return a borrowed item",
        "Report an item as lost or damaged",
        "View borrowing history",
        "View a user's reliability score",
        "View overdue loans",
        "Save data and exit"
    };

    private static WebServer webServer;

    public static void main(String[] args) {
        System.out.println("BorrowBuddy - Shared Item Lending & Trust Tracker");
        if (!loadSavedData()) {
            return;     // load fail hua toh exit, warna khaali data se purani files overwrite ho jaati
        }
        manager.startBackgroundThreads();

        try {
            java.nio.file.Path staticDir = java.nio.file.Paths.get("..").toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(staticDir.resolve("index.html"))) {
                staticDir = java.nio.file.Paths.get(".").toAbsolutePath().normalize();
            }
            webServer = new WebServer(8080, manager, staticDir);
            webServer.start();
        } catch (Exception e) {
            System.out.println("Note: Web server could not start on port 8080: " + e.getMessage());
        }

        try {
            runMenu();
        } catch (NoSuchElementException e) {
            System.out.println("\nInput ended unexpectedly. Saving data and exiting...");
        } finally {
            closeApplication();
        }
    }

    private static boolean loadSavedData() {
        try {
            ArrayList<String> warnings = manager.loadData();
            for (String warning : warnings) {
                System.out.println("Warning: " + warning);
            }
            System.out.println("Data loaded: " + manager.getDataSummary() + ".");
            return true;
        } catch (IOException e) {
            System.out.println("Could not read the saved data: " + e.getMessage());
            System.out.println("Exiting without changes so your existing records are not overwritten.");
            return false;
        }
    }

    private static void closeApplication() {
        if (webServer != null) {
            webServer.stop();
        }
        manager.shutdown();
        try {
            manager.saveAll();
            System.out.println("Data saved. Goodbye!");
        } catch (IOException e) {
            System.out.println("Error: data could not be saved - " + e.getMessage());
        }
    }

    private static void runMenu() {
        boolean keepRunning = true;
        while (keepRunning) {
            printMenu();
            int choice = readInt("Enter your choice (1-" + MENU_OPTIONS.length + "): ");
            try {
                switch (choice) {
                    case 1: registerUser(); break;
                    case 2: addItem(); break;
                    case 3: viewAvailableItems(); break;
                    case 4: sendRequest(); break;
                    case 5: viewPendingRequests(); break;
                    case 6: handleRequest(); break;
                    case 7: viewActiveLoans(); break;
                    case 8: returnItem(); break;
                    case 9: reportProblem(); break;
                    case 10: viewHistory(); break;
                    case 11: viewScore(); break;
                    case 12: viewOverdueLoans(); break;
                    case 13: keepRunning = false; break;
                    default: System.out.println("Invalid choice. Please enter a number from 1 to 13.");
                }
            } catch (InvalidLoanException e) {
                System.out.println("\nCould not complete the action: " + e.getMessage());
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n==============================================");
        System.out.println("  BorrowBuddy        Today: " + Loan.formatDate(LocalDate.now()));
        System.out.println("==============================================");
        for (int i = 0; i < MENU_OPTIONS.length; i++) {
            System.out.printf(" %2d. %s%n", i + 1, MENU_OPTIONS[i]);
        }
        System.out.println("----------------------------------------------");
    }

    // ------------------------------------------------------------ menu actions

    private static void registerUser() throws InvalidLoanException {
        String name = readText("Full name: ");
        String room = readText("Hostel room / block (optional): ");
        User user = manager.registerUser(name, room);
        System.out.println("Registered " + user.getName() + ". Your user ID is " + user.getUserId()
                + " - remember it, you need it for every action.");
    }

    private static void addItem() throws InvalidLoanException {
        User owner = manager.findUser(readInt("Owner user ID: "));

        System.out.println("Item categories:");
        for (int i = 0; i < Item.CATEGORIES.length; i++) {
            System.out.println("  " + (i + 1) + ". " + Item.CATEGORIES[i]);
        }
        int choice = readInt("Choose category: ");
        if (choice < 1 || choice > Item.CATEGORIES.length) {
            throw new InvalidLoanException("Category number must be between 1 and " + Item.CATEGORIES.length + ".");
        }
        String category = Item.CATEGORIES[choice - 1];
        String name = readText("Item name: ");
        if (name.isEmpty()) {
            throw new InvalidLoanException("Item name cannot be empty.");
        }

        Item item;
        if (category.equals("Book")) {
            item = new Book(name, owner, readText("Author: "));
        } else if (category.equals("Textbook")) {
            String author = readText("Author: ");
            String subject = readText("Subject: ");
            item = new TextBook(name, owner, author, subject);
        } else {
            item = new Item(name, category, owner);
        }
        manager.addItem(item);
        System.out.println("Item added: " + item + ". It is now available for lending.");
    }

    private static void viewAvailableItems() {
        ArrayList<Item> available = manager.getAvailableItems();
        if (available.isEmpty()) {
            System.out.println("No items are available right now.");
            return;
        }
        System.out.printf("%-5s %-24.24s %-32.32s %-14.14s %s%n", "ID", "Item", "Details", "Owner", "Max days");
        for (Item item : available) {
            // item.getDetails() Book/TextBook mein alag chalega - runtime polymorphism
            System.out.printf("%-5d %-24.24s %-32.32s %-14.14s %d%n", item.getItemId(), item.getName(),
                    item.getDetails(), item.getOwner().getName(), manager.getMaxDaysFor(item));
        }
    }

    private static void sendRequest() throws InvalidLoanException {
        int userId = readInt("Your user ID (borrower): ");
        manager.findUser(userId);
        int itemId = readInt("Item ID you want to borrow: ");
        Item item = manager.findItem(itemId);
        int days = readInt("For how many days? (max " + manager.getMaxDaysFor(item) + "): ");
        BorrowRequest request = manager.sendRequest(userId, itemId, days);
        System.out.println("Request #" + request.getRequestId() + " sent to " + item.getOwner().getName()
                + ". It stays pending until the owner decides.");
    }

    private static void viewPendingRequests() {
        ArrayList<BorrowRequest> pending = manager.getPendingRequests();
        if (pending.isEmpty()) {
            System.out.println("No pending requests.");
            return;
        }
        printRequestTable(pending);
    }

    private static void printRequestTable(ArrayList<BorrowRequest> list) {
        System.out.printf("%-5s %-22.22s %-14.14s %-14.14s %-5s %s%n", "Req", "Item", "Borrower", "Owner", "Days", "Requested on");
        for (BorrowRequest r : list) {
            System.out.printf("%-5d %-22.22s %-14.14s %-14.14s %-5d %s%n", r.getRequestId(), r.getItem().getName(),
                    r.getBorrower().getName(), r.getItem().getOwner().getName(), r.getRequestedDays(),
                    Loan.formatDate(r.getRequestDate()));
        }
    }

    private static void handleRequest() throws InvalidLoanException {
        ArrayList<BorrowRequest> pending = manager.getPendingRequests();
        if (pending.isEmpty()) {
            System.out.println("No pending requests to process.");
            return;
        }
        printRequestTable(pending);
        int requestId = readInt("Request ID to process: ");
        int ownerId = readInt("Your user ID (must be the item owner): ");
        String decision = readText("Approve or Reject? (A/R): ").toUpperCase();

        if (decision.equals("A")) {
            LocalDate handover = readOptionalDate("Handover date dd-MM-yyyy (press Enter for today): ");
            Loan loan;
            if (handover == null) {
                loan = manager.approveRequest(requestId, ownerId);
            } else {
                loan = manager.approveRequest(requestId, ownerId, handover);
            }
            System.out.println("Approved. Loan #" + loan.getLoanId() + " created. Due date: "
                    + Loan.formatDate(loan.getDueDate()));
        } else if (decision.equals("R")) {
            manager.rejectRequest(requestId, ownerId);
            System.out.println("Request #" + requestId + " rejected.");
        } else {
            System.out.println("Please type A to approve or R to reject.");
        }
    }

    private static void viewActiveLoans() {
        ArrayList<Loan> active = manager.getActiveLoans();
        if (active.isEmpty()) {
            System.out.println("There are no active loans.");
            return;
        }
        printLoanTable(active);
    }

    private static void printLoanTable(ArrayList<Loan> list) {
        System.out.printf("%-5s %-20.20s %-12.12s %-12.12s %-10s %-11s %-11s %s%n",
                "Loan", "Item", "Borrower", "Owner", "Type", "Issued", "Due", "Status");
        for (Loan loan : list) {
            System.out.printf("%-5d %-20.20s %-12.12s %-12.12s %-10s %-11s %-11s %s%n",
                    loan.getLoanId(), loan.getItem().getName(), loan.getBorrower().getName(),
                    loan.getOwner().getName(), loan.getLoanType(), Loan.formatDate(loan.getIssueDate()),
                    Loan.formatDate(loan.getDueDate()), loan.getStatusText());
        }
    }

    private static void returnItem() throws InvalidLoanException {
        int loanId = readInt("Loan ID to return: ");
        int userId = readInt("Your user ID (owner or borrower): ");
        LocalDate returnDate = readOptionalDate("Return date dd-MM-yyyy (press Enter for today): ");
        Loan loan;
        if (returnDate == null) {
            loan = manager.returnItem(loanId, userId);
        } else {
            loan = manager.returnItem(loanId, userId, returnDate);
        }
        System.out.println("Item '" + loan.getItem().getName() + "' returned. " + loan.getStatusText() + ".");
    }

    private static void reportProblem() throws InvalidLoanException {
        ArrayList<Loan> active = manager.getActiveLoans();
        if (active.isEmpty()) {
            System.out.println("There are no active loans to report on.");
            return;
        }
        printLoanTable(active);
        int loanId = readInt("Loan ID: ");
        int userId = readInt("Your user ID (owner or borrower): ");
        int type = readInt("Report as 1) Lost  2) Damaged: ");
        if (type != 1 && type != 2) {
            throw new InvalidLoanException("Please choose 1 for lost or 2 for damaged.");
        }
        String reason = readText("Reason / details: ");

        Loan loan;
        if (type == 1) {
            loan = manager.reportLost(loanId, userId, reason);
        } else {
            loan = manager.reportDamaged(loanId, userId, reason);
        }
        System.out.println("Report recorded for '" + loan.getItem().getName() + "' as " + loan.getStatus()
                + ". The item is no longer available, and this affects the borrower's reliability score.");
    }

    private static void viewHistory() throws InvalidLoanException {
        User user = manager.findUser(readInt("User ID: "));
        ArrayList<Loan> borrowed = manager.getLoansOf(user, true);
        ArrayList<Loan> lent = manager.getLoansOf(user, false);

        System.out.println("\nHistory for " + user);
        System.out.println("Items borrowed by " + user.getName() + ":");
        if (borrowed.isEmpty()) {
            System.out.println("  (none)");
        } else {
            System.out.print(manager.buildHistoryText(borrowed, 0));
        }
        System.out.println("Items lent by " + user.getName() + ":");
        if (lent.isEmpty()) {
            System.out.println("  (none)");
        } else {
            System.out.print(manager.buildHistoryText(lent, 0));
        }
    }

    private static void viewScore() throws InvalidLoanException {
        User user = manager.findUser(readInt("User ID: "));
        System.out.println();
        System.out.println(manager.getScoreReport(user));
    }

    private static void viewOverdueLoans() {
        ArrayList<Loan> overdue = manager.getOverdueLoans();
        if (overdue.isEmpty()) {
            System.out.println("No overdue loans. Everything is on schedule.");
            return;
        }
        printLoanTable(overdue);
    }

    // ------------------------------------------------------------ input helpers

    private static String readText(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    // Galat number likhne par dobara poochta hai, crash nahi hota
    private static int readInt(String prompt) {
        while (true) {
            String text = readText(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    // Enter dabane par null milta hai (matlab "aaj ki date")
    private static LocalDate readOptionalDate(String prompt) {
        while (true) {
            String text = readText(prompt);
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Loan.parseDate(text);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Use dd-MM-yyyy, for example 05-08-2025.");
            }
        }
    }
}
