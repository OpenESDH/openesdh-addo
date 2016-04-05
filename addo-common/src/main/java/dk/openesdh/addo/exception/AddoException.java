package dk.openesdh.addo.exception;

public class AddoException extends Exception {

    private static final long serialVersionUID = -1;

    private final boolean domainException;

    public AddoException(Throwable e) {
        super(e.getClass().getSimpleName() + ": " + e.getMessage());
        domainException = false;
        this.setStackTrace(e.getStackTrace());
    }

    public AddoException(String message) {
        super(message);
        domainException = true;
    }

    public boolean isDomainException() {
        return domainException;
    }
}
