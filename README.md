# ATM Simulator
Made by - Vansh Kumar , Rakshit Khanna 

A simple ATM Simulator built using Core Java as a college OOP mini-project.

The project runs in the terminal and allows users to perform basic ATM operations like checking balance, depositing money, withdrawing money and viewing recent transactions.

## Features

- Login using account number and PIN
- Check account balance
- Deposit money
- Withdraw money with balance validation
- View the last 5 transactions
- Store transaction history in a text file
- Save account balances even after restarting the program
- Block login after 3 incorrect PIN attempts

## Demo Accounts

| Account Number | PIN | Initial Balance |
|---|---|---:|
| ACC1001 | 1234 | ₹10,000 |
| ACC1002 | 4321 | ₹25,000 |

## Technologies Used

- Java (Core Java)
- File Handling
- Object-Oriented Programming
- Exception Handling

## Project Files

```text
SmartATM/
│
├── Main.java
├── accounts.txt
└── transactions.txt
```

- `Main.java` – Contains the complete program.
- `accounts.txt` – Stores account details and updated balances.
- `transactions.txt` – Stores deposit and withdrawal records.

The two text files are created automatically when the program runs for the first time.

## How to Run

Make sure Java JDK is installed on your system.

1. Download or clone this repository.
2. Open CMD or terminal in the project folder.
3. Compile the program:

```bash
javac Main.java
```

4. Run the application:

```bash
java Main
```

## OOPS Concepts Used

This project uses different Java concepts:

- Classes and Objects
- Constructors
- Encapsulation
- Inheritance
- Abstract Classes
- Method Overloading and Overriding
- Access Modifiers
- Arrays
- Inner Classes
- File Handling
- Exception Handling


---

Made as part of my Java OOP mini-project.
