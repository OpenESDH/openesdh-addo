package dk.openesdh.addo.exception;

import java.io.Serializable;

public class AddoException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = -1;

    public AddoException(Throwable e) {
        super(e.getClass().getSimpleName() + ": " + e.getMessage());
        this.setStackTrace(e.getStackTrace());
    }
}
