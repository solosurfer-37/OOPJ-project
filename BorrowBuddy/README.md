# BorrowBuddy - Shared Item Lending & Trust Tracker

A console-based Java mini-project for college students and hostel roommates who lend and borrow
books, calculators, chargers, sports gear and lab accessories.

## 1. Problem statement

In a hostel, items get lent informally ("bhai calculator de dena") and then nobody remembers who has
what, when it was due, or whether it came back damaged. BorrowBuddy records the owner, borrower, due
date and outcome of every loan, and turns that history into a transparent reliability score.

## 2. Objectives and key features

- Register users and add items (books, textbooks, equipment).
- Borrowers send requests; the **owner** approves or rejects them.
- Active loans with due dates; **overdue is calculated from today's date**, never marked by hand.
- Return items, or report them lost/damaged with a reason (these are *not* counted as returns).
- Borrowing history per user and a reliability score with a full explanation.
- Data is saved to local files and reloaded on the next start.
- Two background threads: an overdue checker and an alert printer.

## 3. Requirements and commands

- JDK 17 or later (tested on JDK 21). You need the **JDK** (`javac`), not just the JRE.
- No external libraries, no packages. All `.java` files stay in one folder.

```
javac *.java
java Main
```

Data is stored in a `data` folder created next to where you run the program
(`users.txt`, `items.txt`, `requests.txt`, `loans.txt`). No sample records are ever added.
Date format everywhere: `dd-MM-yyyy`.

## 4. Classes

| File | Responsibility |
|------|----------------|
| `Main` | `main` method, numbered menu, all `Scanner` input and printing. |
| `LendingManager` | Holds users/items/requests/loans, enforces every rule, runs background threads. |
| `User` | ID, name, room, loan limit and the score formula constants. |
| `Item` | Base class: ID, name, category, owner, status (`AVAILABLE/BORROWED/LOST/DAMAGED`). |
| `Book` | Extends `Item`, adds author. |
| `TextBook` | Extends `Book`, adds subject. |
| `BorrowRequest` | One request with status `PENDING/APPROVED/REJECTED`. |
| `Loan` | **Abstract** base: borrower, item, issue/due/return date, status `ACTIVE/RETURNED/LOST/DAMAGED`. |
| `BookLoan` | Loan for `Book` items, max 14 days. |
| `EquipmentLoan` | Loan for everything else, max 7 days. |
| `FileStorage` | Reads and writes the four data files. |
| `InvalidLoanException` | Custom checked exception for rule violations. |
| `ItemUnavailableException` | Subclass of the above for "item cannot be borrowed now". |

### Class hierarchy

```
Item                        Loan (abstract)                Exception
 \-- Book                    |-- BookLoan                   \-- InvalidLoanException
      \-- TextBook           \-- EquipmentLoan                    \-- ItemUnavailableException
```

- `Item -> Book -> TextBook` is **multilevel inheritance**. Each level adds one real field.
- `Loan` is abstract because a loan is always either a book loan or an equipment loan.
  `getMaxLoanDays()` and `getLoanType()` are abstract, so each subclass must define them.
- `LendingManager.createLoan()` chooses `BookLoan` if the item `instanceof Book`, else `EquipmentLoan`.

## 5. Where each syllabus concept is implemented

| Concept | Where |
|---------|-------|
| Classes, objects, references | Every class; `Main.addItem()` creates `Book`/`TextBook`/`Item` objects |
| Constructor overloading | `User` (3 constructors), `Item`, `Book`, `Loan`, `BorrowRequest` (new vs. load-from-file) |
| Method overloading | `LendingManager.sendRequest(int,int,int)` / `sendRequest(User,Item,int)`; `approveRequest(...)` and `returnItem(...)` with/without date; `Loan.markReturned()` / `markReturned(LocalDate)`; `Loan.isOverdue()` / `isOverdue(LocalDate)` |
| Objects as arguments / returned | `LendingManager.addItem(Item)`, `sendRequest(User, Item, int)`; `findUser()`, `approveRequest()` return `User`/`Loan` |
| Access modifiers, encapsulation | Private fields + getters in all model classes; `protected` constructors in `Loan` and `Book` |
| `this`, `static`, `final` | `this(...)` in `User(String)`; static `nextId` counters, `User.computeScore()`, `Loan.formatDate()`; `final` fields like `itemId`, constants like `User.LATE_PENALTY`, `BookLoan.MAX_DAYS` |
| Nested / inner class | `LendingManager.OverdueChecker` and `AlertPrinter` (inner classes using outer fields); nested enums `Item.Status`, `Loan.Status` |
| Arrays | `Item.CATEGORIES`, `Main.MENU_OPTIONS`, `LendingManager.countOutcomes()` (int[] of counts) |
| Wrapper classes / conversion | `Integer.parseInt` in `Main.readInt()`; `HashSet<Integer>` autoboxing in `OverdueChecker`; `(double)` cast for on-time rate in `getScoreReport()` |
| Recursion | `LendingManager.buildHistoryText(list, index)` |
| Inheritance, overriding | `Item.getDetails()` overridden in `Book` and `TextBook`; `Loan` abstract methods implemented in `BookLoan`/`EquipmentLoan` (`@Override`) |
| Runtime polymorphism | `Main.viewAvailableItems()` calls `item.getDetails()` on `Item` references; `Main.printLoanTable()` calls `loan.getLoanType()` on `Loan` references; `FileStorage.saveAll()` calls `item.getTypeCode()` |
| Abstract class | `Loan` |
| Exceptions | `throw`/`throws` in `LendingManager` and `Loan.markReturned()`; `try/catch` in `Main.runMenu()`, `readInt()`, `readOptionalDate()`; `try/catch/finally` in `Main.main()` (`finally` always runs `closeApplication()`) |
| File handling | `FileStorage` (`BufferedReader`, `BufferedWriter`, `Files`) |
| Threads | `LendingManager.startBackgroundThreads()` |
| `synchronized` | Almost every public `LendingManager` method |
| `wait()` / `notifyAll()` | `AlertPrinter.run()` waits; `postAlert()` calls `notifyAll()`; `OverdueChecker.run()` uses `wait(timeout)`; `shutdown()` calls `notifyAll()` |

