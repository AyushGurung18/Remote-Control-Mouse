package com.example.remotecontrol;

public class Computer {
    private String ip;        // IP only (no port)
    private String password;  // plain; we hash when sending
    private boolean connected;

    public Computer( String ip, String password) {
        this.ip = ip;
        this.password = password;
    }


    public String getIp() {
        return ip;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Build ws URL with default port 5000
    public String getWebSocketUrl() {
        // Accept ws://… if user ever stored that form; otherwise build it.
        if (ip.startsWith("ws://") || ip.startsWith("wss://")) return ip;
        return "ws://" + ip + ":5000";
    }

    public String getDisplayAddress() {
        return ip + ":5000";
    }
}
