package dk.openesdh.addo.model;

import java.io.Serializable;

public class AddoRecipient implements Serializable {

    private final String cpr;
    private final String email;
    private String address;
    private String name;
    private String phone;

    public AddoRecipient(String cpr, String email) {
        this.cpr = cpr;
        this.email = email;
    }

    public AddoRecipient(String cpr, String email, String address, String name, String phone) {
        this(cpr, email);
        this.address = address;
        this.name = name;
        this.phone = phone;
    }


    public String getCpr() {
        return cpr;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
