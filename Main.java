import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

abstract class Account {
    private String number, pin;
    private double balance;
    private boolean locked;
    Account(String number, String pin, double balance) {
        this.number = number; this.pin = pin; this.balance = balance; locked = false;
    }
    public String getNumber() { return number; }
    public String getPin() { return pin; }
    public double getBalance() { return balance; }
    public boolean checkPin(String entered) { return pin.equals(entered); }
    public boolean isLocked() { return locked; }
    public void lock() { locked = true; }
    public void deposit(double amount) { balance += amount; }
    public boolean withdraw(double amount) {
        if (amount > balance) return false;
        balance -= amount; return true;
    }
    abstract String getType();
}

class SavingsAccount extends Account {
    SavingsAccount(String no, String pin, double balance) { super(no, pin, balance); }
    @Override String getType() { return "Savings Account"; }
}

class Transaction {
    private String number, type, time;
    private double amount, balance;
    Transaction(String number, String type, double amount, double balance) {
        this.number = number; this.type = type; this.amount = amount; this.balance = balance;
        time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    public String toFileString() { return number + "|" + time + "|" + type + "|" + amount + "|" + balance; }
    public String getDetails() { return type + " Rs." + amount + " | Balance: Rs." + balance + " | " + time; }
}

class ATM {
    private final String ACCOUNT_FILE = "accounts.txt", TRANSACTION_FILE = "transactions.txt";
    private Scanner input = new Scanner(System.in);
    private Account[] accounts = new Account[10];
    private int count = 0;
    private boolean running = true;

    ATM() { loadAccounts(); }
    public void start() {
        while (running) {
            Account user = login();
            if (user == null) break;
            showMenu(user);
        }
        System.out.println("Thank you for using Smart ATM.");
    }
    private void addDemoAccounts() {
        accounts[count++] = new SavingsAccount("ACC1001", "1234", 10000);
        accounts[count++] = new SavingsAccount("ACC1002", "4321", 25000);
    }
    private void loadAccounts() {
        File file = new File(ACCOUNT_FILE);
        if (!file.exists()) { addDemoAccounts(); saveAccounts(); return; }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null && count < accounts.length) {
                String[] d = line.split(",");
                if (d.length == 3) try {
                    accounts[count++] = new SavingsAccount(d[0], d[1], Double.parseDouble(d[2]));
                } catch (NumberFormatException e) { System.out.println("Skipping invalid account record."); }
            }
        } catch (IOException e) { System.out.println("Could not read accounts file: " + e.getMessage()); }
        if (count == 0) { addDemoAccounts(); saveAccounts(); }
    }
    private void saveAccounts() {
        try (FileWriter w = new FileWriter(ACCOUNT_FILE)) {
            for (int i = 0; i < count; i++)
                w.write(accounts[i].getNumber() + "," + accounts[i].getPin() + "," + accounts[i].getBalance() + "\n");
        } catch (IOException e) { System.out.println("Could not save balance: " + e.getMessage()); }
    }
    private Account findAccount(String no) {
        for (int i = 0; i < count; i++)
            if (accounts[i].getNumber().equalsIgnoreCase(no)) return accounts[i];
        return null;
    }
    private Account login() {
        while (running) {
            System.out.print("\nEnter Account Number (or EXIT): ");
            String no = input.nextLine().trim();
            if (no.equalsIgnoreCase("EXIT")) return null;
            Account user = findAccount(no);
            if (user == null) { System.out.println("Invalid account number."); continue; }
            if (user.isLocked()) { System.out.println("Account is locked for this session."); continue; }
            for (int i = 1; i <= 3; i++) {
                System.out.print("Enter PIN: ");
                if (user.checkPin(input.nextLine().trim())) {
                    System.out.println("\nLogin successful! Welcome to your " + user.getType() + ".");
                    return user;
                }
                System.out.println("Incorrect PIN. Attempts left: " + (3 - i));
            }
            user.lock();
            System.out.println("Three incorrect attempts. Account locked for this session.");
        }
        return null;
    }
    private void showMenu(Account user) {
        boolean loggedIn = true;
        while (loggedIn && running) {
            System.out.println("\n1. Check Balance\n2. Deposit Money\n3. Withdraw Money\n4. Mini Statement\n5. Logout\n6. Exit");
            switch (readChoice("Enter your choice: ")) {
                case 1: System.out.printf("Current Balance: Rs. %.2f%n", user.getBalance()); break;
                case 2: deposit(user); break;
                case 3: withdraw(user); break;
                case 4: miniStatement(user); break;
                case 5: loggedIn = false; System.out.println("Logged out successfully."); break;
                case 6: running = false; loggedIn = false; break;
                default: System.out.println("Invalid menu choice.");
            }
        }
    }
    private int readChoice(String msg) {
        while (true) try {
            System.out.print(msg); return Integer.parseInt(input.nextLine().trim());
        } catch (NumberFormatException e) { System.out.println("Enter a valid whole number."); }
    }
    private double readAmount() {
        while (true) try {
            System.out.print("Enter amount: Rs. "); return Double.parseDouble(input.nextLine().trim());
        } catch (NumberFormatException e) { System.out.println("Enter a valid amount."); }
    }
    private void deposit(Account user) {
        double amount = readAmount();
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            System.out.println("Deposit must be greater than zero."); return;
        }
        user.deposit(amount); saveAccounts();
        Transaction t = new Transaction(user.getNumber(), "DEPOSIT", amount, user.getBalance());
        saveTransaction(t); new Receipt().print(t, "Cash deposited successfully");
    }
    private void withdraw(Account user) {
        double amount = readAmount();
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            System.out.println("Withdrawal must be greater than zero."); return;
        }
        if (!user.withdraw(amount)) { System.out.println("Insufficient balance."); return; }
        saveAccounts();
        Transaction t = new Transaction(user.getNumber(), "WITHDRAWAL", amount, user.getBalance());
        saveTransaction(t); new Receipt().print(t);
    }
    private void saveTransaction(Transaction t) {
        try (FileWriter w = new FileWriter(TRANSACTION_FILE, true)) { w.write(t.toFileString() + "\n"); }
        catch (IOException e) { System.out.println("Could not save transaction: " + e.getMessage()); }
    }
    private void miniStatement(Account user) {
        String[] recent = new String[5]; int size = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(TRANSACTION_FILE))) {
            String line;
            while ((line = br.readLine()) != null) if (line.startsWith(user.getNumber() + "|")) {
                if (size < 5) recent[size++] = line;
                else { for (int i = 0; i < 4; i++) recent[i] = recent[i + 1]; recent[4] = line; }
            }
        } catch (FileNotFoundException e) { System.out.println("No transactions found yet."); return; }
          catch (IOException e) { System.out.println("Could not read transaction history."); return; }
        System.out.println("\n----- MINI STATEMENT (LATEST 5) -----");
        if (size == 0) System.out.println("No transactions found.");
        for (int i = 0; i < size; i++) {
            String[] d = recent[i].split("\\|");
            System.out.println(d[1] + " | " + d[2] + " | Rs. " + d[3] + " | Balance: Rs. " + d[4]);
        }
    }
    class Receipt {
        void print(Transaction t) { print(t, "Transaction completed"); }
        void print(Transaction t, String note) { System.out.println("\n--- RECEIPT ---\n" + note + "\n" + t.getDetails()); }
    }
}

public class Main {
    public static void main(String[] args) { new ATM().start(); }
}
