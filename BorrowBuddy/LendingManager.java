import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

// Poora lending data yahin hai. Background threads bhi isi ko padhte hain, isliye
// jo methods shared lists (users/items/requests/loans) ko padhte ya badalte hain wo synchronized hain.
public class LendingManager {

    private static final long CHECK_INTERVAL_MS = 15000;

    private final ArrayList<User> users = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<BorrowRequest> requests = new ArrayList<>();
    private final ArrayList<Loan> loans = new ArrayList<>();
    private final FileStorage storage;

    // Alerts ka queue: producer = request bhejne wala / overdue checker, consumer = alert thread
    private final LinkedList<String> alerts = new LinkedList<>();
    private final Object alertLock = new Object();
    private final Object stopLock = new Object();
    private volatile boolean running = false;
    private Thread overdueThread;
    private Thread alertThread;

    public LendingManager(FileStorage storage) {
        this.storage = storage;
    }

    // =============================================================== loading / saving

    public synchronized ArrayList<String> loadData() throws IOException {
        ArrayList<String> warnings = storage.loadAll(users, items, requests, loans);

        // Item ka status file mein galat bhi ho, active loan ka matlab item borrowed hai
        for (Loan loan : loans) {
            Item item = loan.getItem();
            if (loan.isActive() && item.getStatus() != Item.Status.BORROWED) {
                item.setStatus(Item.Status.BORROWED);
                warnings.add("Item '" + item.getName() + "' was marked as borrowed again because loan #"
                        + loan.getLoanId() + " is still active.");
            }
        }
        return warnings;
    }

    public synchronized void saveAll() throws IOException {
        storage.saveAll(users, items, requests, loans);
    }

    // Har change ke baad auto-save, taaki crash hone par data na jaaye
    private void autoSave() {
        try {
            saveAll();
        } catch (IOException e) {
            System.out.println("Warning: data could not be saved (" + e.getMessage() + ").");
        }
    }

    public synchronized String getDataSummary() {
        return users.size() + " users, " + items.size() + " items, " + requests.size()
                + " requests, " + loans.size() + " loans";
    }

    // ================================================================ finding records

    public synchronized User findUser(int userId) throws InvalidLoanException {
        for (User user : users) {
            if (user.getUserId() == userId) {
                return user;
            }
        }
        throw new InvalidLoanException("No user found with ID " + userId + ".");
    }

    public synchronized Item findItem(int itemId) throws InvalidLoanException {
        for (Item item : items) {
            if (item.getItemId() == itemId) {
                return item;
            }
        }
        throw new InvalidLoanException("No item found with ID " + itemId + ".");
    }

    public synchronized BorrowRequest findRequest(int requestId) throws InvalidLoanException {
        for (BorrowRequest request : requests) {
            if (request.getRequestId() == requestId) {
                return request;
            }
        }
        throw new InvalidLoanException("No borrowing request found with ID " + requestId + ".");
    }

    public synchronized Loan findLoan(int loanId) throws InvalidLoanException {
        for (Loan loan : loans) {
            if (loan.getLoanId() == loanId) {
                return loan;
            }
        }
        throw new InvalidLoanException("No loan found with ID " + loanId + ".");
    }

    // =============================================================== users and items

