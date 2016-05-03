package com.ugcleague.ops.web.dto;

import java.io.Serializable;

public class ConsoleMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String message;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "ConsoleMessage [user=" + username + ", message=" + message + "]";
    }

}
