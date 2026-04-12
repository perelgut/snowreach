package com.yosnowmow.model;

/**
 * Embedded address type used in user documents (Worker.baseAddress) and
 * job documents (propertyAddress).
 *
 * Matches the Firestore embedded {@code Address} schema from spec §3.1.
 * The full address is captured on input; structured fields are parsed
 * server-side or from the geocoding response (P1-07).
 */
public class Address {

    private String streetNumber;
    private String street;
    private String city;

    /** Always "ON" for Ontario — enforced at the API layer. */
    private String province;

    /** Postal code, e.g. "M8Y 2B3". */
    private String postalCode;

    /** Full address as the user typed it — preserved verbatim. */
    private String fullText;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Required by Firestore deserialisation. */
    public Address() {}

    /** Convenience constructor used when only the raw text is available at intake. */
    public Address(String fullText) {
        this.fullText = fullText;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getStreetNumber() { return streetNumber; }
    public void setStreetNumber(String streetNumber) { this.streetNumber = streetNumber; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
}
