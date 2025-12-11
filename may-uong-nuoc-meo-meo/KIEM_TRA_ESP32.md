# Hướng Dẫn Kiểm Tra ESP32

## 🔍 Vấn Đề: App Không Nhận Được Status Từ ESP32

Nếu app gửi lệnh nhưng không nhận được phản hồi, hãy kiểm tra các bước sau:

## ✅ Bước 1: Kiểm Tra Serial Monitor ESP32

Mở **Serial Monitor** trong Arduino IDE (Baud rate: **115200**) và kiểm tra:

### 1.1. ESP32 Có Kết Nối WiFi Không?

Tìm các dòng sau:
```
Da ket noi WiFi thanh cong!
Dia chi IP: xxx.xxx.xxx.xxx
```

**Nếu KHÔNG thấy:**
- Kiểm tra tên WiFi và mật khẩu trong code
- Đảm bảo WiFi ở chế độ 2.4GHz
- Kiểm tra ESP32 có trong phạm vi WiFi không

### 1.2. ESP32 Có Kết Nối MQTT Không?

Tìm các dòng sau:
```
Da ket noi MQTT thanh cong!
Da dang ky topic: maybom/command
```

**Nếu KHÔNG thấy:**
- Kiểm tra broker MQTT (`broker.hivemq.com`)
- Kiểm tra kết nối Internet của ESP32
- Kiểm tra có lỗi gì trong Serial Monitor không

### 1.3. ESP32 Có Nhận Được Lệnh Không?

Khi bạn gửi lệnh từ app, trong Serial Monitor sẽ thấy:
```
Nhan lenh tu MQTT: STATUS
Nhan lenh tu MQTT: ON
Nhan lenh tu MQTT: OFF
Nhan lenh tu MQTT: TIMER:0:30
```

**Nếu KHÔNG thấy:**
- ESP32 chưa subscribe vào `maybom/command`
- ESP32 không gọi `client.loop()` trong hàm `loop()`
- Topic không khớp

### 1.4. ESP32 Có Gửi Status Không?

Khi ESP32 nhận lệnh `STATUS`, nó sẽ gửi phản hồi. Trong Serial Monitor sẽ thấy:
```
Gui trang thai: STATUS:OFF|MODE:AUTO|WIFI:CONNECTED
```

**Nếu KHÔNG thấy:**
- ESP32 không gọi hàm `sendCurrentStatus()`
- ESP32 không publish vào `maybom/status`
- MQTT connection bị mất

## 🔧 Các Lỗi Thường Gặp

### Lỗi 1: ESP32 Không Kết Nối MQTT

**Triệu chứng:**
- Serial Monitor hiển thị: "Khong the ket noi MQTT, ma loi: X"
- App không nhận được status

**Giải pháp:**
1. Kiểm tra broker MQTT có đúng không
2. Kiểm tra kết nối Internet
3. Thử đổi sang broker khác (test.mosquitto.org)

### Lỗi 2: ESP32 Không Nhận Được Lệnh

**Triệu chứng:**
- App gửi lệnh nhưng Serial Monitor không hiển thị "Nhan lenh tu MQTT"
- App không nhận được phản hồi

**Giải pháp:**
1. Đảm bảo ESP32 subscribe vào `maybom/command`:
   ```cpp
   client.subscribe(mqtt_topic_command);
   ```

2. Đảm bảo ESP32 gọi `client.loop()` trong hàm `loop()`:
   ```cpp
   void loop() {
     // ... code khác ...
     if (client.connected()) {
       client.loop(); // QUAN TRỌNG!
     }
   }
   ```

### Lỗi 3: ESP32 Không Gửi Status

**Triệu chứng:**
- ESP32 nhận được lệnh nhưng không gửi status về
- App không nhận được message từ `maybom/status`

**Giải pháp:**
1. Kiểm tra hàm `sendCurrentStatus()` có được gọi không
2. Kiểm tra hàm `publishStatus()` có publish vào đúng topic không
3. Kiểm tra MQTT connection còn hoạt động không

## 📋 Checklist Kiểm Tra

- [ ] ESP32 kết nối WiFi thành công
- [ ] ESP32 kết nối MQTT broker thành công
- [ ] ESP32 subscribe vào `maybom/command`
- [ ] ESP32 gọi `client.loop()` trong hàm `loop()`
- [ ] ESP32 nhận được lệnh (hiển thị trong Serial Monitor)
- [ ] ESP32 gửi status (hiển thị "Gui trang thai" trong Serial Monitor)
- [ ] Topics khớp giữa ESP32 và App

## 🧪 Test Thủ Công

### Test 1: Kiểm Tra ESP32 Có Online Không

1. Mở Serial Monitor ESP32
2. Gửi lệnh `STATUS` từ app
3. Xem Serial Monitor có hiển thị "Nhan lenh tu MQTT: STATUS" không
4. Xem Serial Monitor có hiển thị "Gui trang thai" không

### Test 2: Kiểm Tra MQTT Broker

Dùng MQTT client khác (như MQTT.fx) để test:

1. **Subscribe vào `maybom/status`:**
   - Xem ESP32 có gửi message không

2. **Publish vào `maybom/command`:**
   - Gửi "STATUS" → ESP32 sẽ phản hồi
   - Gửi "ON" → ESP32 sẽ bật motor

3. **Kiểm tra ESP32 có nhận được không:**
   - Xem Serial Monitor của ESP32

## 💡 Mẹo Debug

1. **Luôn mở Serial Monitor** khi test để xem ESP32 đang làm gì
2. **Kiểm tra từng bước một:** WiFi → MQTT → Subscribe → Publish
3. **Dùng MQTT client khác** để test broker có hoạt động không
4. **Kiểm tra topics** có đúng không (case-sensitive!)

## ⚠️ Lưu Ý Quan Trọng

1. **`client.loop()` phải được gọi thường xuyên** trong hàm `loop()` để ESP32 nhận được message
2. **Topics phải khớp chính xác** (case-sensitive, không có khoảng trắng)
3. **MQTT connection phải được giữ** - nếu mất kết nối, ESP32 sẽ không nhận/gửi được message

## 🔍 Code Mẫu Đúng

```cpp
void loop() {
  // Kiểm tra và reconnect MQTT nếu cần
  if (!client.connected()) {
    connectMQTT();
  } else {
    client.loop(); // QUAN TRỌNG: Phải gọi để nhận message!
  }
  
  // ... code khác ...
}
```

Nếu vẫn không hoạt động, hãy kiểm tra Serial Monitor và chia sẻ log để debug tiếp!

