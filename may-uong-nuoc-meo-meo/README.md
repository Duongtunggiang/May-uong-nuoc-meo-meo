# Hệ Thống Điều Khiển Máy Bơm Nước ESP32-C3

Hệ thống điều khiển máy bơm nước thông minh sử dụng ESP32-C3 Super Mini với các tính năng:
- Điều khiển từ xa qua WiFi/MQTT
- Hẹn giờ tắt tự động
- Cảm biến siêu âm tự động bật khi phát hiện vật thể
- Hoạt động ổn định ngay cả khi mất WiFi

## 📋 Yêu Cầu Phần Cứng

- ESP32-C3 Super Mini
- Motor Control Module (điều khiển bơm)
- Cảm biến siêu âm HC-SR04
- Nguồn 5V cho ESP32 (USB)
- Pin 7.4V cho máy bơm

## 🔌 Đấu Nối

### GPIO ESP32-C3:
- **GPIO 4** → ENB của Motor Control (điều khiển BẬT/TẮT)
- **GPIO 10** → Trig của cảm biến siêu âm
- **GPIO 9** → Echo của cảm biến siêu âm

### Nguồn:
- ESP32-C3: Cấp nguồn 5V qua USB
- Motor Control: Nối Pin 7.4V vào VS+
- **QUAN TRỌNG**: Nối chung GND giữa ESP32, Motor Control và Pin 7.4V

## ⚙️ Cài Đặt

### 1. Cài đặt Thư viện Arduino

Mở Arduino IDE và cài đặt các thư viện sau:
- **WiFi** (có sẵn trong ESP32)
- **PubSubClient** (Tools → Manage Libraries → tìm "PubSubClient" bởi Nick O'Leary)

### 2. Cấu hình WiFi và MQTT

Mở file `may-uong-nuoc-meo-meo.ino` và sửa các thông tin sau:

```cpp
const char* ssid = "TEN_WIFI_CUA_BAN";           // Tên WiFi của bạn
const char* password = "MAT_KHAU_CUA_BAN";       // Mật khẩu WiFi

const char* mqtt_server = "broker.hivemq.com";   // MQTT Broker
const int mqtt_port = 1883;
const char* mqtt_topic_command = "maybom/command";    // Topic nhận lệnh
const char* mqtt_topic_status = "maybom/status";        // Topic gửi trạng thái
const char* mqtt_client_id = "ESP32_MayBom_001";       // ID duy nhất
```

### 3. Chọn Board và Port

- Board: **ESP32C3 Dev Module**
- Port: Chọn cổng COM của ESP32-C3
- Upload Speed: 921600 (hoặc 115200)

### 4. Upload Code

Nhấn Upload và đợi code được nạp vào ESP32-C3.

## 📱 Sử Dụng

### Chế Độ Mặc Định: Cảm Biến Tự Động

Khi không có lệnh từ điện thoại, hệ thống sẽ tự động:
- Bật máy bơm khi cảm biến phát hiện vật thể trong vòng 20cm liên tục 2 giây
- Tắt máy bơm khi vật thể rời đi

### Điều Khiển Qua MQTT (Từ Điện Thoại)

#### Các Lệnh Gửi Đến Topic: `maybom/command`

1. **Hẹn giờ chạy:**
   ```
   TIMER:h:m
   ```
   Ví dụ: `TIMER:1:30` = chạy 1 giờ 30 phút rồi tự tắt

2. **Bật ngay:**
   ```
   ON
   ```

3. **Tắt ngay:**
   ```
   OFF
   ```

4. **Chuyển về chế độ cảm biến tự động:**
   ```
   AUTO
   ```

5. **Xem trạng thái:**
   ```
   STATUS
   ```

#### Nhận Trạng Thái Từ Topic: `maybom/status`

ESP32 sẽ gửi các thông báo trạng thái như:
- `MOTOR_ON` - Motor đã bật
- `MOTOR_OFF` - Motor đã tắt
- `TIMER_SET:1h30m` - Đã đặt hẹn giờ 1h30m
- `TIMER_RUNNING:45m30s` - Đang chạy, còn lại 45 phút 30 giây
- `TIMER_OFF` - Hẹn giờ đã hết, motor đã tắt
- `AUTO_ON` - Bật tự động từ cảm biến
- `AUTO_OFF` - Tắt tự động từ cảm biến
- `STATUS:ON|MODE:TIMER|WIFI:CONNECTED|REMAINING:30m0s|TIME:...` - Trạng thái chi tiết

## 🔧 MQTT Broker Miễn Phí

Bạn có thể sử dụng các MQTT Broker miễn phí sau:

1. **HiveMQ Cloud** (đã cấu hình sẵn trong code)
   - Server: `broker.hivemq.com`
   - Port: `1883`
   - Không cần đăng ký

2. **Mosquitto Test Server**
   - Server: `test.mosquitto.org`
   - Port: `1883`

3. **Tạo MQTT Broker riêng** (nếu muốn bảo mật hơn)

## 📱 Ứng Dụng Android Điều Khiển

Bạn có thể sử dụng các app MQTT Client trên Android như:
- **MQTT Dashboard**
- **MQTT Tool**
- **IoT MQTT Panel**

Hoặc tự tạo app Android sử dụng thư viện **Eclipse Paho MQTT Client**.

## ⚠️ Lưu Ý Quan Trọng

1. **Mất WiFi**: Khi mất WiFi, hệ thống vẫn tiếp tục chạy theo hẹn giờ đã đặt. Tiến trình được lưu bằng `millis()` nên không bị mất.

2. **Chế độ ưu tiên**: 
   - Khi đặt hẹn giờ → Chế độ hẹn giờ (tắt cảm biến tự động)
   - Khi gửi lệnh `AUTO` → Chế độ cảm biến tự động (tắt hẹn giờ)

3. **Cảm biến siêu âm**: Chân Echo của HC-SR04 xuất 5V, trong khi ESP32-C3 chỉ chịu được 3.3V. Nên sử dụng mạch chia áp (voltage divider) với 2 điện trở 1kΩ và 2kΩ để bảo vệ ESP32.

4. **Nguồn điện**: Đảm bảo nguồn 5V cho ESP32 ổn định. Pin 7.4V chỉ dùng cho máy bơm.

## 🐛 Xử Lý Sự Cố

### ESP32 không kết nối WiFi
- Kiểm tra tên WiFi và mật khẩu
- Đảm bảo WiFi ở chế độ 2.4GHz (ESP32-C3 không hỗ trợ 5GHz)
- Kiểm tra Serial Monitor để xem thông báo lỗi

### MQTT không kết nối
- Kiểm tra kết nối WiFi
- Kiểm tra địa chỉ MQTT broker
- Thử đổi sang broker khác (test.mosquitto.org)

### Cảm biến không hoạt động
- Kiểm tra đấu nối Trig và Echo
- Kiểm tra nguồn 5V cho cảm biến
- Kiểm tra Serial Monitor để xem giá trị khoảng cách

### Motor không chạy
- Kiểm tra nguồn Pin 7.4V
- Kiểm tra đấu nối Motor Control
- Kiểm tra GPIO 4 có tín hiệu không (dùng multimeter)

## 📝 Changelog

- **v2.0**: Thêm MQTT, cải thiện xử lý mất WiFi, hẹn giờ hoạt động độc lập
- **v1.0**: Phiên bản đầu với WebServer

## 📄 License

Dự án mã nguồn mở, tự do sử dụng và chỉnh sửa.

