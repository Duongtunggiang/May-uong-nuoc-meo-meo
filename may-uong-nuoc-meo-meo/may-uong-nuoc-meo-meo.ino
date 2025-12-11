#include <WiFi.h>
#include <PubSubClient.h>
#include <time.h>

// ============================================
// CẤU HÌNH WIFI VÀ MQTT
// ============================================
const char* ssid = "DuGo"; 
const char* password = "88888888";       

const char* mqtt_server = "broker.hivemq.com";
const int mqtt_port = 1883;
const char* mqtt_topic_command = "maybom/command";
const char* mqtt_topic_status = "maybom/status";
const char* mqtt_client_id = "ESP32_MayBom_001";

// ============================================
// CẤU HÌNH GPIO
// ============================================
// QUAN TRỌNG: Tránh dùng Strapping Pins (GPIO 2, 3, 8, 9) để tránh lỗi boot khi dùng nguồn ngoài
const int MOTOR_PIN = 4;  // GPIO 4 - Điều khiển Motor (ENB)
const int TRIG_PIN = 10;  // GPIO 10 - Cảm biến siêu âm Trig (an toàn)
const int ECHO_PIN = 1;   // GPIO 1 - Cảm biến siêu âm Echo (đổi từ GPIO 9 - strapping pin)

// IN3 & IN4 - Điều khiển hướng Motor (Phương án A)
// ĐÃ THAY ĐỔI: Từ GPIO 2,3 (JTAG/Strapping - có thể xung đột) sang GPIO 5,6 (an toàn hơn)
const int IN_PIN_3 = 5;   // GPIO 5 - IN3 (an toàn)
const int IN_PIN_4 = 6;   // GPIO 6 - IN4 (an toàn)
const int LED = 7;        // GPIO 7 - LED (đổi từ GPIO 8 - strapping pin)

// ============================================
// CẤU HÌNH CẢM BIẾN
// ============================================
const int DISTANCE_THRESHOLD = 20;
const long DURATION_TO_TRIGGER_MS = 200;

// ============================================
// CẤU HÌNH NTP (LẤY GIỜ)
// ============================================
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 7 * 3600;

// ============================================
// BIẾN TOÀN CỤC
// ============================================
WiFiClient espClient;
PubSubClient client(espClient);

bool isMotorOn = false;
bool isTimerMode = false; // true = che do hen gio (uu tien cao nhat)
bool wifiConnected = false;
bool mqttConnected = false;

// Quản lý hẹn giờ (dùng millis())
unsigned long motorStartTime_ms = 0;
unsigned long motorRunDuration_ms = 0;
unsigned long motorEndTime_ms = 0;

// Quản lý cảm biến siêu âm
unsigned long lastDistanceCheckTime_ms = 0;
const long DISTANCE_CHECK_INTERVAL_MS = 100;
unsigned long distanceStartTime_ms = 0;

// Quản lý reconnect
unsigned long lastWifiReconnectAttempt = 0;
const long WIFI_RECONNECT_INTERVAL_MS = 30000;
unsigned long lastMqttReconnectAttempt = 0;
const long MQTT_RECONNECT_INTERVAL_MS = 10000; // Tăng lên 10 giây để tránh reconnect quá nhanh

// Khai báo sớm
void publishStatus(const char* status);

// ============================================
// HÀM ĐIỀU KHIỂN MOTOR
// ============================================
void setMotor(bool state) {
  if (state == isMotorOn) return; 

  isMotorOn = state;
  if (state) {
    digitalWrite(MOTOR_PIN, HIGH);
    digitalWrite(LED, HIGH); // Bật LED khi motor chạy
    Serial.println(">>> MOTOR BAT!");
    publishStatus("MOTOR_ON");
  } else {
    digitalWrite(MOTOR_PIN, LOW);
    digitalWrite(LED, LOW); // Tắt LED khi motor dừng
    Serial.println("<<< MOTOR TAT!");
    publishStatus("MOTOR_OFF");
  }
}

// ... (Các hàm readDistance() không thay đổi) ...
long readDistance() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH);
  long distance_cm = duration * 0.034 / 2;

  return distance_cm;
}

