package com.example.myapplication;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements MQTTHelper.MQTTListener {
    
    private MQTTHelper mqttHelper;
    private MaterialButton btnConnect, btnOn, btnOff, btnSetTimer, btnAutoMode, btnClearTerminal;
    private TextView tvConnectionStatus, tvMotorStatus, tvTimerStatus, tvTerminal;
    private View statusIndicator;
    private TextInputEditText etHours, etMinutes;
    private MaterialCardView cardTimer;
    private Handler handler;
    private StringBuilder terminalLog = new StringBuilder();
    private static final int MAX_TERMINAL_LINES = 200;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        handler = new Handler(Looper.getMainLooper());
        
        // Khởi tạo MQTT Helper trước khi initializeViews()
        mqttHelper = new MQTTHelper(this, this);
        
        initializeViews();
        setupClickListeners();
        
        updateConnectionUI(false);
        addTerminalLog("Terminal đã sẵn sàng. Đang chờ dữ liệu...");
    }
    
    private void initializeViews() {
        btnConnect = findViewById(R.id.btnConnect);
        btnOn = findViewById(R.id.btnOn);
        btnOff = findViewById(R.id.btnOff);
        btnSetTimer = findViewById(R.id.btnSetTimer);
        btnAutoMode = findViewById(R.id.btnAutoMode);
        btnClearTerminal = findViewById(R.id.btnClearTerminal);
        
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvMotorStatus = findViewById(R.id.tvMotorStatus);
        tvTimerStatus = findViewById(R.id.tvTimerStatus);
        tvTerminal = findViewById(R.id.tvTerminal);
        statusIndicator = findViewById(R.id.statusIndicator);
        
        etHours = findViewById(R.id.etHours);
        etMinutes = findViewById(R.id.etMinutes);
        cardTimer = findViewById(R.id.cardTimer);
    }
    
    private void setupClickListeners() {
        btnConnect.setOnClickListener(v -> {
            if (mqttHelper.isConnected()) {
                mqttHelper.disconnect();
            } else {
                mqttHelper.connect();
            }
        });
        
        btnOn.setOnClickListener(v -> {
            // Chỉ hiển thị phần hẹn giờ, KHÔNG gửi lệnh
            if (cardTimer.getVisibility() == View.GONE) {
                cardTimer.setVisibility(View.VISIBLE);
                // Focus vào ô nhập giờ để người dùng có thể nhập ngay
                cardTimer.post(() -> {
                    etHours.requestFocus();
                });
                Toast.makeText(this, "Nhập thời gian hẹn giờ và bấm ĐẶT GIỜ", Toast.LENGTH_SHORT).show();
            } else {
                // Nếu đã hiển thị rồi thì ẩn đi
                cardTimer.setVisibility(View.GONE);
            }
        });
        
        btnOff.setOnClickListener(v -> {
            if (checkConnection()) {
                // Ẩn phần hẹn giờ khi bấm TẮT
                cardTimer.setVisibility(View.GONE);
                // Gửi lệnh hủy hẹn giờ (TIMER:0:0) để về chế độ tự động mặc định
                mqttHelper.publishCommand("TIMER:0:0");
                addTerminalLog("📤 Đã gửi lệnh: TIMER:0:0 (Hủy hẹn giờ)");
                Toast.makeText(this, "Đã hủy hẹn giờ - Về chế độ tự động", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnSetTimer.setOnClickListener(v -> {
            if (checkConnection()) {
                String hoursStr = etHours.getText().toString();
                String minutesStr = etMinutes.getText().toString();
                
                if (TextUtils.isEmpty(hoursStr)) hoursStr = "0";
                if (TextUtils.isEmpty(minutesStr)) minutesStr = "0";
                
                try {
                    int hours = Integer.parseInt(hoursStr);
                    int minutes = Integer.parseInt(minutesStr);
                    
                    if (hours < 0 || minutes < 0 || minutes >= 60) {
                        Toast.makeText(this, "Giờ và phút không hợp lệ!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (hours == 0 && minutes == 0) {
                        Toast.makeText(this, "Vui lòng nhập thời gian lớn hơn 0!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    String command = "TIMER:" + hours + ":" + minutes;
                    mqttHelper.publishCommand(command);
                    addTerminalLog("📤 Đã gửi lệnh: " + command);
                    Toast.makeText(this, "Đã đặt hẹn giờ: " + hours + "h " + minutes + "m", Toast.LENGTH_SHORT).show();
                    
                    // Ẩn phần hẹn giờ sau khi đặt
                    cardTimer.setVisibility(View.GONE);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Vui lòng nhập số hợp lệ!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        btnAutoMode.setOnClickListener(v -> {
            if (checkConnection()) {
                // Ẩn phần hẹn giờ khi chuyển sang chế độ tự động
                cardTimer.setVisibility(View.GONE);
                mqttHelper.publishCommand("AUTO");
                addTerminalLog("📤 Đã gửi lệnh: AUTO");
                Toast.makeText(this, "Đã chuyển sang chế độ tự động", Toast.LENGTH_SHORT).show();
            }
        });
        
        btnClearTerminal.setOnClickListener(v -> {
            terminalLog.setLength(0);
            tvTerminal.setText("");
            addTerminalLog("Log đã được xóa");
        });
        
        // Thêm nút test kết nối ESP32 (double tap vào trạng thái kết nối)
        tvConnectionStatus.setOnClickListener(v -> {
            if (mqttHelper != null && mqttHelper.isConnected()) {
                addTerminalLog("🔍 Đang test kết nối ESP32...");
                mqttHelper.publishCommand("STATUS");
                // Gửi test message vào topic khác để xem ESP32 có online không
                mqttHelper.publishTestMessage();
            }
        });
    }
    
    private boolean checkConnection() {
        if (!mqttHelper.isConnected()) {
            Toast.makeText(this, "Chưa kết nối MQTT! Vui lòng kết nối trước.", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
    
    private void updateConnectionUI(boolean connected) {
        handler.post(() -> {
            if (connected) {
                tvConnectionStatus.setText("Đã kết nối");
                tvConnectionStatus.setTextColor(Color.parseColor("#4CAF50"));
                statusIndicator.setBackgroundColor(Color.parseColor("#4CAF50"));
                btnConnect.setText("Ngắt Kết Nối");
                btnOn.setEnabled(true);
                btnOff.setEnabled(true);
                btnSetTimer.setEnabled(true);
                btnAutoMode.setEnabled(true);
            } else {
                tvConnectionStatus.setText("Chưa kết nối");
                tvConnectionStatus.setTextColor(Color.parseColor("#F44336"));
                statusIndicator.setBackgroundColor(Color.parseColor("#F44336"));
                btnConnect.setText("Kết Nối");
                btnOn.setEnabled(false);
                btnOff.setEnabled(false);
                btnSetTimer.setEnabled(false);
                btnAutoMode.setEnabled(false);
            }
        });
    }
    
    private void parseStatusMessage(String message) {
        handler.post(() -> {
            if (message.startsWith("STATUS:")) {
                // Parse status message: STATUS:ON|MODE:TIMER|WIFI:CONNECTED|REMAINING:30m0s|TIME:...
                String[] parts = message.split("\\|");
                String mode = "AUTO";
                for (String part : parts) {
                    if (part.startsWith("STATUS:")) {
                        String status = part.substring(7);
                        tvMotorStatus.setText("Motor: " + (status.equals("ON") ? "ĐANG CHẠY" : "ĐÃ TẮT"));
                        tvMotorStatus.setTextColor(status.equals("ON") ? 
                            Color.parseColor("#4CAF50") : Color.parseColor("#757575"));
                    } else if (part.startsWith("MODE:")) {
                        mode = part.substring(5);
                        if (mode.equals("TIMER")) {
                            tvTimerStatus.setTextColor(Color.parseColor("#FF9800"));
                        } else {
                            tvTimerStatus.setTextColor(Color.parseColor("#2196F3"));
                        }
                    } else if (part.startsWith("REMAINING:")) {
                        String remaining = part.substring(10);
                        tvTimerStatus.setText("⏱ Hẹn giờ còn: " + remaining + " - Cảm biến tự động đã tắt");
                        tvTimerStatus.setTextColor(Color.parseColor("#FF9800"));
                        // Cập nhật trạng thái motor khi ở chế độ hẹn giờ
                        if (mode.equals("TIMER")) {
                            tvMotorStatus.setText("Motor: ĐANG CHẠY (Hẹn giờ)");
                            tvMotorStatus.setTextColor(Color.parseColor("#FF9800"));
                        }
                    }
                }
                // Hiển thị chế độ
                if (!message.contains("REMAINING:")) {
                    if (mode.equals("TIMER")) {
                        tvTimerStatus.setText("⏱ Chế độ: Hẹn giờ - Cảm biến tự động đã tắt");
                        tvTimerStatus.setTextColor(Color.parseColor("#FF9800"));
                        // Nếu motor đang chạy và ở chế độ TIMER
                        if (message.contains("STATUS:ON")) {
                            tvMotorStatus.setText("Motor: ĐANG CHẠY (Hẹn giờ)");
                            tvMotorStatus.setTextColor(Color.parseColor("#FF9800"));
                        }
                    } else {
                        tvTimerStatus.setText("🔍 Chế độ: Cảm biến tự động");
                        tvTimerStatus.setTextColor(Color.parseColor("#2196F3"));
                    }
                }
            } else if (message.equals("MOTOR_ON")) {
                tvMotorStatus.setText("Motor: ĐANG CHẠY");
                tvMotorStatus.setTextColor(Color.parseColor("#4CAF50"));
            } else if (message.equals("MOTOR_OFF")) {
                tvMotorStatus.setText("Motor: ĐÃ TẮT");
                tvMotorStatus.setTextColor(Color.parseColor("#757575"));
            } else if (message.startsWith("TIMER_SET:")) {
                String timerInfo = message.substring(10);
                tvTimerStatus.setText("⏱ Đã đặt hẹn giờ: " + timerInfo + " - Cảm biến tự động đã tắt");
                tvTimerStatus.setTextColor(Color.parseColor("#FF9800"));
                tvMotorStatus.setText("Motor: ĐANG CHẠY (Hẹn giờ)");
                tvMotorStatus.setTextColor(Color.parseColor("#FF9800"));
                // Ẩn phần hẹn giờ sau khi đặt
                cardTimer.setVisibility(View.GONE);
            } else if (message.startsWith("TIMER_RUNNING:")) {
                String remaining = message.substring(14);
                tvTimerStatus.setText("⏱ Hẹn giờ còn: " + remaining + " - Cảm biến tự động đã tắt");
                tvTimerStatus.setTextColor(Color.parseColor("#FF9800"));
                tvMotorStatus.setText("Motor: ĐANG CHẠY (Hẹn giờ)");
                tvMotorStatus.setTextColor(Color.parseColor("#FF9800"));
            } else if (message.equals("TIMER_OFF")) {
                tvTimerStatus.setText("✅ Hẹn giờ đã hết - Đã chuyển về chế độ cảm biến tự động");
                tvTimerStatus.setTextColor(Color.parseColor("#4CAF50"));
                tvMotorStatus.setText("Motor: ĐÃ TẮT");
                tvMotorStatus.setTextColor(Color.parseColor("#757575"));
                // Tự động chuyển về chế độ cảm biến
                handler.postDelayed(() -> {
                    tvTimerStatus.setText("🔍 Chế độ: Cảm biến tự động");
                    tvTimerStatus.setTextColor(Color.parseColor("#2196F3"));
                }, 3000);
            } else if (message.equals("TIMER_CANCELLED")) {
                tvTimerStatus.setText("❌ Đã hủy hẹn giờ");
                tvTimerStatus.setTextColor(Color.parseColor("#757575"));
                handler.postDelayed(() -> {
                    tvTimerStatus.setText("🔍 Chế độ: Cảm biến tự động");
                    tvTimerStatus.setTextColor(Color.parseColor("#2196F3"));
                }, 2000);
            } else if (message.equals("AUTO_ON")) {
                tvMotorStatus.setText("Motor: ĐANG CHẠY (Tự động)");
                tvMotorStatus.setTextColor(Color.parseColor("#4CAF50"));
            } else if (message.equals("AUTO_OFF")) {
                tvMotorStatus.setText("Motor: ĐÃ TẮT (Tự động)");
                tvMotorStatus.setTextColor(Color.parseColor("#757575"));
            } else if (message.equals("MODE_AUTO")) {
                tvTimerStatus.setText("🔍 Chế độ: Cảm biến tự động");
                tvTimerStatus.setTextColor(Color.parseColor("#2196F3"));
            }
        });
    }
    
    private void addTerminalLog(String message) {
        if (message == null) return;
        
        String timestamp = timeFormat.format(new Date());
        String logEntry = "[" + timestamp + "] " + message + "\n";
        
        terminalLog.append(logEntry);
        
        // Giới hạn số dòng log
        String[] lines = terminalLog.toString().split("\n");
        if (lines.length > MAX_TERMINAL_LINES) {
            StringBuilder newBuffer = new StringBuilder();
            int startIndex = lines.length - MAX_TERMINAL_LINES;
            for (int i = startIndex; i < lines.length; i++) {
                newBuffer.append(lines[i]).append("\n");
            }
            terminalLog = newBuffer;
        }
        
        // Cập nhật UI
        handler.post(() -> {
            tvTerminal.setText(terminalLog.toString());
            // Scroll xuống dưới
            tvTerminal.post(() -> {
                if (tvTerminal.getLayout() != null) {
                    int scrollAmount = tvTerminal.getLayout().getLineTop(tvTerminal.getLineCount()) - tvTerminal.getHeight();
                    if (scrollAmount > 0) {
                        tvTerminal.scrollTo(0, scrollAmount);
                    }
                }
            });
        });
    }
    
    @Override
    public void onMessageReceived(String topic, String message) {
        if (topic.equals(mqttHelper.getStatusTopic())) {
            parseStatusMessage(message);
        }
        addTerminalLog("📥 [" + topic + "] " + message);
    }
    
    @Override
    public void onConnectionStatusChanged(boolean connected) {
        updateConnectionUI(connected);
        if (connected && mqttHelper != null) {
            addTerminalLog("Client ID: " + mqttHelper.getClientId());
        }
    }
    
    @Override
    public void onLogMessage(String message) {
        addTerminalLog(message);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Giữ kết nối khi pause
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Kiểm tra và reconnect nếu cần
        if (mqttHelper != null && !mqttHelper.isConnected()) {
            mqttHelper.connect();
        }
    }
}
