// MainActivity.java - Updated for your remote control app
package com.example.remotecontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "RemoteControl";
    private static final int WINDOWS_WIDTH = 1920;
    private static final int WINDOWS_HEIGHT = 1080;

    private static float lastX, lastY;

    private long lastSent = 0;


    // UI Components
    private LinearLayout serverSelector;
    private LinearLayout serverDropdown;
    private TextView tvServerName;
    private TextView statusText;
    private RecyclerView rvServers;
    private ImageView btnAddServer;
    private ImageView btnMute;
    private ImageView touchpad;
    private ImageView btnClick;
    private ImageView btnBack;
    private ImageView btnMenu;

    // WebSocket and Connection
    private OkHttpClient client;
    private WebSocket ws;
    private boolean isConnected = false;
    private boolean isMuted = false;

    // Server Management
    private ServerAdapter serverAdapter;
    private List<Computer> computerList;
    private boolean isDropdownVisible = false;
    private Computer currentComputer;

    // SharedPreferences
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "RemoteControlPrefs";
    private static final String KEY_COMPUTERS = "saved_computers";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initOkHttp();
        initViews();
        loadSavedComputers();
        setupClickListeners();
        setupTouchpad();
    }

    private void initOkHttp() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    private void initViews() {
        serverSelector = findViewById(R.id.server_selector);
        serverDropdown = findViewById(R.id.server_dropdown);
        tvServerName = findViewById(R.id.tv_server_name);
        statusText = findViewById(R.id.status_text);
        rvServers = findViewById(R.id.rv_servers);
        btnAddServer = findViewById(R.id.btn_add_server);
        btnMute = findViewById(R.id.btn_mute);
        touchpad = findViewById(R.id.touchpad);
        btnClick = findViewById(R.id.btn_click);
        btnBack = findViewById(R.id.btn_back);
        btnMenu = findViewById(R.id.btn_menu);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void loadSavedComputers() {
        computerList = new ArrayList<>();

        // Load from SharedPreferences
        String json = prefs.getString(KEY_COMPUTERS, "[]");
        try {
            Gson gson = new Gson();
            List<Computer> saved = gson.fromJson(json, new TypeToken<List<Computer>>(){}.getType());
            if (saved != null) {
                computerList.addAll(saved);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading saved computers", e);
        }

        // Setup RecyclerView
        serverAdapter = new ServerAdapter(computerList, this::onComputerSelected);
        rvServers.setLayoutManager(new LinearLayoutManager(this));
        rvServers.setAdapter(serverAdapter);
    }

    private void setupClickListeners() {
        // Server selector dropdown toggle
        serverSelector.setOnClickListener(v -> toggleServerDropdown());

        // Add server button
        btnAddServer.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddServerActivity.class);
            startActivityForResult(intent, 1001);
        });

        // Control buttons
        btnMute.setOnClickListener(v -> toggleMute());
        btnClick.setOnClickListener(v -> sendClick());
        btnBack.setOnClickListener(v -> sendCommand("BACK"));
        btnMenu.setOnClickListener(v -> sendCommand("MENU"));
    }

    private void setupTouchpad() {
        touchpad.setOnTouchListener(this::handleTouch);
        touchpad.setOnClickListener(v -> sendClick());
        touchpad.setOnLongClickListener(v -> {
            sendCommand("RIGHT_CLICK");
            return true;
        });
    }

    private boolean handleTouch(View v, MotionEvent event) {
        if (!isConnected || ws == null) return true;

        try {
            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    break;

                case MotionEvent.ACTION_MOVE:
                    long now = System.currentTimeMillis();
                    if (now - lastSent > 16) {

                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;

                        lastX = event.getX();
                        lastY = event.getY();

                        String message = String.format(
                                "{\"type\":\"MOVE\", \"dx\":%d, \"dy\":%d}",
                                (int) dx, (int) dy
                        );

                        ws.send(message);
                        lastSent = now;
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    // Optional: Reset or ignore
                    break;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling touch", e);
        }

        return true;
    }

    private void toggleServerDropdown() {
        if (isDropdownVisible) {
            serverDropdown.setVisibility(View.GONE);
            isDropdownVisible = false;
        } else {
            serverDropdown.setVisibility(View.VISIBLE);
            isDropdownVisible = true;
        }
    }

    private void toggleMute() {
        isMuted = !isMuted;
        if (isMuted) {
            btnMute.setImageResource(R.drawable.ic_volume_off);
            sendCommand("MUTE");
        } else {
            btnMute.setImageResource(R.drawable.ic_volume_on);
            sendCommand("UNMUTE");
        }
    }

    private void onComputerSelected(Computer computer) {
        tvServerName.setText(computer.getIp()); // show IP instead of name
        toggleServerDropdown();
        currentComputer = computer;
        connectToComputer(computer);
    }

    private void connectToComputer(Computer computer) {
        if (isConnected) {
            disconnect();
        }

        updateConnectionStatus("Connecting...", false);
        String url = computer.getWebSocketUrl();

        try {
            Request request = new Request.Builder().url(url).build();
            ws = client.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, okhttp3.Response response) {
                    runOnUiThread(() -> updateConnectionStatus("Connected, waiting for server...", false));
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    runOnUiThread(() -> {
                        String lower = text.toLowerCase();
                        if (lower.contains("enter password")) {
                            showPasswordDialog(webSocket);
                        } else if (lower.contains("success") || lower.contains("connected")) {
                            updateConnectionStatus("Authenticated ✅", true);
                            saveComputers();
                        } else if (lower.contains("failed")) {
                            updateConnectionStatus("Authentication failed ❌", false);
                            disconnect();
                        } else {
                            statusText.setText("Server: " + text);
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating WebSocket", e);
            updateConnectionStatus("Connection error", false);
        }
    }

    private void showPasswordDialog(WebSocket webSocket) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Enter Password");

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String password = input.getText().toString();
            currentComputer.setPassword(password);
            webSocket.send(password); // send raw password (matches Python server)
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }


    private void disconnect() {
        if (ws != null) {
            ws.close(1000, "User disconnect");
            ws = null;
        }
        updateConnectionStatus("Disconnected", false);
        if (currentComputer != null) {
            saveComputers();
        }
    }

    private void updateConnectionStatus(String status, boolean connected) {
        isConnected = connected;
        statusText.setText(status);
        statusText.setTextColor(connected ? 0xFF4CAF50 : 0xFF888888);

        // Update UI state
        btnClick.setEnabled(connected);
        btnBack.setEnabled(connected);
        btnMenu.setEnabled(connected);
        touchpad.setEnabled(connected);
        touchpad.setAlpha(connected ? 1.0f : 0.5f);

        // Update adapter
        if (serverAdapter != null) {
            serverAdapter.notifyDataSetChanged();
        }
    }

    private void sendClick() {
        if (!isConnected || ws == null) { showToast("Not connected"); return; }
        try { ws.send("{\"type\": \"CLICK\"}"); }
        catch (Exception e) { Log.e(TAG, "Error sending click", e); }
    }

    private void sendCommand(String cmd) {
        if (!isConnected || ws == null) { showToast("Not connected"); return; }
        try {
            String json = "{\"type\":\"" + cmd + "\"}";
            ws.send(json);
        } catch (Exception e) {
            Log.e(TAG, "sendCommand error", e);
        }
    }

    private String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            Log.e(TAG, "hashPassword error", e);
            return password;
        }
    }

    // ----- Persistence -----
    private void saveComputers() {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String json = gson.toJson(computerList);
            prefs.edit().putString(KEY_COMPUTERS, json).apply();
            if (serverAdapter != null) serverAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "saveComputers error", e);
        }
    }

    // Receive new server from AddServerActivity
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            String ip = data.getStringExtra("server_ip");  // ✅ correct key
            if (ip != null) {
                Computer c = new Computer(ip, ""); // password is optional for now
                computerList.add(c);
                saveComputers();
                // auto-connect
                onComputerSelected(c);
            }
        }
    }

    private void showToast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ws != null) ws.close(1000, "App closed");
        if (client != null) client.dispatcher().executorService().shutdown();
    }
}