# Hướng Dẫn Cài Đặt Thư Viện PubSubClient

## ⚠️ Lỗi: `PubSubClient.h: No such file or directory`

Lỗi này xảy ra vì bạn chưa cài đặt thư viện **PubSubClient** trong Arduino IDE.

## 📥 Cách Cài Đặt Thư Viện PubSubClient

### Phương Pháp 1: Cài Đặt Qua Library Manager (Khuyên Dùng)

1. **Mở Arduino IDE**

2. **Vào menu**: `Tools` → `Manage Libraries...` (Hoặc nhấn `Ctrl + Shift + I`)

3. **Tìm kiếm**: Gõ `PubSubClient` vào ô tìm kiếm

4. **Chọn thư viện**: Tìm và chọn **"PubSubClient"** bởi **Nick O'Leary**

5. **Cài đặt**: Nhấn nút **"Install"** (phiên bản mới nhất thường là 2.8.x)

6. **Đợi cài đặt hoàn tất**: Bạn sẽ thấy thông báo "Installed" khi xong

7. **Đóng cửa sổ Library Manager**

8. **Thử biên dịch lại**: Nhấn `Ctrl + R` hoặc nút Verify (✓)

### Phương Pháp 2: Cài Đặt Thủ Công (Nếu Phương Pháp 1 Không Hoạt Động)

1. **Tải thư viện từ GitHub**:
   - Truy cập: https://github.com/knolleary/pubsubclient
   - Nhấn nút **"Code"** → **"Download ZIP"**

2. **Cài đặt trong Arduino IDE**:
   - Mở Arduino IDE
   - Vào menu: `Sketch` → `Include Library` → `Add .ZIP Library...`
   - Chọn file ZIP vừa tải về
   - Đợi cài đặt hoàn tất

3. **Kiểm tra cài đặt**:
   - Vào menu: `Sketch` → `Include Library`
   - Tìm `PubSubClient` trong danh sách → Nếu có nghĩa là đã cài thành công

### Phương Pháp 3: Cài Đặt Qua Package Manager URL (ESP32)

Nếu bạn đang dùng ESP32 và Library Manager không tìm thấy:

1. **Thêm URL Board Manager** (nếu chưa có):
   - Vào `File` → `Preferences`
   - Trong ô "Additional Board Manager URLs", thêm:
     ```
     https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
     ```
   - Nhấn OK

2. **Cài đặt ESP32 Board**:
   - Vào `Tools` → `Board` → `Boards Manager...`
   - Tìm "ESP32" và cài đặt

3. **Sau đó cài PubSubClient** theo Phương Pháp 1

## ✅ Kiểm Tra Cài Đặt Thành Công

Sau khi cài đặt, bạn có thể kiểm tra bằng cách:

1. Mở file `.ino` của bạn
2. Nhấn `Ctrl + R` (Verify/Compile)
3. Nếu không còn lỗi `PubSubClient.h: No such file or directory` → **Thành công!**

## 🔍 Xác Định Vị Trí Thư Viện Đã Cài

Thư viện thường được cài tại:
- **Windows**: `C:\Users\[TênNgườiDùng]\Documents\Arduino\libraries\PubSubClient\`
- **Mac**: `~/Documents/Arduino/libraries/PubSubClient/`
- **Linux**: `~/Arduino/libraries/PubSubClient/`

## ⚠️ Lưu Ý Quan Trọng

1. **Phiên bản PubSubClient**: 
   - Nên dùng phiên bản **2.8.x** trở lên
   - Phiên bản cũ có thể không tương thích với ESP32-C3

2. **Nhiều phiên bản**: 
   - Nếu có nhiều phiên bản PubSubClient, Arduino IDE sẽ dùng phiên bản mới nhất
   - Nếu gặp lỗi, thử xóa các phiên bản cũ trong thư mục libraries

3. **ESP32-C3**: 
   - Đảm bảo bạn đã cài đặt ESP32 Board Support Package
   - Vào `Tools` → `Board` → `Boards Manager...` → Tìm "esp32" và cài đặt

## 🐛 Xử Lý Lỗi Thường Gặp

### Lỗi: "Multiple libraries found"
- **Nguyên nhân**: Có nhiều phiên bản PubSubClient
- **Giải pháp**: Xóa các phiên bản cũ, chỉ giữ lại phiên bản mới nhất

### Lỗi: "No such file or directory" sau khi cài đặt
- **Nguyên nhân**: Arduino IDE chưa nhận diện thư viện
- **Giải pháp**: 
  1. Đóng và mở lại Arduino IDE
  2. Kiểm tra đường dẫn thư viện trong `File` → `Preferences` → `Sketchbook location`

### Lỗi: "Compilation error" với ESP32-C3
- **Nguyên nhân**: Phiên bản PubSubClient không tương thích
- **Giải pháp**: Cập nhật PubSubClient lên phiên bản mới nhất (2.8.x trở lên)

## 📚 Tài Liệu Tham Khảo

- **PubSubClient GitHub**: https://github.com/knolleary/pubsubclient
- **ESP32 Arduino**: https://github.com/espressif/arduino-esp32
- **Arduino Library Guide**: https://www.arduino.cc/en/guide/libraries

## 💡 Mẹo

- Sau khi cài đặt thư viện, luôn **đóng và mở lại Arduino IDE** để đảm bảo thư viện được load đúng
- Nếu vẫn gặp lỗi, thử **xóa thư mục `build`** trong thư mục sketch của bạn và biên dịch lại

