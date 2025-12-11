package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MQTTHelper {
    private static final String TAG = "MQTTHelper";
    private static final String PREFS_NAME = "MQTT_PREFS";
    private static final String KEY_CLIENT_ID = "client_id";
    
    // Cấu hình MQTT - Có thể thay đổi theo broker của bạn
    private static final String MQTT_SERVER = "tcp://broker.hivemq.com:1883";
    private static final String TOPIC_COMMAND = "maybom/command";
    private static final String TOPIC_STATUS = "maybom/status";
    
    private MqttClient mqttClient;
    private Context context;
    private MQTTListener listener;
    private ExecutorService executorService;
    private Handler mainHandler;
    private boolean isConnected = false;
    private String clientId;
    private Handler statusRequestHandler;
    private Runnable statusRequestRunnable;
    
    public interface MQTTListener {
        void onMessageReceived(String topic, String message);
        void onConnectionStatusChanged(boolean connected);
        void onLogMessage(String message);
    }
    
    public MQTTHelper(Context context, MQTTListener listener) {
        this.context = context;
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.statusRequestHandler = new Handler(Looper.getMainLooper());
        
        // Lấy hoặc tạo CLIENT_ID cố định
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        clientId = prefs.getString(KEY_CLIENT_ID, null);
        if (clientId == null) {
            clientId = "AndroidApp_" + System.currentTimeMillis();
            prefs.edit().putString(KEY_CLIENT_ID, clientId).apply();
        }
        
        initializeMQTT();
    }
    
    private void initializeMQTT() {
        executorService.execute(() -> {
            try {
                mqttClient = new MqttClient(MQTT_SERVER, clientId, new MemoryPersistence());
                mqttClient.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        isConnected = false;
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onConnectionStatusChanged(false);
                                listener.onLogMessage("✗ Mất kết nối MQTT: " + (cause != null ? cause.getMessage() : "Unknown"));
                            }
                        });
                        Log.e(TAG, "Connection lost", cause);
                        
                        // Tự động reconnect
                        reconnect();
                    }
                    
                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        String payload = new String(message.getPayload());
                        Log.d(TAG, "Message received from topic: " + topic + " -> " + payload);
                        
                        // Bỏ qua message từ topic command (đó là echo của chính app)
                        if (topic.equals(TOPIC_COMMAND)) {
                            Log.d(TAG, "Ignoring echo from command topic");
                            return;
                        }
                        
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onMessageReceived(topic, payload);
                                listener.onLogMessage("📥 Nhận: [" + topic + "] " + payload);
                                
                                // Nếu nhận được từ status topic, đó là từ ESP32
                                if (topic.equals(TOPIC_STATUS)) {
                                    listener.onLogMessage("✓ Đã nhận status từ ESP32!");
                                }
                            }
                        });
                    }
                    
                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        Log.d(TAG, "Message delivered");
                    }
                });
            } catch (MqttException e) {
                Log.e(TAG, "Error initializing MQTT client", e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onLogMessage("✗ Lỗi khởi tạo MQTT: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    public void connect() {
        if (isConnected) {
            return;
        }
        
        executorService.execute(() -> {
            try {
                if (mqttClient == null) {
                    initializeMQTT();
                    Thread.sleep(500); // Đợi khởi tạo xong
                }
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onLogMessage("🔄 Đang kết nối MQTT...");
                    }
                });
                
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(false);
                options.setConnectionTimeout(10);
                options.setKeepAliveInterval(60);
                
                mqttClient.connect(options);
                isConnected = true;
                
                // Subscribe vào topic status
                mqttClient.subscribe(TOPIC_STATUS, 0);
                
                // Subscribe vào topic command để debug (xem có message nào không)
                mqttClient.subscribe(TOPIC_COMMAND, 0);
                
                // Subscribe vào wildcard để xem tất cả messages
                try {
                    mqttClient.subscribe("maybom/#", 0);
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onLogMessage("✓ Đã subscribe wildcard: maybom/#");
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to subscribe wildcard", e);
                }
                
                // Tự động request status sau khi kết nối
                Thread.sleep(1000); // Đợi subscribe xong
                requestStatus();
                
                // Bắt đầu periodic request status mỗi 5 giây
                startPeriodicStatusRequest();
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionStatusChanged(true);
                        listener.onLogMessage("✓ Đã kết nối MQTT thành công!");
                        listener.onLogMessage("✓ Đã đăng ký topic: " + TOPIC_STATUS);
                        listener.onLogMessage("✓ Đã gửi yêu cầu trạng thái");
                    }
                });
                
                Log.d(TAG, "Connected to MQTT");
            } catch (MqttException e) {
                isConnected = false;
                Log.e(TAG, "Failed to connect", e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionStatusChanged(false);
                        listener.onLogMessage("✗ Lỗi kết nối MQTT: " + e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    private void reconnect() {
        executorService.execute(() -> {
            int retryCount = 0;
            while (!isConnected && retryCount < 5) {
                try {
                    Thread.sleep(5000); // Đợi 5 giây trước khi reconnect
                    if (mqttClient != null && !mqttClient.isConnected()) {
                        MqttConnectOptions options = new MqttConnectOptions();
                        options.setAutomaticReconnect(true);
                        options.setCleanSession(false);
                        options.setConnectionTimeout(10);
                        options.setKeepAliveInterval(60);
                        
                        mqttClient.connect(options);
                        isConnected = true;
                        
                        // Subscribe lại
                        mqttClient.subscribe(TOPIC_STATUS, 0);
                        mqttClient.subscribe(TOPIC_COMMAND, 0);
                        try {
                            mqttClient.subscribe("maybom/#", 0);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to subscribe wildcard on reconnect", e);
                        }
                        
                        // Request status sau khi reconnect
                        Thread.sleep(1000);
                        requestStatus();
                        startPeriodicStatusRequest();
                        
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onConnectionStatusChanged(true);
                                listener.onLogMessage("✓ Đã kết nối lại MQTT thành công!");
                            }
                        });
                        
                        Log.d(TAG, "Reconnected to MQTT");
                        break;
                    }
                } catch (Exception e) {
                    retryCount++;
                    Log.e(TAG, "Reconnect attempt " + retryCount + " failed", e);
                    if (retryCount >= 5) {
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onLogMessage("✗ Không thể kết nối lại sau 5 lần thử");
                            }
                        });
                    }
                }
            }
        });
    }
    
    public void publishCommand(String command) {
        if (!isConnected || mqttClient == null) {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onLogMessage("✗ Chưa kết nối MQTT, không thể gửi lệnh");
                }
            });
            return;
        }
        
        executorService.execute(() -> {
            try {
                MqttMessage message = new MqttMessage();
                message.setPayload(command.getBytes());
                message.setQos(0);
                message.setRetained(false);
                
                mqttClient.publish(TOPIC_COMMAND, message);
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onLogMessage("📤 Đã gửi: " + command);
                    }
                });
                
                Log.d(TAG, "Published: " + command);
            } catch (MqttException e) {
                Log.e(TAG, "Failed to publish", e);
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onLogMessage("✗ Lỗi gửi lệnh: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    private void requestStatus() {
        publishCommand("STATUS");
    }
    
    private void startPeriodicStatusRequest() {
        // Dừng periodic request cũ nếu có
        stopPeriodicStatusRequest();
        
        // Tạo periodic request mới - mỗi 5 giây
        statusRequestRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected && mqttClient != null && mqttClient.isConnected()) {
                    requestStatus();
                    // Lên lịch request tiếp theo sau 5 giây
                    statusRequestHandler.postDelayed(this, 5000);
                }
            }
        };
        
        // Bắt đầu request sau 2 giây đầu tiên
        statusRequestHandler.postDelayed(statusRequestRunnable, 2000);
    }
    
    private void stopPeriodicStatusRequest() {
        if (statusRequestRunnable != null) {
            statusRequestHandler.removeCallbacks(statusRequestRunnable);
            statusRequestRunnable = null;
        }
    }
    
    public void disconnect() {
        // Dừng periodic request
        stopPeriodicStatusRequest();
        
        executorService.execute(() -> {
            try {
                if (mqttClient != null && mqttClient.isConnected()) {
                    mqttClient.disconnect();
                    isConnected = false;
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onConnectionStatusChanged(false);
                            listener.onLogMessage("✓ Đã ngắt kết nối MQTT");
                        }
                    });
                    Log.d(TAG, "Disconnected");
                }
            } catch (MqttException e) {
                Log.e(TAG, "Failed to disconnect", e);
            }
        });
    }
    
    public boolean isConnected() {
        return isConnected && mqttClient != null && mqttClient.isConnected();
    }
    
    public String getServer() {
        return MQTT_SERVER;
    }
    
    public String getStatusTopic() {
        return TOPIC_STATUS;
    }
    
    public String getCommandTopic() {
        return TOPIC_COMMAND;
    }
    
    public void destroy() {
        stopPeriodicStatusRequest();
        disconnect();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public void publishTestMessage() {
        // Gửi test message để kiểm tra ESP32 có online không
        publishCommand("STATUS");
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onLogMessage("🔍 Đã gửi test message, đợi phản hồi từ ESP32...");
                listener.onLogMessage("⚠️ Nếu không nhận được phản hồi trong 5 giây:");
                listener.onLogMessage("   1. Kiểm tra ESP32 có kết nối WiFi không");
                listener.onLogMessage("   2. Kiểm tra ESP32 có kết nối MQTT không");
                listener.onLogMessage("   3. Kiểm tra Serial Monitor của ESP32");
                listener.onLogMessage("   4. Xem ESP32 có hiển thị 'Nhan lenh tu MQTT: STATUS' không");
            }
        });
        
        // Sau 5 giây, kiểm tra xem có nhận được phản hồi không
        statusRequestHandler.postDelayed(() -> {
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onLogMessage("⚠️ CHƯA NHẬN ĐƯỢC PHẢN HỒI TỪ ESP32!");
                    listener.onLogMessage("   → ESP32 có thể chưa kết nối MQTT");
                    listener.onLogMessage("   → Hoặc ESP32 không subscribe vào maybom/command");
                    listener.onLogMessage("   → Hoặc ESP32 không gọi client.loop()");
                }
            });
        }, 5000);
    }
}
