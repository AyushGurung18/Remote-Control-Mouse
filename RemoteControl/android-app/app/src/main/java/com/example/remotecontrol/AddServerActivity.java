package com.example.remotecontrol;

import android.util.Log;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AddServerActivity extends AppCompatActivity {

    private EditText etServerIp;
    Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_server);

        etServerIp = findViewById(R.id.ip_input);

        btnSave = findViewById(R.id.connect_button);  // Updated to correct button ID

        btnSave.setOnClickListener(v -> {
            String ip = etServerIp.getText().toString().trim();
            if (ip.isEmpty()) {
                toast("Enter server IP");
                return;
            }

            // Optional: Add validation for the IP format
            if (!isValidIp(ip)) {
                toast("Invalid IP address");
                return;
            }

            // Pass only IP
            Intent resultIntent = new Intent();
            resultIntent.putExtra("server_ip", ip);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    // Helper method to show toast messages
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // Validate IP address format (simple regex check)
    private boolean isValidIp(String ip) {
        String regex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(regex);
    }
}