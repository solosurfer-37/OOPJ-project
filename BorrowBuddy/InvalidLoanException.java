// Custom checked exception: jab koi lending rule todta hai (galat ID, invalid request, double return, etc.)
public class InvalidLoanException extends Exception {

    public InvalidLoanException(String message) {
        super(message);
    }
}
