package dk.openesdh.addo.common;

import java.io.Serializable;
import java.util.Date;

public class AddoGUID implements Serializable {

    private static final long FOUR_MIN_IN_MS = 4 * 60000;
    private final long createdDate;
    private String token;
    public final String username;
    public final String password;

    public AddoGUID(String username, String password) {
        createdDate = new Date().getTime();
        this.username = username;
        this.password = password;
    }

    public AddoGUID setToken(String token) {
        this.token = token;
        return this;
    }

    public String getToken() {
        return token;
    }

    /**
     * Check if GUID is expired (with 1min reserve). Actual expiration 5min.
     *
     * @return
     */
    public boolean isValid() {
        return new Date().getTime() - createdDate < FOUR_MIN_IN_MS;
    }
}