// ============================================
// HÀM XỬ LÝ CẢM BIẾN SIÊU ÂM
// ============================================
void handleUltrasonicSensor() {
  // QUAN TRỌNG: Chỉ chạy khi KHÔNG ở chế độ hẹn giờ
  if (isTimerMode) {
    distanceStartTime_ms = 0; // Reset
    return;
  }

  unsigned long currentTime_ms = millis();

  if (currentTime_ms - lastDistanceCheckTime_ms >= DISTANCE_CHECK_INTERVAL_MS) {
    lastDistanceCheckTime_ms = currentTime_ms;

    long distance = readDistance();

    if (distance <= DISTANCE_THRESHOLD && distance > 0) {
      if (distanceStartTime_ms == 0) {
        distanceStartTime_ms = currentTime_ms;
        Serial.print("Phat hien vat can (");
        Serial.print(distance);
        Serial.println("cm).");
      }

      if (currentTime_ms - distanceStartTime_ms >= DURATION_TO_TRIGGER_MS) {
        if (!isMotorOn) {
          setMotor(true); // LED sẽ tự động bật trong hàm setMotor()
          Serial.println("Da BAT BOM theo che do tu dong cam bien!");
          publishStatus("AUTO_ON");
        }
      }
    } else {
      distanceStartTime_ms = 0;

      if (isMotorOn) {
        setMotor(false); // LED sẽ tự động tắt trong hàm setMotor()
        Serial.println("Vat can da roi di. TAT BOM.");
        publishStatus("AUTO_OFF");
      }
    }
  }
}

// ============================================
// HÀM XỬ LÝ HẸN GIỜ TẮT (Chế độ ưu tiên)
// ============================================
void handleTimer() {
  if (!isTimerMode || motorEndTime_ms == 0) {
    return;
  }

  unsigned long currentTime_ms = millis();

  if (currentTime_ms >= motorEndTime_ms) {
    // Hết thời gian hẹn giờ
    isTimerMode = false; // Tắt chế độ hẹn giờ TRƯỚC khi tắt motor
    motorEndTime_ms = 0;
    motorRunDuration_ms = 0;
    motorStartTime_ms = 0;
    
    // Đảm bảo motor tắt
    setMotor(false);
    
    Serial.println("✓ Hen gio da het. Motor TAT. Chuyen ve che do cam bien tu dong.");
    publishStatus("TIMER_OFF");
    
  } else {
    // Gửi thông tin thời gian còn lại định kỳ
    static unsigned long lastStatusUpdate = 0;
    if (currentTime_ms - lastStatusUpdate >= 60000) {
      lastStatusUpdate = currentTime_ms;
      unsigned long remaining_ms = motorEndTime_ms - currentTime_ms;
      unsigned long remaining_seconds = remaining_ms / 1000;
      unsigned long remaining_minutes = remaining_seconds / 60;
      remaining_seconds = remaining_seconds % 60;

      String statusMsg = "TIMER_RUNNING:" + String(remaining_minutes) + "m" + String(remaining_seconds) + "s";
      publishStatus(statusMsg.c_str());
    }
  }
}

// ============================================
// HÀM THIẾT LẬP HẸN GIỜ
// ============================================
void setTimer(int hours, int minutes) {
  // Nếu thời gian là 0:0, hủy hẹn giờ và về chế độ cảm biến tự động
  if (hours == 0 && minutes == 0) {
    isTimerMode = false;
    motorEndTime_ms = 0;
    motorRunDuration_ms = 0;
    motorStartTime_ms = 0;
    
    // Reset cảm biến để sẵn sàng hoạt động lại
    distanceStartTime_ms = 0;
    lastDistanceCheckTime_ms = 0;
    
    // Tắt motor và chuyển về chế độ cảm biến (LED sẽ tự động tắt trong setMotor())
    setMotor(false);
    
    Serial.println("✓ Da huy hen gio. Chuyen ve che do cam bien tu dong.");
    publishStatus("TIMER_CANCELLED");
    return;
  }

  // Chuyển sang chế độ hẹn giờ
  isTimerMode = true; 
  
  // Thiết lập thời gian
  motorRunDuration_ms = (unsigned long)hours * 3600UL * 1000UL + (unsigned long)minutes * 60UL * 1000UL;
  motorStartTime_ms = millis();
  motorEndTime_ms = motorStartTime_ms + motorRunDuration_ms;

  // Bật motor (LED sẽ tự động bật trong setMotor())
  setMotor(true);
  
  // Reset cảm biến khi vào chế độ hẹn giờ để tránh xung đột
  distanceStartTime_ms = 0;
  lastDistanceCheckTime_ms = 0;

  Serial.print("✓ Da hen gio BAT ");
  Serial.print(hours);
  Serial.print("h ");
  Serial.print(minutes);
  Serial.print("p. Tat sau: ");
  Serial.print(motorRunDuration_ms / 1000);
  Serial.println(" giay.");
  Serial.println("✓ Che do hen gio da kich hoat. Cam bien bi vo hieu hoa.");

  String statusMsg = "TIMER_SET:" + String(hours) + "h" + String(minutes) + "m";
  publishStatus(statusMsg.c_str());
}

