package com.example.myapplication;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TerminalActivity extends AppCompatActivity {
    
    private static TextView tvTerminal;
    private static StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG_LINES = 500;
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private MQTTHelper mqttHelper;
    
    private MaterialButton btnClearTerminal, btnRequestStatus;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        
        Toolbar toolbar = findViewById(R.id.toolbar_terminal);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Terminal - Log");
        }
        
        tvTerminal = findViewById(R.id.tvTerminal);
        tvTerminal.setMovementMethod(new ScrollingMovementMethod());
        
        btnClearTerminal = findViewById(R.id.btnClearTerminal);
        btnRequestStatus = findViewById(R.id.btnRequestStatus);
        
        btnClearTerminal.setOnClickListener(v -> {
            clearLog();
            Toast.makeText(this, "Đã xóa log", Toast.LENGTH_SHORT).show();
        });
        
        btnRequestStatus.setOnClickListener(v -> {
            if (mqttHelper != null && mqttHelper.isConnected()) {
                mqttHelper.publishCommand("STATUS");
                addLog("📤 Đã gửi yêu cầu trạng thái");
            } else {
                Toast.makeText(this, "Chưa kết nối MQTT!", Toast.LENGTH_SHORT).show();
                addLog("✗ Chưa kết nối MQTT, không thể gửi lệnh");
            }
        });
        
        // Khởi tạo MQTT Helper
        mqttHelper = new MQTTHelper(this, new MQTTHelper.MQTTListener() {
            @Override
            public void onMessageReceived(String topic, String message) {
                addLog("📥 [" + topic + "] " + message);
            }
            
            @Override
            public void onConnectionStatusChanged(boolean connected) {
                addLog(connected ? "✓ Đã kết nối MQTT" : "✗ Mất kết nối MQTT");
            }
            
            @Override
            public void onLogMessage(String message) {
                addLog(message);
            }
        });
        
        // Thử kết nối nếu chưa kết nối
        if (!mqttHelper.isConnected()) {
            mqttHelper.connect();
        }
        
        // Hiển thị log hiện có
        if (logBuffer.length() > 0) {
            tvTerminal.setText(logBuffer.toString());
            scrollToBottom();
        } else {
            addLog("Terminal đã sẵn sàng. Đang chờ dữ liệu...");
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    public static void addLog(String message) {
        if (message == null) return;
        
        String timestamp = timeFormat.format(new Date());
        String logEntry = "[" + timestamp + "] " + message + "\n";
        
        logBuffer.append(logEntry);
        
        // Giới hạn số dòng log để tránh quá tải bộ nhớ
        String[] lines = logBuffer.toString().split("\n");
        if (lines.length > MAX_LOG_LINES) {
            StringBuilder newBuffer = new StringBuilder();
            int startIndex = lines.length - MAX_LOG_LINES;
            for (int i = startIndex; i < lines.length; i++) {
                newBuffer.append(lines[i]).append("\n");
            }
            logBuffer = newBuffer;
        }
        
        // Cập nhật UI nếu activity đang hiển thị
        if (tvTerminal != null) {
            tvTerminal.post(() -> {
                tvTerminal.setText(logBuffer.toString());
                scrollToBottom();
            });
        }
    }
    
    private static void scrollToBottom() {
        if (tvTerminal != null) {
            int scrollAmount = tvTerminal.getLayout().getLineTop(tvTerminal.getLineCount()) - tvTerminal.getHeight();
            if (scrollAmount > 0) {
                tvTerminal.scrollTo(0, scrollAmount);
            } else {
                tvTerminal.scrollTo(0, 0);
            }
        }
    }
    
    public static void clearLog() {
        logBuffer.setLength(0);
        if (tvTerminal != null) {
            tvTerminal.post(() -> {
                tvTerminal.setText("");
                addLog("Log đã được xóa");
            });
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Không xóa tvTerminal static để có thể tiếp tục nhận log
    }
}

