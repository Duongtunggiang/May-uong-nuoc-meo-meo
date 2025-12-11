# Ứng Dụng Android Điều Khiển Máy Bơm Nước

Ứng dụng Android để điều khiển máy bơm nước qua MQTT, kết nối với ESP32-C3.

## ✨ Tính Năng

- ✅ Kết nối MQTT để điều khiển từ xa
- ✅ Bật/Tắt máy bơm trực tiếp
- ✅ Hẹn giờ tắt tự động (đặt thời gian chạy)
- ✅ Chuyển sang chế độ cảm biến tự động
- ✅ Màn hình Terminal để xem log và trạng thái real-time
- ✅ Hiển thị trạng thái kết nối và máy bơm
- ✅ Tự động reconnect khi mất kết nối

## 📋 Yêu Cầu

- Android SDK 30 (Android 11) trở lên
- Kết nối Internet để giao tiếp với MQTT Broker
- ESP32-C3 đã được cấu hình và kết nối WiFi

## 🚀 Cài Đặt

### 1. Cài Đặt Dependencies

Dự án đã được cấu hình sẵn với các thư viện cần thiết:
- MQTT Client (Eclipse Paho)
- Material Design Components
- AndroidX Libraries

### 2. Build và Chạy

1. Mở project trong Android Studio
2. Sync Gradle files
3. Kết nối thiết bị Android hoặc khởi động emulator
4. Nhấn Run (Shift + F10)

## 📱 Hướng Dẫn Sử Dụng

### Kết Nối MQTT

1. Mở ứng dụng
2. Nhấn nút **"Kết Nối"** để kết nối với MQTT Broker
3. Đợi đến khi trạng thái chuyển sang "Đã kết nối" (màu xanh)

### Điều Khiển Máy Bơm

#### Bật/Tắt Trực Tiếp
- **BẬT**: Nhấn nút "BẬT" để bật máy bơm ngay lập tức
- **TẮT**: Nhấn nút "TẮT" để tắt máy bơm ngay lập tức

#### Hẹn Giờ Tắt
1. Nhập số giờ và phút vào các ô tương ứng
2. Nhấn nút **"Đặt Hẹn Giờ"**
3. Máy bơm sẽ tự động tắt sau thời gian đã đặt

**Ví dụ:**
- Nhập `1` giờ và `30` phút → Máy bơm sẽ chạy 1 giờ 30 phút rồi tự tắt

#### Chế Độ Tự Động
- Nhấn nút **"Chế Độ Tự Động (Cảm Biến)"** để chuyển sang chế độ cảm biến siêu âm
- Máy bơm sẽ tự động bật khi phát hiện vật thể trong 20cm

### Màn Hình Terminal

1. Nhấn nút **"Mở Terminal"** để xem log chi tiết
2. Terminal hiển thị:
   - Tất cả các lệnh đã gửi
   - Tất cả các thông báo nhận được từ ESP32
   - Trạng thái kết nối
   - Thời gian thực của mỗi sự kiện

3. Các nút trong Terminal:
   - **Xóa Log**: Xóa tất cả log hiện tại
   - **Yêu Cầu Trạng Thái**: Gửi lệnh yêu cầu ESP32 gửi trạng thái hiện tại

## ⚙️ Cấu Hình MQTT

Mặc định ứng dụng sử dụng HiveMQ Cloud Broker miễn phí:
- **Server**: `broker.hivemq.com`
- **Port**: `1883`
- **Topic Command**: `maybom/command`
- **Topic Status**: `maybom/status`

### Thay Đổi Broker

Nếu bạn muốn dùng broker khác, sửa trong file `MQTTHelper.java`:

```java
private static final String MQTT_SERVER = "tcp://your-broker.com:1883";
private static final String TOPIC_COMMAND = "your-topic/command";
private static final String TOPIC_STATUS = "your-topic/status";
```

## 📊 Các Lệnh MQTT

Ứng dụng gửi các lệnh sau đến ESP32:

| Lệnh | Mô Tả |
|------|-------|
| `ON` | Bật máy bơm ngay |
| `OFF` | Tắt máy bơm ngay |
| `TIMER:h:m` | Hẹn giờ chạy h giờ m phút |
| `AUTO` | Chuyển sang chế độ cảm biến tự động |
| `STATUS` | Yêu cầu gửi trạng thái |

## 🔔 Nhận Trạng Thái

ESP32 sẽ gửi các thông báo trạng thái về:

- `MOTOR_ON` - Motor đã bật
- `MOTOR_OFF` - Motor đã tắt
- `TIMER_SET:1h30m` - Đã đặt hẹn giờ
- `TIMER_RUNNING:45m30s` - Đang chạy, còn lại 45m30s
- `TIMER_OFF` - Hẹn giờ đã hết
- `AUTO_ON` - Bật tự động từ cảm biến
- `AUTO_OFF` - Tắt tự động từ cảm biến
- `STATUS:ON|MODE:TIMER|WIFI:CONNECTED|REMAINING:30m0s|TIME:...` - Trạng thái chi tiết

## 🐛 Xử Lý Sự Cố

### Không kết nối được MQTT
- Kiểm tra kết nối Internet
- Kiểm tra broker có đang hoạt động không
- Xem log trong Terminal để biết lỗi cụ thể

### Không nhận được phản hồi từ ESP32
- Kiểm tra ESP32 đã kết nối WiFi chưa
- Kiểm tra ESP32 đã kết nối MQTT chưa
- Kiểm tra topic có đúng không
- Xem Serial Monitor của ESP32 để debug

### Ứng dụng bị crash
- Xem logcat trong Android Studio
- Kiểm tra permissions trong AndroidManifest.xml
- Đảm bảo đã cài đặt đầy đủ dependencies

## 📝 Lưu Ý

1. **Kết nối MQTT**: Ứng dụng cần kết nối Internet để giao tiếp với MQTT Broker
2. **Permissions**: Ứng dụng yêu cầu quyền Internet và Network State
3. **Background**: Kết nối MQTT sẽ được giữ khi ứng dụng chạy nền
4. **Reconnect**: Ứng dụng tự động reconnect khi mất kết nối

## 🔄 Cập Nhật

- **v1.0**: Phiên bản đầu với đầy đủ tính năng cơ bản

## 📄 License

Dự án mã nguồn mở, tự do sử dụng và chỉnh sửa.

