# HƯỚNG DẪN FOODNOW

**Sinh viên:** 524H0098 - Nguyễn Hòa Khang
**Firebase:** `nhk-foodnow`
**Web:** <https://nhk-foodnow.web.app>

## 1. Bàn giao

- `FoodNow-1.0-demo.apk`: APK debug
- `verification.json`: thông tin build, SHA-256, kết quả kiểm tra
- `firestore.rules`, `firestore.indexes.json`: bản sao cấu hình đã deploy
- Mã nguồn ở thư mục gốc repository

## 2. Build

Yêu cầu: JDK 17+, Android SDK 37, Node.js 22+.

```bash
npm ci
npm run build
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Hoặc:

```bash
npm run build:install   # build + cài qua ADB
npm run build:web       # chỉ build web
```

Build bằng terminal cần `ANDROID_HOME`; hoặc mở bằng Android Studio.

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 3. Firebase

Cấu hình client đã có trong:

- `app/google-services.json`
- `web/firebase-config.js`

Nếu dùng project khác:

1. Bật Email/Password và Google trong Authentication.
2. Thêm Android app với application ID `com.example.a524h0098_nguyenhoakhang_foodnow_project`.
3. Tải `google-services.json` mới vào `app/`.
4. Điền Web config vào `web/firebase-config.js`.
5. Thêm SHA-1 debug keystore nếu dùng Google Sign-In.

Deploy và seed:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project nhk-foodnow
GCLOUD_PROJECT=nhk-foodnow npm run seed
```

Cấp quyền admin:

```bash
GCLOUD_PROJECT=nhk-foodnow npm run admin:claim -- admin@foodnow.test
```

Đăng xuất rồi đăng nhập lại web sau khi cấp claim.

## 4. Deploy web

```bash
firebase deploy --only hosting --project nhk-foodnow
```

- Admin: <https://nhk-foodnow.web.app>
- Shipper: <https://nhk-foodnow.web.app/shipper.html>

## 5. Tài khoản demo

| Vai trò    | Email                    |
| ---------- | ------------------------ |
| Khách hàng | `customer1@foodnow.test` |
| Khách hàng | `customer2@foodnow.test` |
| Tài xế     | `driver1@foodnow.test`   |
| Tài xế     | `driver2@foodnow.test`   |
| Admin      | `admin@foodnow.test`     |

Mật khẩu lưu trong `Mục 8.5` của file Báo cáo đồ án

`customer1` có 1 đơn đang tìm tài xế và 1 đơn đã hoàn tất. `driver1` đang online.

## 6. Demo

### Khách hàng

1. Đăng nhập, chọn nhà hàng và thêm món.
2. Mở giỏ, xác nhận đơn, kiểm tra COD và địa chỉ.
3. Đặt hàng, theo dõi trạng thái và bản đồ.
4. Có thể hủy trước khi tài xế lấy món.

### Tài xế Android

1. Đăng nhập, bật nhận đơn và cấp quyền vị trí.
2. Nhận offer ưu tiên trong 45 giây.
3. Xác nhận lấy món, bắt đầu giao, hoàn tất.
4. Có thể trả đơn trước khi lấy hoặc hủy sau khi lấy.

### Shipper Web

1. Mở `/shipper.html`, đăng nhập tài xế.
2. Bật nhận đơn, cho phép vị trí.
3. Nhận đơn, lấy món, giao hàng, hoàn tất COD.

### Admin Web

1. Mở trang chủ, đăng nhập tài khoản admin.
2. Xem thống kê, đơn hàng, người dùng.
3. Duyệt/khóa tài xế, sửa nhà hàng và món.
