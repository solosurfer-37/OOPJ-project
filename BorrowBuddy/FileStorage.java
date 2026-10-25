import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

// Saare files ka kaam yahin hota hai. Format: ek record = ek line, fields '|' se alag.
// '|' aur '\' ko escape karte hain taaki naam mein ye characters ho toh bhi file kharab na ho.
public class FileStorage {

    private static final String USERS_FILE = "users.txt";
    private static final String ITEMS_FILE = "items.txt";
    private static final String REQUESTS_FILE = "requests.txt";
    private static final String LOANS_FILE = "loans.txt";

    private final Path folder;

    public FileStorage(String folderName) {
        this.folder = Paths.get(folderName);
    }

    // ---------------------------------------------------------------- loading

    // Returns warnings (bad lines etc). Missing files = pehli baar run, koi problem nahi.
    public ArrayList<String> loadAll(ArrayList<User> users, ArrayList<Item> items,
                                     ArrayList<BorrowRequest> requests, ArrayList<Loan> loans)
            throws IOException {
        ArrayList<String> warnings = new ArrayList<>();
        loadUsers(users, warnings);
        loadItems(items, users, warnings);
        loadRequests(requests, users, items, warnings);
        loadLoans(loans, users, items, warnings);
        return warnings;
    }

    private void loadUsers(ArrayList<User> users, ArrayList<String> warnings) throws IOException {
        ArrayList<String> lines = readLines(USERS_FILE);
        boolean foundBadLine = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) {
                continue;
            }
            try {
                ArrayList<String> f = splitLine(lines.get(i));
                requireFields(f, 3);
                users.add(new User(Integer.parseInt(f.get(0)), f.get(1), f.get(2)));
            } catch (IllegalArgumentException e) {
                warnings.add(USERS_FILE + " line " + (i + 1) + " skipped: " + e.getMessage());
                foundBadLine = true;
            }
        }
        if (foundBadLine) {
            backupFile(USERS_FILE);
        }
    }

    private void loadItems(ArrayList<Item> items, ArrayList<User> users, ArrayList<String> warnings)
            throws IOException {
        ArrayList<String> lines = readLines(ITEMS_FILE);
        boolean foundBadLine = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) {
                continue;
            }
            try {
                items.add(parseItem(splitLine(lines.get(i)), users));
            } catch (IllegalArgumentException e) {
                warnings.add(ITEMS_FILE + " line " + (i + 1) + " skipped: " + e.getMessage());
                foundBadLine = true;
            }
        }
        if (foundBadLine) {
            backupFile(ITEMS_FILE);
        }
    }

    private Item parseItem(ArrayList<String> f, ArrayList<User> users) {
        requireFields(f, 8);
        String type = f.get(0);
        int id = Integer.parseInt(f.get(1));
        String name = f.get(2);
        String category = f.get(3);
        User owner = lookupUser(users, Integer.parseInt(f.get(4)));
        Item.Status status = Item.Status.valueOf(f.get(5));

        if (type.equals("BOOK")) {
            return new Book(id, name, category, owner, status, f.get(6));
        } else if (type.equals("TEXTBOOK")) {
            return new TextBook(id, name, owner, status, f.get(6), f.get(7));
        } else if (type.equals("ITEM")) {
            return new Item(id, name, category, owner, status);
        }
        throw new IllegalArgumentException("unknown item type '" + type + "'");
    }

    private void loadRequests(ArrayList<BorrowRequest> requests, ArrayList<User> users,
                              ArrayList<Item> items, ArrayList<String> warnings) throws IOException {
        ArrayList<String> lines = readLines(REQUESTS_FILE);
        boolean foundBadLine = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) {
                continue;
            }
            try {
                ArrayList<String> f = splitLine(lines.get(i));
                requireFields(f, 6);
                requests.add(new BorrowRequest(
                        Integer.parseInt(f.get(0)),
                        lookupUser(users, Integer.parseInt(f.get(1))),
                        lookupItem(items, Integer.parseInt(f.get(2))),
                        Loan.parseDate(f.get(3)),
                        Integer.parseInt(f.get(4)),
                        BorrowRequest.Status.valueOf(f.get(5))));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                warnings.add(REQUESTS_FILE + " line " + (i + 1) + " skipped: " + e.getMessage());
                foundBadLine = true;
            }
        }
        if (foundBadLine) {
            backupFile(REQUESTS_FILE);
        }
    }

    private void loadLoans(ArrayList<Loan> loans, ArrayList<User> users, ArrayList<Item> items,
                           ArrayList<String> warnings) throws IOException {
        ArrayList<String> lines = readLines(LOANS_FILE);
        boolean foundBadLine = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) {
                continue;
            }
            try {
                loans.add(parseLoan(splitLine(lines.get(i)), users, items));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                warnings.add(LOANS_FILE + " line " + (i + 1) + " skipped: " + e.getMessage());
                foundBadLine = true;
            }
        }
        if (foundBadLine) {
            backupFile(LOANS_FILE);
        }
    }

    private Loan parseLoan(ArrayList<String> f, ArrayList<User> users, ArrayList<Item> items) {
        requireFields(f, 9);
        String type = f.get(0);
        int id = Integer.parseInt(f.get(1));
        User borrower = lookupUser(users, Integer.parseInt(f.get(2)));
        Item item = lookupItem(items, Integer.parseInt(f.get(3)));
        LocalDate issueDate = Loan.parseDate(f.get(4));
        LocalDate dueDate = Loan.parseDate(f.get(5));
        LocalDate returnDate = null;
        if (!f.get(6).isEmpty()) {
            returnDate = Loan.parseDate(f.get(6));
        }
        Loan.Status status = Loan.Status.valueOf(f.get(7));
        String reason = f.get(8);

        if (type.equals("Book")) {
            return new BookLoan(id, borrower, item, issueDate, dueDate, returnDate, status, reason);
        } else if (type.equals("Equipment")) {
            return new EquipmentLoan(id, borrower, item, issueDate, dueDate, returnDate, status, reason);
        }
        throw new IllegalArgumentException("unknown loan type '" + type + "'");
    }

    private User lookupUser(ArrayList<User> users, int id) {
        for (User user : users) {
            if (user.getUserId() == id) {
                return user;
            }
        }
        throw new IllegalArgumentException("user ID " + id + " does not exist");
    }

    private Item lookupItem(ArrayList<Item> items, int id) {
        for (Item item : items) {
            if (item.getItemId() == id) {
                return item;
            }
        }
        throw new IllegalArgumentException("item ID " + id + " does not exist");
    }

    private void requireFields(ArrayList<String> fields, int expected) {
        if (fields.size() < expected) {
            throw new IllegalArgumentException("expected " + expected + " fields but found " + fields.size());
        }
    }

    private ArrayList<String> readLines(String fileName) throws IOException {
        ArrayList<String> lines = new ArrayList<>();
        Path path = folder.resolve(fileName);
        if (!Files.exists(path)) {
            return lines;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // Kharab line mili toh original file ki copy rakh lete hain, warna agle save par wo line udd jaati
    private void backupFile(String fileName) throws IOException {
        Path source = folder.resolve(fileName);
        Path backup = folder.resolve(fileName + ".bak");
        Files.copy(source, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    // ----------------------------------------------------------------- saving

    public void saveAll(ArrayList<User> users, ArrayList<Item> items,
                        ArrayList<BorrowRequest> requests, ArrayList<Loan> loans) throws IOException {
        Files.createDirectories(folder);

        ArrayList<String> lines = new ArrayList<>();
        for (User user : users) {
            lines.add(joinFields(String.valueOf(user.getUserId()), user.getName(), user.getRoom()));
        }
        writeLines(USERS_FILE, lines);

        lines = new ArrayList<>();
        for (Item item : items) {
            String[] extra = item.getExtraFields();
            lines.add(joinFields(item.getTypeCode(), String.valueOf(item.getItemId()), item.getName(),
                    item.getCategory(), String.valueOf(item.getOwner().getUserId()),
                    item.getStatus().name(), extra[0], extra[1]));
        }
        writeLines(ITEMS_FILE, lines);

        lines = new ArrayList<>();
        for (BorrowRequest request : requests) {
            lines.add(joinFields(String.valueOf(request.getRequestId()),
                    String.valueOf(request.getBorrower().getUserId()),
                    String.valueOf(request.getItem().getItemId()),
                    Loan.formatDate(request.getRequestDate()),
                    String.valueOf(request.getRequestedDays()),
                    request.getStatus().name()));
        }
        writeLines(REQUESTS_FILE, lines);

        lines = new ArrayList<>();
        for (Loan loan : loans) {
            String returnText = "";
            if (loan.getReturnDate() != null) {
                returnText = Loan.formatDate(loan.getReturnDate());
            }
            lines.add(joinFields(loan.getLoanType(), String.valueOf(loan.getLoanId()),
                    String.valueOf(loan.getBorrower().getUserId()),
                    String.valueOf(loan.getItem().getItemId()),
                    Loan.formatDate(loan.getIssueDate()), Loan.formatDate(loan.getDueDate()),
                    returnText, loan.getStatus().name(), loan.getProblemReason()));
        }
        writeLines(LOANS_FILE, lines);
    }

    // Pehle temp file mein likhte hain, phir replace. Beech mein crash hua toh purani file safe rahegi.
    private void writeLines(String fileName, ArrayList<String> lines) throws IOException {
        Path target = folder.resolve(fileName);
        Path temp = folder.resolve(fileName + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    // --------------------------------------------------------- field handling

    private String joinFields(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("|", "\\|")
                .replace("\r", "").replace("\n", "\\n");
    }

    // '|' par split karta hai par "\|" ko normal character maanta hai
    private ArrayList<String> splitLine(String line) {
        ArrayList<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                if (next == 'n') {
                    current.append('\n');
                } else {
                    current.append(next);
                }
                i++;
            } else if (c == '|') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
