# Hướng Dẫn Debug Kết Nối ESP32

## 🔍 Kiểm Tra Kết Nối

Nếu app không nhận được phản hồi từ ESP32, hãy kiểm tra các bước sau:

### 1. Kiểm Tra ESP32 Có Kết Nối WiFi Không

Mở **Serial Monitor** trong Arduino IDE (Baud rate: 115200) và kiểm tra:

```
✓ Tìm dòng: "Da ket noi WiFi thanh cong!"
✓ Tìm dòng: "Dia chi IP: xxx.xxx.xxx.xxx"
```

**Nếu không thấy:**
- Kiểm tra tên WiFi và mật khẩu trong code ESP32
- Đảm bảo WiFi ở chế độ 2.4GHz (ESP32-C3 không hỗ trợ 5GHz)
- Kiểm tra ESP32 có trong phạm vi WiFi không

### 2. Kiểm Tra ESP32 Có Kết Nối MQTT Không

Trong Serial Monitor, tìm:

```
✓ "Da ket noi MQTT thanh cong!"
✓ "Da dang ky topic: maybom/command"
```

**Nếu không thấy:**
- Kiểm tra broker MQTT có đúng không (`broker.hivemq.com`)
- Kiểm tra kết nối Internet của ESP32
- Thử đổi sang broker khác (test.mosquitto.org)

### 3. Kiểm Tra ESP32 Có Nhận Được Lệnh Không

Khi bạn gửi lệnh từ app, trong Serial Monitor ESP32 sẽ hiển thị:

```
✓ "Nhan lenh tu MQTT: STATUS"
✓ "Nhan lenh tu MQTT: ON"
✓ "Nhan lenh tu MQTT: OFF"
```

**Nếu không thấy:**
- ESP32 chưa subscribe vào topic `maybom/command`
- Topic không khớp giữa app và ESP32
- ESP32 không gọi `client.loop()` trong hàm `loop()`

### 4. Kiểm Tra ESP32 Có Gửi Trạng Thái Không

Khi ESP32 nhận lệnh `STATUS`, nó sẽ gửi phản hồi. Trong Serial Monitor sẽ thấy:

```
✓ "Gui trang thai: STATUS:OFF|MODE:AUTO|WIFI:CONNECTED"
```

**Nếu không thấy:**
- ESP32 không gọi hàm `sendCurrentStatus()`
- ESP32 không publish vào topic `maybom/status`
- MQTT connection bị mất

## 🛠️ Các Lỗi Thường Gặp

### Lỗi: App gửi lệnh nhưng không nhận được phản hồi

**Nguyên nhân có thể:**
1. ESP32 chưa kết nối MQTT broker
2. ESP32 không subscribe vào topic đúng
3. ESP32 không gọi `client.loop()` thường xuyên
4. Topics không khớp

**Giải pháp:**
- Kiểm tra Serial Monitor của ESP32
- Đảm bảo ESP32 gọi `client.loop()` trong hàm `loop()`
- Kiểm tra topics trong code ESP32 và app có khớp không

### Lỗi: "Chưa có thông tin" hiển thị mãi

**Nguyên nhân:**
- ESP32 không gửi status về
- App không nhận được message từ ESP32

**Giải pháp:**
- Kiểm tra ESP32 có publish status không
- Kiểm tra app có subscribe vào topic `maybom/status` không
- Thử bấm vào trạng thái kết nối trong app để test

## 📱 Test Từ App

1. **Bấm vào trạng thái kết nối** (dòng "Đã kết nối" hoặc "Chưa kết nối")
   - App sẽ gửi test message và hiển thị hướng dẫn debug

2. **Kiểm tra Terminal trong app:**
   - Xem có dòng "📥 Nhận: [...]" không
   - Nếu không có → ESP32 không gửi message về

3. **Kiểm tra log:**
   - Tất cả lệnh gửi sẽ hiển thị "📤 Đã gửi: ..."
   - Tất cả message nhận sẽ hiển thị "📥 Nhận: [...]"

## 🔧 Kiểm Tra Topics

Đảm bảo topics khớp giữa ESP32 và App:

**ESP32 (may-uong-nuoc-meo-meo.ino):**
```cpp
const char* mqtt_topic_command = "maybom/command";  // Nhận lệnh
const char* mqtt_topic_status = "maybom/status";    // Gửi trạng thái
```

**App (MQTTHelper.java):**
```java
private static final String TOPIC_COMMAND = "maybom/command";  // Gửi lệnh
private static final String TOPIC_STATUS = "maybom/status";    // Nhận trạng thái
```

## 📊 Test Bằng MQTT Client Khác

Bạn có thể dùng MQTT client khác (như MQTT.fx hoặc HiveMQ WebSocket Client) để test:

1. **Subscribe vào `maybom/status`:**
   - Xem ESP32 có gửi message không

2. **Publish vào `maybom/command`:**
   - Gửi "STATUS" → ESP32 sẽ phản hồi
   - Gửi "ON" → ESP32 sẽ bật motor

3. **Kiểm tra ESP32 có nhận được không:**
   - Xem Serial Monitor của ESP32

## ✅ Checklist Debug

- [ ] ESP32 kết nối WiFi thành công
- [ ] ESP32 kết nối MQTT broker thành công
- [ ] ESP32 subscribe vào `maybom/command`
- [ ] ESP32 gọi `client.loop()` trong hàm `loop()`
- [ ] App subscribe vào `maybom/status`
- [ ] Topics khớp giữa ESP32 và App
- [ ] ESP32 publish status khi nhận lệnh STATUS
- [ ] Serial Monitor ESP32 hiển thị lệnh nhận được

## 💡 Mẹo

1. **Luôn mở Serial Monitor** khi test để xem ESP32 đang làm gì
2. **Kiểm tra Terminal trong app** để xem app có nhận được message không
3. **Test từng bước một:** WiFi → MQTT → Subscribe → Publish
4. **Dùng MQTT client khác** để test broker có hoạt động không

