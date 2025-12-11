# Hướng Dẫn Điều Khiển Qua MQTT

## 📱 Sử Dụng App Android

### 1. Tải App MQTT Client

Các app phổ biến:
- **MQTT Dashboard** (Google Play)
- **MQTT Tool** (Google Play)
- **IoT MQTT Panel** (Google Play)

### 2. Cấu Hình Kết Nối MQTT

1. Mở app và tạo kết nối mới
2. Nhập thông tin:
   - **Broker**: `broker.hivemq.com` (hoặc broker bạn đang dùng)
   - **Port**: `1883`
   - **Protocol**: MQTT
   - **Client ID**: Bất kỳ tên nào (ví dụ: `Phone_001`)

3. Kết nối đến broker

### 3. Subscribe Topic Nhận Trạng Thái

- **Topic**: `maybom/status`
- **QoS**: 0 hoặc 1

Bạn sẽ nhận được các thông báo trạng thái từ ESP32 tại đây.

### 4. Publish Lệnh Điều Khiển

- **Topic**: `maybom/command`
- **QoS**: 0 hoặc 1

#### Các Lệnh Có Thể Gửi:

| Lệnh | Mô Tả | Ví Dụ |
|------|-------|-------|
| `TIMER:h:m` | Hẹn giờ chạy trong h giờ m phút | `TIMER:1:30` = 1h30m |
| `ON` | Bật máy bơm ngay | `ON` |
| `OFF` | Tắt máy bơm ngay | `OFF` |
| `AUTO` | Chuyển về chế độ cảm biến tự động | `AUTO` |
| `STATUS` | Yêu cầu gửi trạng thái hiện tại | `STATUS` |

## 💻 Ví Dụ Code Android (Java/Kotlin)

Nếu bạn muốn tự tạo app Android, đây là ví dụ sử dụng thư viện **Eclipse Paho MQTT Client**:

### 1. Thêm Dependency (build.gradle)

```gradle
dependencies {
    implementation 'org.eclipse.paho:org.eclipse.paho.android.service:1.1.1'
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.2'
}
```

### 2. Code MQTT Client (Java)

```java
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;

public class MQTTController {
    private static final String BROKER = "tcp://broker.hivemq.com:1883";
    private static final String TOPIC_COMMAND = "maybom/command";
    private static final String TOPIC_STATUS = "maybom/status";
    private static final String CLIENT_ID = "AndroidApp_001";
    
    private MqttClient client;
    
    public void connect() {
        try {
            client = new MqttClient(BROKER, CLIENT_ID, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            
            client.connect(options);
            
            // Subscribe để nhận trạng thái
            client.subscribe(TOPIC_STATUS);
            
            // Set callback để xử lý tin nhắn nhận được
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    // Xử lý mất kết nối
                }
                
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    // Xử lý trạng thái nhận được
                    System.out.println("Nhận trạng thái: " + payload);
                }
                
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Gửi thành công
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Hàm gửi lệnh hẹn giờ
    public void setTimer(int hours, int minutes) {
        try {
            String command = "TIMER:" + hours + ":" + minutes;
            MqttMessage message = new MqttMessage(command.getBytes());
            message.setQos(1);
            client.publish(TOPIC_COMMAND, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Hàm bật máy bơm
    public void turnOn() {
        try {
            MqttMessage message = new MqttMessage("ON".getBytes());
            message.setQos(1);
            client.publish(TOPIC_COMMAND, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Hàm tắt máy bơm
    public void turnOff() {
        try {
            MqttMessage message = new MqttMessage("OFF".getBytes());
            message.setQos(1);
            client.publish(TOPIC_COMMAND, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Hàm chuyển về chế độ tự động
    public void setAutoMode() {
        try {
            MqttMessage message = new MqttMessage("AUTO".getBytes());
            message.setQos(1);
            client.publish(TOPIC_COMMAND, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Hàm yêu cầu trạng thái
    public void requestStatus() {
        try {
            MqttMessage message = new MqttMessage("STATUS".getBytes());
            message.setQos(1);
            client.publish(TOPIC_COMMAND, message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### 3. Sử Dụng Trong Activity

```java
public class MainActivity extends AppCompatActivity {
    private MQTTController mqttController;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mqttController = new MQTTController();
        mqttController.connect();
        
        // Ví dụ: Bấm nút để hẹn giờ 1 giờ 30 phút
        Button btnTimer = findViewById(R.id.btnTimer);
        btnTimer.setOnClickListener(v -> {
            mqttController.setTimer(1, 30);
        });
        
        // Ví dụ: Bấm nút để bật
        Button btnOn = findViewById(R.id.btnOn);
        btnOn.setOnClickListener(v -> {
            mqttController.turnOn();
        });
        
        // Ví dụ: Bấm nút để tắt
        Button btnOff = findViewById(R.id.btnOff);
        btnOff.setOnClickListener(v -> {
            mqttController.turnOff();
        });
    }
}
```

## 🌐 Sử Dụng Web Browser (Test Nhanh)

Bạn cũng có thể test bằng các trang web MQTT client:

1. **HiveMQ WebSocket Client**: https://www.hivemq.com/demos/websocket-client/
   - Kết nối đến: `broker.hivemq.com`, Port: `8000` (WebSocket)
   - Subscribe: `maybom/status`
   - Publish: `maybom/command` với message là lệnh

2. **MQTT.fx** (Desktop App): https://mqttfx.jensd.de/
   - Tải về và cài đặt
   - Cấu hình tương tự như app Android

## 📊 Ví Dụ Lệnh

### Hẹn giờ chạy 2 giờ 15 phút:
```
Topic: maybom/command
Message: TIMER:2:15
```

### Bật ngay:
```
Topic: maybom/command
Message: ON
```

### Tắt ngay:
```
Topic: maybom/command
Message: OFF
```

### Chuyển về tự động:
```
Topic: maybom/command
Message: AUTO
```

## 🔔 Nhận Trạng Thái

Khi subscribe vào `maybom/status`, bạn sẽ nhận được các thông báo như:

- `MOTOR_ON` - Motor đã bật
- `MOTOR_OFF` - Motor đã tắt
- `TIMER_SET:1h30m` - Đã đặt hẹn giờ
- `TIMER_RUNNING:45m30s` - Đang chạy, còn lại 45m30s
- `TIMER_OFF` - Hẹn giờ đã hết
- `AUTO_ON` - Bật tự động từ cảm biến
- `AUTO_OFF` - Tắt tự động từ cảm biến
- `STATUS:ON|MODE:TIMER|WIFI:CONNECTED|REMAINING:30m0s|TIME:...` - Trạng thái chi tiết

## ⚠️ Lưu Ý

1. Đảm bảo ESP32 đã kết nối WiFi và MQTT thành công (xem Serial Monitor)
2. Topic phải chính xác: `maybom/command` và `maybom/status`
3. Message phải đúng định dạng (ví dụ: `TIMER:1:30` không có khoảng trắng)
4. Nếu dùng broker khác, nhớ cập nhật trong code ESP32

