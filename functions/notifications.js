const {
  onDocumentUpdated,
  onDocumentCreated,
} = require("firebase-functions/v2/firestore");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

const statusText = {
  SEARCHING: "Đang tìm tài xế",
  ACCEPTED: "Tài xế đã nhận đơn",
  PICKED_UP: "Tài xế đã lấy món",
  DELIVERING: "Đơn đang được giao",
  COMPLETED: "Đơn đã hoàn tất",
  CANCELLED: "Đơn đã hủy",
};

async function tokenFor(uid) {
  if (!uid) return null;
  const snapshot = await getFirestore()
    .collection("deviceTokens")
    .doc(uid)
    .get();
  return snapshot.exists ? snapshot.get("token") : null;
}

async function send(uid, title, body, orderId) {
  const token = await tokenFor(uid);
  if (!token) return;
  try {
    await getMessaging().send({ token, data: { title, body, orderId } });
  } catch (error) {
    if (
      error.code === "messaging/registration-token-not-registered" ||
      error.code === "messaging/invalid-registration-token"
    ) {
      await getFirestore().collection("deviceTokens").doc(uid).delete();
    } else {
      console.error(`FCM send failed for ${uid}:`, error.code || error.message);
    }
  }
}

exports.notifyOrderUpdated = onDocumentUpdated(
  "orders/{orderId}",
  async (event) => {
    const before = event.data.before.data();
    const after = event.data.after.data();
    if (before.status === after.status) return;
    const body = statusText[after.status] || after.status;
    const tasks = [
      send(after.customerId, "Cập nhật đơn hàng", body, event.params.orderId),
    ];
    if (after.driverId)
      tasks.push(
        send(after.driverId, "Đơn giao hàng", body, event.params.orderId),
      );
    await Promise.all(tasks);
  },
);

exports.notifyPriorityOffer = onDocumentCreated(
  "orderOffers/{orderId}",
  async (event) => {
    const data = event.data.data();
    if (!data.targetDriverId) return;
    const total = Number(data.total || 0).toLocaleString("vi-VN");
    await send(
      data.targetDriverId,
      "Đơn ưu tiên cho bạn",
      `${data.restaurantName} · ${total} VND`,
      event.params.orderId,
    );
  },
);