// ... (Các hàm connectWiFi(), callback(), sendCurrentStatus(), publishStatus() không thay đổi) ...
void connectWiFi() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    Serial.println("WiFi da ket noi san.");
    return;
  }

  Serial.print("Dang ket noi WiFi: ");
  Serial.print(ssid);
  Serial.print(" ...");

  // Reset WiFi module để đảm bảo khởi động sạch khi dùng nguồn ngoài
  WiFi.disconnect(true);
  delay(100);
  WiFi.mode(WIFI_STA);
  delay(100);
  WiFi.begin(ssid, password);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {  // Tăng số lần thử từ 20 lên 30
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    Serial.println("\n✓ Da ket noi WiFi thanh cong!");
    Serial.print("Dia chi IP: ");
    Serial.println(WiFi.localIP());

    Serial.println("Dang dong bo thoi gian NTP...");
    configTime(gmtOffset_sec, 0, ntpServer);
    Serial.println("NTP da duoc cau hinh, se dong bo sau.");
  } else {
    wifiConnected = false;
    Serial.println("\n✗ Khong the ket noi WiFi!");
    Serial.println("Kiem tra ten WiFi va mat khau.");
  }
}

void callback(char* topic, byte* payload, unsigned int length) {
  String message = "";
  for (int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.print("📥 Nhan lenh tu MQTT [");
  Serial.print(topic);
  Serial.print("]: ");
  Serial.println(message);

  if (message.startsWith("TIMER:")) {
    // TIMER:h:m (ví dụ: TIMER:0:30)
    // Tìm vị trí dấu : đầu tiên sau "TIMER:"
    int colonIndex1 = message.indexOf(':', 6); // Tìm dấu : sau "TIMER:"
    
    if (colonIndex1 > 0) {
      // Lấy phần giờ (từ sau "TIMER:" đến dấu : đầu tiên)
      int hours = message.substring(6, colonIndex1).toInt();
      
      // Lấy phần phút (từ sau dấu : đầu tiên đến hết chuỗi)
      int minutes = message.substring(colonIndex1 + 1).toInt();
      
      Serial.print("Da nhan lenh TIMER: ");
      Serial.print(hours);
      Serial.print("h ");
      Serial.print(minutes);
      Serial.println("m");
      
      setTimer(hours, minutes); // LED sẽ tự động điều khiển trong setTimer() -> setMotor()
    } else {
      Serial.println("✗ Lenh TIMER khong hop le! Dinh dang: TIMER:h:m");
    }
  } else if (message == "ON") {
    // Lệnh ON không còn được sử dụng trong app mới
    // Giữ lại để tương thích ngược, nhưng sẽ bật motor ngay lập tức (không hẹn giờ)
    isTimerMode = false;
    motorEndTime_ms = 0;
    distanceStartTime_ms = 0;
    setMotor(true); // LED sẽ tự động bật trong setMotor()
    Serial.println("Da nhan lenh ON (legacy) - Bat motor ngay lap tuc.");
  } else if (message == "OFF") {
    // Lệnh OFF không còn được sử dụng trong app mới
    // Giữ lại để tương thích ngược
    isTimerMode = false;
    motorEndTime_ms = 0;
    distanceStartTime_ms = 0;
    setMotor(false); // LED sẽ tự động tắt trong setMotor()
    Serial.println("Da nhan lenh OFF (legacy) - TAT motor.");
  } else if (message == "STATUS") {
    sendCurrentStatus();
    // Không thay đổi LED khi nhận STATUS
  } else if (message == "AUTO") {
    // Chuyen ve che do cam bien tu dong & Huy che do hen gio
    isTimerMode = false;
    motorEndTime_ms = 0;
    distanceStartTime_ms = 0;
    setMotor(false); // LED sẽ tự động tắt trong setMotor()
    Serial.println("Da huy che do hen gio, chuyen ve che do cam bien tu dong.");
    publishStatus("MODE_AUTO");
  }
}

void sendCurrentStatus() {
  String status = "STATUS:";
  status += isMotorOn ? "ON" : "OFF";
  status += "|MODE:";
  status += isTimerMode ? "TIMER" : "AUTO";
  status += "|WIFI:";
  status += wifiConnected ? "CONNECTED" : "DISCONNECTED";

  if (isTimerMode && motorEndTime_ms > 0) {
    unsigned long remaining_ms = motorEndTime_ms - millis();
    if (remaining_ms > 0) {
      unsigned long remaining_seconds = remaining_ms / 1000;
      unsigned long remaining_minutes = remaining_seconds / 60;
      remaining_seconds = remaining_seconds % 60;
      status += "|REMAINING:";
      status += String(remaining_minutes) + "m" + String(remaining_seconds) + "s";
    }
  }

  if (wifiConnected) {
    time_t now = time(nullptr);
    if (now > 0) {
      // Dùng hàm strftime cho định dạng sạch sẽ hơn
      char timeBuffer[50];
      strftime(timeBuffer, sizeof(timeBuffer), "%H:%M:%S %d/%m", localtime(&now));
      status += "|TIME:";
      status += String(timeBuffer);
    }
  }

  publishStatus(status.c_str());
}

void publishStatus(const char* status) {
  if (mqttConnected && client.connected()) {
    if (client.publish(mqtt_topic_status, status)) {
      Serial.print("📤 Gui trang thai: ");
      Serial.println(status);
    } else {
      Serial.println("✗ Khong the gui trang thai!");
    }
  }
}

bool connectMQTT() {
  if (mqttConnected && client.connected()) {
    return true;
  }

  if (!wifiConnected) {
    return false;
  }

  Serial.print("Dang ket noi MQTT toi ");
  Serial.print(mqtt_server);
  Serial.print(" ...");

  // MA LOI CU -2
  if (client.connect(mqtt_client_id)) {
    Serial.println("\n✓ Da ket noi MQTT thanh cong!");
    mqttConnected = true;

    if (client.subscribe(mqtt_topic_command)) {
      Serial.print("✓ Da dang ky topic: ");
      Serial.println(mqtt_topic_command);
    } else {
      Serial.println("✗ Khong the dang ky topic!");
    }
    return true;
  } else {
    Serial.print("\n✗ Khong the ket noi MQTT, ma loi: ");
    Serial.println(client.state());
    mqttConnected = false;
    return false;
  }
}

// ============================================
// SETUP
// ============================================
void setup() {
  // ============================================
  // KHỞI TẠO GPIO - ĐÃ ĐỔI SANG CHÂN AN TOÀN
  // ============================================
  // ĐÃ ĐỔI: GPIO 8, 9 (strapping pins) → GPIO 7, 1 (an toàn)
  // Điều này tránh lỗi boot khi dùng nguồn ngoài
  
  // Set LED (GPIO 7) về OUTPUT LOW NGAY LẬP TỨC
  pinMode(LED, OUTPUT);
  digitalWrite(LED, LOW);
  delay(10);
  
  // Set ECHO_PIN (GPIO 1) về INPUT_PULLDOWN
  pinMode(ECHO_PIN, INPUT_PULLDOWN);
  delay(10);
  
  // Set TRIG_PIN (GPIO 10) về OUTPUT LOW
  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  delay(10);
  
  // KHỞI TẠO SERIAL SAU KHI ĐÃ SET CÁC STRAPPING PINS
  Serial.begin(115200);
  delay(2000);  // Delay để đợi nguồn ổn định khi dùng nguồn ngoài
  
  // BẮT ĐẦU SERIAL OUTPUT
  Serial.println("\n\n========================================");
  Serial.println("HE THONG DIEU KHIEN MAY BOM NUOC");
  Serial.println("ESP32-C3 Super Mini");
  Serial.println("========================================\n");
  
  // Thiết lập các chân Motor cố định cho Phương án A
  pinMode(MOTOR_PIN, OUTPUT);
  digitalWrite(MOTOR_PIN, LOW);
  pinMode(IN_PIN_3, OUTPUT); // IN3 - GPIO 5 (đã thay đổi từ GPIO 3 để tránh xung đột JTAG)
  pinMode(IN_PIN_4, OUTPUT); // IN4 - GPIO 6 (đã thay đổi từ GPIO 2 để tránh xung đột JTAG)
  digitalWrite(IN_PIN_3, HIGH); // IN3 = HIGH
  digitalWrite(IN_PIN_4, LOW);  // IN4 = LOW
  Serial.println("✓ Da thiet lap IN3 (GPIO 5) va IN4 (GPIO 6) cho Motor Control."); 

  // TRIG và ECHO đã được thiết lập ở trên
  Serial.print("✓ Cam bien sieu am: TRIG (GPIO ");
  Serial.print(TRIG_PIN);
  Serial.print("), ECHO (GPIO ");
  Serial.print(ECHO_PIN);
  Serial.println(") da duoc khoi tao.");
  Serial.print("✓ LED (GPIO ");
  Serial.print(LED);
  Serial.println(") da duoc khoi tao.");

  digitalWrite(MOTOR_PIN, LOW);
  isMotorOn = false;
  Serial.println("Motor da duoc tat.");

  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
  Serial.println("MQTT da duoc thiet lap.");

  // Đợi thêm một chút để đảm bảo hệ thống ổn định khi dùng nguồn ngoài
  delay(500);
  
  Serial.println("Bat dau ket noi WiFi...");
  connectWiFi();

  if (wifiConnected) {
    Serial.println("WiFi da ket noi, se ket noi MQTT trong loop...");
  } else {
    Serial.println("WiFi chua ket noi, se thu lai trong loop.");
  }

  Serial.println("\nHe thong da san sang!");
  Serial.println("Che do mac dinh: Tu dong cam bien");
  Serial.println("========================================\n");

  lastWifiReconnectAttempt = 0;
  lastMqttReconnectAttempt = 0;
}

// ============================================
// LOOP - CHẠY LIÊN TỤC
// ============================================
void loop() {
  
  unsigned long currentTime_ms = millis();

  // 1. QUẢN LÝ KẾT NỐI WIFI
  if (WiFi.status() != WL_CONNECTED) {
    // ... (logic reconnect WiFi cũ) ...
    if (wifiConnected) { Serial.println("⚠ Mat ket noi WiFi!"); }
    wifiConnected = false;
    mqttConnected = false;

    if (currentTime_ms - lastWifiReconnectAttempt >= WIFI_RECONNECT_INTERVAL_MS) {
      lastWifiReconnectAttempt = currentTime_ms;
      Serial.println("Dang thu ket noi lai WiFi...");
      connectWiFi();
    }
  } else {
    if (!wifiConnected) { Serial.println("✓ WiFi da ket noi lai!"); }
    wifiConnected = true;

    // 2. QUẢN LÝ KẾT NỐI MQTT
    if (!client.connected()) {
      // ... (logic reconnect MQTT cũ) ...
      if (mqttConnected) { Serial.println("⚠ Mat ket noi MQTT!"); }
      mqttConnected = false;

      if (currentTime_ms - lastMqttReconnectAttempt >= MQTT_RECONNECT_INTERVAL_MS) {
        lastMqttReconnectAttempt = currentTime_ms;
        Serial.println("Dang thu ket noi lai MQTT...");
        connectMQTT();
      }
    } else {
      if (!mqttConnected) {
        Serial.println("✓ MQTT da ket noi!");
        delay(500);
        sendCurrentStatus();
      }
      mqttConnected = true;
      client.loop(); // Xử lý các tin nhắn MQTT
      delay(10); // Tăng delay lên 10ms để hệ thống có thời gian xử lý, tránh assert failed
    }

    // 3. ĐỒNG BỘ NTP (nếu có WiFi)
    static unsigned long lastNTPCheck = 0;
    if (currentTime_ms - lastNTPCheck >= 60000) { 
      lastNTPCheck = currentTime_ms;
      time_t now = time(nullptr);
      if (now < 1000) {
        configTime(gmtOffset_sec, 0, ntpServer);
      } else if (now > 0 && lastNTPCheck == 60000) { 
        Serial.print("✓ Gio hien tai (NTP): ");
        Serial.println(ctime(&now));
      }
    }
  }

  // 4. LOGIC CHÍNH: Hẹn giờ (Ưu tiên) và Cảm biến
  handleTimer();
  handleUltrasonicSensor();
  
  // Delay nhỏ để tránh quá tải CPU và cho hệ thống thời gian xử lý
  delay(10);
}