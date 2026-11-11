# FoodNow

Ứng dụng đặt và giao đồ ăn gồm Android khách hàng, Android tài xế, Admin Web và Shipper Web, dùng chung Firebase.

**Sinh viên:** 524H0098 - Nguyễn Hòa Khang
**Website:** <https://nhk-foodnow.web.app>

## Chức năng

- **Khách hàng:** đăng nhập email/Google, tìm nhà hàng, giỏ hàng, đặt COD, theo dõi bản đồ, chat, lịch sử, hủy đơn, sửa hồ sơ.
- **Tài xế:** nhận offer gần nhất, lấy món, giao hàng, hoàn tất COD, trả/hủy đơn, gửi vị trí, lịch sử.
- **Admin Web:** thống kê đơn, duyệt/khóa tài xế, xem người dùng, sửa nhà hàng và món.
- **Shipper Web:** nhận đơn, giao hàng và gửi vị trí trên trình duyệt.

## Công nghệ

Java 11, XML/ViewBinding, Firebase Auth, Firestore, FCM, Cloud Functions v2, Leaflet/OpenStreetMap/OSRM, HTML/CSS/JS.

## Chạy dự án

Yêu cầu: JDK 17+, Android SDK 37, Node.js 22+.

```bash
npm ci
npm run build
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

APK có sẵn: `delivery/FoodNow-1.0-demo.apk`

Build bằng terminal cần đặt `ANDROID_HOME`; hoặc mở bằng Android Studio.

### Firebase

Project và cấu hình client đã có trong `app/google-services.json` và `web/firebase-config.js`.

Nếu dùng project khác: tạo Android app với application ID `com.example.a524h0098_nguyenhoakhang_foodnow_project`, thêm `google-services.json`, điền Web config, sau đó:

```bash
firebase deploy --only firestore:rules,firestore:indexes --project <project-id>
GCLOUD_PROJECT=<project-id> npm run seed
```

Cấp quyền admin:

```bash
GCLOUD_PROJECT=<project-id> npm run admin:claim -- admin@foodnow.test
```

Google Sign-In cần SHA-1 của debug keystore trong Firebase Console.

## Tài khoản demo

| Vai trò    | Email                    |
| ---------- | ------------------------ |
| Khách hàng | `customer1@foodnow.test` |
| Khách hàng | `customer2@foodnow.test` |
| Tài xế     | `driver1@foodnow.test`   |
| Tài xế     | `driver2@foodnow.test`   |
| Admin      | `admin@foodnow.test`     |

Mật khẩu lưu trong `Mục 8.5` của file Báo cáo đồ án

## Tài liệu

- [Báo cáo đồ án (PDF)](docs/NguyenHoaKhang_524H0098_FoodNow_Project.pdf)
- [Hướng dẫn và demo](delivery/HUONG_DAN.md)