    public synchronized User registerUser(String name, String room) throws InvalidLoanException {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidLoanException("Name cannot be empty.");
        }
        User user;
        if (room == null || room.trim().isEmpty()) {
            user = new User(name.trim());
        } else {
            user = new User(name.trim(), room.trim());
        }
        users.add(user);
        autoSave();
        return user;
    }

    // Item object Main banata hai aur yahan pass hota hai
    public synchronized void addItem(Item item) throws InvalidLoanException {
        if (item.getName() == null || item.getName().trim().isEmpty()) {
            throw new InvalidLoanException("Item name cannot be empty.");
        }
        findUser(item.getOwner().getUserId());   // owner registered hona chahiye
        items.add(item);
        autoSave();
    }

    public synchronized ArrayList<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    public synchronized ArrayList<Item> getAllItems() {
        return new ArrayList<>(items);
    }

    public synchronized ArrayList<BorrowRequest> getAllRequests() {
        return new ArrayList<>(requests);
    }

    public synchronized ArrayList<Loan> getAllLoans() {
        return new ArrayList<>(loans);
    }

    public synchronized ArrayList<Item> getAvailableItems() {
        ArrayList<Item> available = new ArrayList<>();
        for (Item item : items) {
            if (item.isAvailable()) {
                available.add(item);
            }
        }
        return available;
    }

    public int getMaxDaysFor(Item item) {
        if (item instanceof Book) {
            return BookLoan.MAX_DAYS;
        }
        return EquipmentLoan.MAX_DAYS;
    }

    // ============================================================ borrowing requests

    public synchronized BorrowRequest sendRequest(int userId, int itemId, int days)
            throws InvalidLoanException {
        return sendRequest(findUser(userId), findItem(itemId), days);
    }

    public synchronized BorrowRequest sendRequest(User borrower, Item item, int days)
            throws InvalidLoanException {
        if (item.getOwner().getUserId() == borrower.getUserId()) {
            throw new InvalidLoanException("You cannot borrow your own item.");
        }
        if (!item.isAvailable()) {
            throw new ItemUnavailableException(item, "it is currently " + item.getStatus().name().toLowerCase() + ".");
        }
        int maxDays = getMaxDaysFor(item);
        if (days < 1 || days > maxDays) {
            throw new InvalidLoanException("Borrowing period must be between 1 and " + maxDays + " days for this item.");
        }
        if (countActiveLoans(borrower) >= User.MAX_ACTIVE_LOANS) {
            throw new InvalidLoanException(borrower.getName() + " already has " + User.MAX_ACTIVE_LOANS
                    + " active loans. Return something first.");
        }
        if (hasPendingRequest(borrower, item)) {
            throw new InvalidLoanException("You already have a pending request for this item.");
        }

        BorrowRequest request = new BorrowRequest(borrower, item, days);
        requests.add(request);
        autoSave();

        postAlert("New request #" + request.getRequestId() + ": " + borrower.getName() + " wants '"
                + item.getName() + "' for " + days + " day(s). Owner " + item.getOwner().getName()
                + " can decide using menu option 6.");
        return request;
    }

    public synchronized ArrayList<BorrowRequest> getPendingRequests() {
        ArrayList<BorrowRequest> pending = new ArrayList<>();
        for (BorrowRequest request : requests) {
            if (request.isPending()) {
                pending.add(request);
            }
        }
        return pending;
    }

    // Availability check aur loan banana ek hi synchronized method mein hai -
    // isliye do requests ek saath approve ho kar same item ko double-lend nahi kar sakti.
    public synchronized Loan approveRequest(int requestId, int ownerId, LocalDate handoverDate)
            throws InvalidLoanException {
        BorrowRequest request = findRequest(requestId);
        checkPendingAndOwner(request, ownerId);
        Item item = request.getItem();

        // Yahan check kar rahe hain ki item pehle se borrowed toh nahi hai
        if (!item.isAvailable()) {
            throw new ItemUnavailableException(item, "it is currently " + item.getStatus().name().toLowerCase()
                    + ". Reject this request or wait until the item is back.");
        }
        if (handoverDate.isAfter(LocalDate.now())) {
            throw new InvalidLoanException("Handover date cannot be in the future.");
        }
        if (countActiveLoans(request.getBorrower()) >= User.MAX_ACTIVE_LOANS) {
            throw new InvalidLoanException(request.getBorrower().getName() + " already has "
                    + User.MAX_ACTIVE_LOANS + " active loans.");
        }

        Loan loan = createLoan(request, handoverDate);
        loans.add(loan);
        item.setStatus(Item.Status.BORROWED);
        request.setStatus(BorrowRequest.Status.APPROVED);
        autoSave();

        postAlert("Request #" + requestId + " approved: " + request.getBorrower().getName()
                + " has '" + item.getName() + "', due on " + Loan.formatDate(loan.getDueDate()) + ".");
        return loan;
    }

    // Overload: handover date nahi di toh aaj ki date
    public synchronized Loan approveRequest(int requestId, int ownerId) throws InvalidLoanException {
        return approveRequest(requestId, ownerId, LocalDate.now());
    }

    public synchronized BorrowRequest rejectRequest(int requestId, int ownerId) throws InvalidLoanException {
        BorrowRequest request = findRequest(requestId);
        checkPendingAndOwner(request, ownerId);
        request.setStatus(BorrowRequest.Status.REJECTED);
        autoSave();
        return request;
    }

    private void checkPendingAndOwner(BorrowRequest request, int ownerId) throws InvalidLoanException {
        if (!request.isPending()) {
            throw new InvalidLoanException("Request #" + request.getRequestId() + " is already "
                    + request.getStatus() + ", so it cannot be processed again.");
        }
        findUser(ownerId);
        if (request.getItem().getOwner().getUserId() != ownerId) {
            throw new InvalidLoanException("Only the owner of the item ("
                    + request.getItem().getOwner().getName() + ") can approve or reject this request.");
        }
    }

    // Book ke liye BookLoan, baaki sab ke liye EquipmentLoan
    private Loan createLoan(BorrowRequest request, LocalDate issueDate) {
        if (request.getItem() instanceof Book) {
            return new BookLoan(request.getBorrower(), request.getItem(), issueDate, request.getRequestedDays());
        }
        return new EquipmentLoan(request.getBorrower(), request.getItem(), issueDate, request.getRequestedDays());
    }

    private boolean hasPendingRequest(User borrower, Item item) {
        for (BorrowRequest request : requests) {
            if (request.isPending()
                    && request.getBorrower().getUserId() == borrower.getUserId()
                    && request.getItem().getItemId() == item.getItemId()) {
                return true;
            }
        }
        return false;
    }

    // ============================================================ returning and reports

    public synchronized Loan returnItem(int loanId, int userId, LocalDate returnDate)
            throws InvalidLoanException {
        Loan loan = findLoan(loanId);
        checkPartyOfLoan(loan, userId);
        if (returnDate.isAfter(LocalDate.now())) {
            throw new InvalidLoanException("Return date cannot be in the future.");
        }
        loan.markReturned(returnDate);     // dobara return karne par yahin exception aata hai
        loan.getItem().setStatus(Item.Status.AVAILABLE);
        autoSave();
        return loan;
    }

    public synchronized Loan returnItem(int loanId, int userId) throws InvalidLoanException {
        return returnItem(loanId, userId, LocalDate.now());
    }

    public synchronized Loan reportLost(int loanId, int reporterId, String reason)
            throws InvalidLoanException {
        Loan loan = checkProblemReport(loanId, reporterId, reason);
        loan.markLost(reason.trim());
        loan.getItem().setStatus(Item.Status.LOST);
        autoSave();
        return loan;
    }

    public synchronized Loan reportDamaged(int loanId, int reporterId, String reason)
            throws InvalidLoanException {
        Loan loan = checkProblemReport(loanId, reporterId, reason);
        loan.markDamaged(reason.trim());
        loan.getItem().setStatus(Item.Status.DAMAGED);
        autoSave();
        return loan;
    }

    private Loan checkProblemReport(int loanId, int reporterId, String reason) throws InvalidLoanException {
        Loan loan = findLoan(loanId);
        checkPartyOfLoan(loan, reporterId);
        if (reason == null || reason.trim().isEmpty()) {
            throw new InvalidLoanException("A reason is required for a lost/damaged report.");
        }
        if (!loan.isActive()) {
            throw new InvalidLoanException("Loan " + loanId + " is already closed (" + loan.getStatus() + ").");
        }
        return loan;
    }

    // Sirf owner ya borrower hi loan ko return/report kar sakte hain
    private void checkPartyOfLoan(Loan loan, int userId) throws InvalidLoanException {
        findUser(userId);
        boolean isBorrower = loan.getBorrower().getUserId() == userId;
        boolean isOwner = loan.getOwner().getUserId() == userId;
        if (!isBorrower && !isOwner) {
            throw new InvalidLoanException("User " + userId + " is neither the owner nor the borrower of loan "
                    + loan.getLoanId() + ".");
        }
    }

    // ============================================================ loan views

    public synchronized ArrayList<Loan> getActiveLoans() {
        ArrayList<Loan> active = new ArrayList<>();
        for (Loan loan : loans) {
            if (loan.isActive()) {
                active.add(loan);
            }
        }
        return active;
    }

    public synchronized ArrayList<Loan> getOverdueLoans() {
        ArrayList<Loan> overdue = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Loan loan : loans) {
            if (loan.isOverdue(today)) {
                overdue.add(loan);
            }
        }
        return overdue;
    }

    public synchronized ArrayList<Loan> getLoansOf(User user, boolean asBorrower) {
        ArrayList<Loan> result = new ArrayList<>();
        for (Loan loan : loans) {
            User person = asBorrower ? loan.getBorrower() : loan.getOwner();
            if (person.getUserId() == user.getUserId()) {
                result.add(loan);
            }
        }
        return result;
    }

    // Recursion yahan natural lagta hai: pehli loan ka summary + baaki list ki history.
    // History chhoti hoti hai (student scale), isliye stack overflow ka dar nahi.
    public String buildHistoryText(ArrayList<Loan> list, int index) {
        if (index >= list.size()) {
            return "";
        }
        return "  " + list.get(index).getSummary() + "\n" + buildHistoryText(list, index + 1);
    }

    private int countActiveLoans(User user) {
        int count = 0;
        for (Loan loan : loans) {
            if (loan.isActive() && loan.getBorrower().getUserId() == user.getUserId()) {
                count++;
            }
        }
        return count;
    }

    // ============================================================ reliability score

    private static final int ON_TIME = 0;
    private static final int LATE = 1;
    private static final int LOST = 2;
    private static final int DAMAGED = 3;

    // counts[] mein ek user ke closed loans ka result gina jaata hai
    private int[] countOutcomes(User user) {
        int[] counts = new int[4];
        for (Loan loan : loans) {
            if (loan.getBorrower().getUserId() != user.getUserId()) {
                continue;
            }
            if (loan.getStatus() == Loan.Status.RETURNED) {
                if (loan.wasReturnedLate()) {
                    counts[LATE]++;
                } else {
                    counts[ON_TIME]++;
                }
            } else if (loan.getStatus() == Loan.Status.LOST) {
                counts[LOST]++;
            } else if (loan.getStatus() == Loan.Status.DAMAGED) {
                counts[DAMAGED]++;
            }
        }
        return counts;
    }

    public synchronized int calculateScore(User user) {
        int[] counts = countOutcomes(user);
        return User.computeScore(counts[LATE], counts[LOST], counts[DAMAGED]);
    }

    public synchronized String getScoreReport(User user) {
        int[] counts = countOutcomes(user);
        int score = User.computeScore(counts[LATE], counts[LOST], counts[DAMAGED]);
        int closed = counts[ON_TIME] + counts[LATE] + counts[LOST] + counts[DAMAGED];

        StringBuilder sb = new StringBuilder();
        sb.append("Reliability report for ").append(user).append("\n");
        sb.append("  Returned on time : ").append(counts[ON_TIME]).append(" (no penalty)\n");
        sb.append("  Returned late    : ").append(counts[LATE]).append(" (-").append(User.LATE_PENALTY).append(" each)\n");
        sb.append("  Reported lost    : ").append(counts[LOST]).append(" (-").append(User.LOST_PENALTY).append(" each)\n");
        sb.append("  Reported damaged : ").append(counts[DAMAGED]).append(" (-").append(User.DAMAGED_PENALTY).append(" each)\n");

        sb.append("  Why the score changed:\n");
        boolean anyPenalty = false;
        for (Loan loan : loans) {
            if (loan.getBorrower().getUserId() != user.getUserId()) {
                continue;
            }
            if (loan.wasReturnedLate()) {
                sb.append("    - Loan #").append(loan.getLoanId()).append(" (").append(loan.getItem().getName())
                  .append(") returned late: -").append(User.LATE_PENALTY).append("\n");
                anyPenalty = true;
            } else if (loan.getStatus() == Loan.Status.LOST) {
                sb.append("    - Loan #").append(loan.getLoanId()).append(" (").append(loan.getItem().getName())
                  .append(") reported lost: -").append(User.LOST_PENALTY).append("\n");
                anyPenalty = true;
            } else if (loan.getStatus() == Loan.Status.DAMAGED) {
                sb.append("    - Loan #").append(loan.getLoanId()).append(" (").append(loan.getItem().getName())
                  .append(") reported damaged: -").append(User.DAMAGED_PENALTY).append("\n");
                anyPenalty = true;
            }
        }
        if (!anyPenalty) {
            sb.append("    - No penalties so far.\n");
        }

        if (closed > 0) {
            double onTimeRate = (double) counts[ON_TIME] / closed * 100;
            sb.append(String.format("  On-time rate     : %.1f%% (%d of %d closed loans)%n", onTimeRate, counts[ON_TIME], closed));
        }
        sb.append("  Score = ").append(User.START_SCORE).append(" - penalties, kept between 0 and 100\n");
        sb.append("  FINAL SCORE      : ").append(score).append(" / 100");
        return sb.toString();
    }

    // ============================================================ background threads

    public void startBackgroundThreads() {
        running = true;
        overdueThread = new Thread(new OverdueChecker(), "OverdueChecker");
        alertThread = new Thread(new AlertPrinter(), "AlertPrinter");
        overdueThread.setDaemon(true);
        alertThread.setDaemon(true);
        overdueThread.start();
        alertThread.start();
    }

    // Thread ko rokne ka safe tareeka: flag false karo, wait() mein soye threads ko jagao, phir join().
    // stop() jaise deprecated methods use nahi kiye.
    public void shutdown() {
        running = false;
        synchronized (stopLock) {
            stopLock.notifyAll();
        }
        synchronized (alertLock) {
            alertLock.notifyAll();
        }
        try {
            if (overdueThread != null) {
                overdueThread.join(2000);
            }
            if (alertThread != null) {
                alertThread.join(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Producer side: alert queue mein message daalo aur consumer ko jagao
    private void postAlert(String message) {
        synchronized (alertLock) {
            alerts.addLast(message);
            alertLock.notifyAll();
        }
    }

    // Inner class: outer class ke fields (alerts, running, loans) seedha use karti hai
    private class OverdueChecker implements Runnable {

        // Sirf is thread ke paas hai, isliye isko synchronized karne ki zaroorat nahi
        private final HashSet<Integer> alreadyAnnounced = new HashSet<>();

        @Override
        public void run() {
            while (running) {
                announceNewOverdueLoans();
                synchronized (stopLock) {
                    if (!running) {
                        break;
                    }
                    try {
                        // sleep ki jagah wait(timeout): shutdown par turant jaag jaata hai
                        stopLock.wait(CHECK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        private void announceNewOverdueLoans() {
            LocalDate today = LocalDate.now();
            for (Loan loan : getOverdueLoans()) {
                Integer id = loan.getLoanId();
                if (!alreadyAnnounced.contains(id)) {
                    alreadyAnnounced.add(id);
                    postAlert("Overdue: '" + loan.getItem().getName() + "' borrowed by "
                            + loan.getBorrower().getName() + " was due on " + Loan.formatDate(loan.getDueDate())
                            + " (" + loan.getDaysOverdue(today) + " day(s) late).");
                }
            }
        }
    }

    // Consumer side: queue khaali ho toh wait(), message aate hi notifyAll se jaagta hai
    private class AlertPrinter implements Runnable {

        @Override
        public void run() {
            while (true) {
                String message;
                synchronized (alertLock) {
                    while (alerts.isEmpty() && running) {
                        try {
                            alertLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (alerts.isEmpty()) {
                        return;     // app band ho raha hai aur koi alert bacha nahi
                    }
                    message = alerts.removeFirst();
                }
                // Print lock ke bahar, taaki lock zyada der na rukey
                System.out.println("\n[ALERT] " + message);
            }
        }
    }
}
