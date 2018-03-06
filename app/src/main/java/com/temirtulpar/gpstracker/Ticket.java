package com.temirtulpar.gpstracker;

public class Ticket {
    private String ticketId;
    private String description;
    private String imei;

    public Ticket(String ticketId, String description, String imei) {
        this.ticketId = ticketId;
        this.description = description;
        this.imei = imei;
    }

    public Ticket() {}

    public String getTicketId() {
        return ticketId;
    }

    public String getDescription() {
        return description;
    }

    public String getImei() {
        return imei;
    }

    public void setTicketId(String value) {
        ticketId = value;
    }

    public void setDescription(String value) {
        description = value;
    }

    public void setImei(String value) {
        imei = value;
    }
}