## 6. File storage

- One record per line, fields separated by `|`. Real `|` and `\` characters inside names are escaped
  (`\|`, `\\`), and newlines become `\n`, so names with these characters still reload correctly.
- Dates are stored as `dd-MM-yyyy`; a missing return date is an empty field.
- Load order: users, items, requests, loans (later files refer to earlier records by ID).
- **First run:** missing files are simply treated as "no data".
- **Saving:** every change is auto-saved, and option 13 saves again on exit. Each file is written to
  a `.tmp` file first and then moved over the old one, so a crash mid-write does not destroy the old file.
- **Bad lines:** a corrupted line is skipped with a visible warning, and a `.bak` copy of that file
  is made so nothing is lost silently.
- **Restart safety:** an active loan always marks its item `BORROWED` on load, so restarting never
  makes a borrowed item available again.
- If the data folder cannot be read at all, the program exits *without* saving, to avoid overwriting
  your records with an empty set.

## 7. Concurrency

Threads (all started in `startBackgroundThreads()`):

1. **OverdueChecker** - every 15 seconds (and once at startup) it asks `getOverdueLoans()` and posts an
   alert for each newly overdue loan. It waits using `stopLock.wait(15000)` instead of `sleep()`,
   so it wakes up immediately on shutdown.
2. **AlertPrinter** - a consumer. When the alert queue is empty it calls `alertLock.wait()`.
   When someone calls `postAlert()` (new request, approval, overdue found) the message is added and
   `notifyAll()` wakes the printer. This is the `wait()/notifyAll()` request workflow.

Data safety:

- `approveRequest()` is `synchronized`: it checks that the item is `AVAILABLE` and creates the loan
  inside the same locked method. Two requests for one item cannot both succeed; the second gets an
  `ItemUnavailableException`. (I tested this with two real threads approving two requests for the same
  item, 200 rounds: exactly one approval each time.)
- Only shared lists are protected. `alreadyAnnounced` in `OverdueChecker` is used by that thread only,
  so it is not synchronized.
- The alert lock and data lock are never held at the same time by the printer thread, so no deadlock.
- Stopping: `shutdown()` sets a `volatile boolean running = false`, notifies the waiting threads and
  calls `join()`. Deprecated `suspend()`, `resume()` and `stop()` are **not** used.
  Modern alternatives: a volatile flag or `interrupt()` instead of `stop()`, and `wait()/notify()` or
  `java.util.concurrent` locks instead of `suspend()/resume()`.

Honest note: the menu itself runs on one thread, so in normal use only one person is typing. The
synchronization protects the shared data from the background threads and would keep it consistent
if more threads called the manager.

## 8. Reliability score

```
score = 100 - (5 x late returns) - (30 x lost items) - (15 x damaged items)
score is then kept between 0 and 100
```

- Late return: returned with a return date after the due date (-5 each).
- Lost: loan closed with a lost report (-30 each). Damaged: closed with a damaged report (-15 each).
- A loan is only ever counted once (late *or* lost *or* damaged).
- No deduction for pending/rejected requests, no bonus points, no social ratings.
- Option 11 lists every loan that caused a deduction, so the score can always be explained.
- Constants live in `User` (`LATE_PENALTY`, `LOST_PENALTY`, `DAMAGED_PENALTY`).

## 9. Example workflow

```
1  Register user      -> Amit, A-101       (ID 1)
1  Register user      -> Ravi, B-202       (ID 2)
2  Add item           -> owner 1, category 1 (Book), "Data Structures", author "Weiss"   (Item 1)
4  Send request       -> user 2, item 1, 5 days       (Request 1, PENDING)
6  Approve/reject     -> request 1, owner 1, A, Enter (Loan 1 created, due in 5 days)
7  Active loans       -> shows Loan 1 as Active
8  Return item        -> loan 1, user 2, Enter        ("Returned on time")
11 Reliability score  -> user 2                       (100 / 100)
13 Save and exit
```

## 10. Known limitations

- Console only, no login: anyone who knows a user ID can act as that user.
- Lost/damaged reports are accepted from the owner or borrower and counted immediately; there is no
  dispute process. A lost or damaged item stays unavailable (no "repaired" option yet).
- Overdue loans are not penalized until they are returned late.
- Other pending requests for an item are not auto-rejected when one is approved; the owner rejects them.
- The approve and return menus accept a past handover/return date (for recording handovers that
  already happened); future dates are rejected.
- Alert messages from background threads can appear in the middle of a prompt.
- Plain text files, not a database; two copies of the program running at once is not supported.
- `buildHistoryText()` is recursive, so an extremely long history (thousands of loans) could overflow the stack.

## 11. Viva questions

1. **Why is `Loan` abstract?** A loan is always a book loan or an equipment loan, never just "a loan". Abstract methods `getMaxLoanDays()` and `getLoanType()` force subclasses to define their own rules.
2. **Where is multilevel inheritance used?** `Item -> Book -> TextBook`.
3. **Give an example of runtime polymorphism.** `item.getDetails()` in `Main.viewAvailableItems()`: the variable type is `Item`, but the object may be `Book` or `TextBook`, so the overridden version runs.
4. **Difference between overloading and overriding?** Overloading: same name, different parameters, same class (`sendRequest`). Overriding: subclass replaces a superclass method with the same signature (`getDetails`).
5. **Why do some constructors take an ID?** One creates a new object and takes the next ID from a static counter; the other rebuilds an object from the file with its saved ID and moves the counter forward.
6. **What do `static` and `final` do here?** `nextId` is shared by all objects of a class; `final` fields like `itemId` cannot change after construction and constants like `LATE_PENALTY` cannot be edited.
7. **Which exceptions did you create and why?** `InvalidLoanException` for broken rules, and `ItemUnavailableException` extending it for items that can't be borrowed; `Main.runMenu()` catches them and prints the message.
8. **`throw` vs `throws`?** `throw` actually raises an exception (`throw new InvalidLoanException(...)`); `throws` in a method signature declares that it may be raised.
9. **What does `finally` do in `main()`?** It always runs `closeApplication()`, so threads are stopped and data is saved even on normal exit or when the input ends.
10. **How is data loaded after restart?** `FileStorage.loadAll()` reads users, items, requests, loans in that order and rebuilds objects using IDs; then `LendingManager.loadData()` re-marks items of active loans as borrowed.
11. **How do you avoid corrupting files?** Write to a `.tmp` file and then replace the real file; bad lines are skipped with a warning and the file is backed up as `.bak`.
12. **Which threads exist and what do they do?** `main` thread (menu), `OverdueChecker` (finds overdue loans periodically), `AlertPrinter` (prints queued alerts).
13. **Explain `wait()` and `notifyAll()` in your project.** `AlertPrinter` waits while the alert queue is empty. `postAlert()` adds a message and calls `notifyAll()`, which wakes it to print. Both happen inside `synchronized (alertLock)`.
14. **Why must `wait()` be inside `synchronized`?** The thread must own the object's monitor; `wait()` releases it while sleeping. Otherwise Java throws `IllegalMonitorStateException`.
15. **How do you stop the background threads safely?** Set the volatile `running` flag to false, `notifyAll()` the locks so waiting threads wake up, then `join()`. `stop()` is deprecated because it can leave shared data half-updated.
16. **How is double-lending prevented?** `approveRequest()` is `synchronized` and checks item availability and creates the loan in the same call, so the second approval sees `BORROWED` and fails.
17. **Why use `wait(timeout)` instead of `Thread.sleep()` in `OverdueChecker`?** It can be woken early by `notifyAll()` on shutdown, so the program does not hang waiting for a sleep to finish.
18. **How is overdue decided?** `Loan.isOverdue(today)`: status is `ACTIVE` and today is after the due date. Nothing is stored, so it can't go out of date.
19. **Why aren't lost/damaged items "returned"?** They get their own `Loan.Status` and a reason, the item status becomes `LOST`/`DAMAGED`, and `markReturned()` refuses non-active loans.
20. **Where did you use recursion, and is it appropriate?** `buildHistoryText()` prints a history list one loan at a time. It is simple and lists are short, but a loop would be needed for huge lists.
21. **Where do wrapper classes appear?** `Integer.parseInt()` for console input and `HashSet<Integer>` (autoboxing of loan IDs) in `OverdueChecker`.
22. **Why use an inner class for the threads?** `OverdueChecker` and `AlertPrinter` need direct access to the manager's private fields and methods, so they are inner (non-static) classes.
