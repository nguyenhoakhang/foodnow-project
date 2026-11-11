import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  getFirestore,
  doc,
  getDoc,
  setDoc,
  deleteDoc,
  onSnapshot,
  collection,
  query,
  where,
  runTransaction,
  updateDoc,
  serverTimestamp,
  deleteField,
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig, "shipper");
const auth = getAuth(app);
const db = getFirestore(app);

const $ = (id) => document.getElementById(id);
const message = $("message");
const statuses = {
  SEARCHING: "Đang tìm tài xế",
  ACCEPTED: "Tài xế đã nhận",
  PICKED_UP: "Đã lấy món",
  DELIVERING: "Đang giao",
  COMPLETED: "Đã hoàn tất",
  CANCELLED: "Đã hủy",
};
const loginErrors = {
  "auth/invalid-credential": "Email hoặc mật khẩu chưa đúng.",
  "auth/wrong-password": "Email hoặc mật khẩu chưa đúng.",
  "auth/user-not-found": "Email hoặc mật khẩu chưa đúng.",
  "auth/invalid-email": "Địa chỉ email không hợp lệ.",
  "auth/user-disabled": "Tài khoản đã bị vô hiệu hóa.",
  "auth/operation-not-allowed": "Chưa bật phương thức Email/Password.",
  "auth/too-many-requests": "Thử quá nhiều lần. Đợi rồi thử lại.",
  "auth/network-request-failed": "Không kết nối được Firebase.",
  "auth/api-key-not-valid": "Cấu hình Firebase Web chưa đúng.",
  "auth/invalid-api-key": "Cấu hình Firebase Web chưa đúng.",
};

let uid = null;
let driver = null;
let offers = [];
let activeOrderId = null;
let activeOrder = null;
let history = [];
let offersUnsubscribe = null;
let historyUnsubscribe = null;
let driverUnsubscribe = null;
let orderUnsubscribe = null;
let refreshTimer = null;
let locationWatch = null;
let locationTimer = null;
let lastPosition = null;
let authVersion = 0;

const element = (tag, text, className) => {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
};
const money = (value) => `${Number(value || 0).toLocaleString("vi-VN")} VND`;
const dateOf = (order) =>
  order.createdAt && order.createdAt.toDate ? order.createdAt.toDate() : null;
const errorText = (error) =>
  error && (error.code || error.message)
    ? `${error.code || ""} ${error.message || ""}`.trim()
    : "Lỗi không xác định";

$("loginForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  message.textContent = "";
  if (
    !firebaseConfig.apiKey ||
    !firebaseConfig.appId ||
    firebaseConfig.apiKey === "REPLACE_ME" ||
    firebaseConfig.appId === "REPLACE_ME"
  ) {
    message.textContent =
      "Chưa điền cấu hình Firebase Web trong web/firebase-config.js.";
    return;
  }
  try {
    await signInWithEmailAndPassword(
      auth,
      $("email").value.trim(),
      $("password").value,
    );
  } catch (error) {
    const code = typeof error.code === "string" ? error.code : "unknown";
    message.textContent = `${loginErrors[code] || "Không thể đăng nhập."} (${code})`;
  }
});

$("logout").addEventListener("click", () => signOut(auth));
$("availability").addEventListener("click", async () => {
  if (!driver || !uid) return;
  $("availability").disabled = true;
  message.textContent = "";
  try {
    await updateDoc(doc(db, "drivers", uid), { available: !driver.available });
  } catch (error) {
    message.textContent = `Không đổi được trạng thái: ${errorText(error)}`;
  } finally {
    $("availability").disabled = false;
  }
});

function isTargeted(offer) {
  return Boolean(uid) && offer.targetDriverId === uid;
}

function isVisible(offer) {
  if (!offer.targetDriverId || offer.targetDriverId === uid) return true;
  const created =
    offer.createdAt && offer.createdAt.toDate
      ? offer.createdAt.toDate().getTime()
      : 0;
  return !created || Date.now() - created > 45000;
}

function publishLocation() {
  if (!uid || !driver || driver.approved !== true || !lastPosition) return;
  setDoc(doc(db, "driverLocations", uid), {
    latitude: lastPosition.latitude,
    longitude: lastPosition.longitude,
    available: driver.available === true && !driver.activeOrderId,
    updatedAt: serverTimestamp(),
  }).catch(() => {});
}

function startLocationTracking() {
  if (locationWatch !== null || !navigator.geolocation) return;
  locationWatch = navigator.geolocation.watchPosition(
    (position) => {
      lastPosition = {
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      };
      publishLocation();
    },
    (error) => {
      message.textContent =
        error && error.code === 1
          ? "Trình duyệt chặn quyền vị trí. Bật quyền vị trí để nhận đơn gần."
          : "Không lấy được vị trí. Kiểm tra GPS và kết nối.";
    },
    { enableHighAccuracy: true, maximumAge: 20000, timeout: 20000 },
  );
  if (locationTimer === null)
    locationTimer = setInterval(publishLocation, 20000);
}

function stopLocationTracking() {
  if (locationWatch !== null) navigator.geolocation.clearWatch(locationWatch);
  locationWatch = null;
  if (locationTimer !== null) clearInterval(locationTimer);
  locationTimer = null;
  lastPosition = null;
  if (uid) deleteDoc(doc(db, "driverLocations", uid)).catch(() => {});
}

window.addEventListener("beforeunload", () => {
  if (uid) deleteDoc(doc(db, "driverLocations", uid)).catch(() => {});
});

function renderDriver() {
  const approved = driver && driver.approved === true;
  const available = driver && driver.available === true;
  $("driverState").textContent = !driver
    ? "Chưa có hồ sơ"
    : !approved
      ? "Chờ duyệt"
      : available
        ? "Đang nhận đơn"
        : "Đang tắt";
  $("driverTitle").textContent = approved
    ? "Tài khoản tài xế"
    : "Tài khoản tài xế chờ duyệt";
  $("driverDetail").textContent = !driver
    ? "Không tìm thấy hồ sơ tài xế."
    : approved
      ? `${driver.vehicle || "Chưa có phương tiện"} · ${available ? "Đang nhận đơn" : "Tạm tắt nhận đơn"}`
      : "Tài khoản cần được duyệt trong Firebase Console.";
  const button = $("availability");
  button.hidden = !approved;
  button.disabled = !approved || Boolean(driver && driver.activeOrderId);
  button.textContent = available ? "Tắt nhận đơn" : "Bật nhận đơn";
}

function renderOffers() {
  const list = $("offers");
  list.replaceChildren();
  const visible = offers
    .filter(isVisible)
    .sort(
      (left, right) => Number(isTargeted(right)) - Number(isTargeted(left)),
    );
  if (!visible.length) {
    list.append(element("p", "Chưa có đơn đang chờ.", "empty"));
    return;
  }
  visible.forEach((offer) => {
    const card = element("article", undefined, "card");
    const title = element(
      "h3",
      `${isTargeted(offer) ? "Đơn ưu tiên cho bạn · " : ""}${offer.restaurantName || "Đơn hàng"}`,
    );
    const detail = element("p", `COD: ${money(offer.total)}`);
    const accept = element("button", "Nhận đơn");
    accept.type = "button";
    accept.addEventListener("click", () => acceptOffer(offer.id, accept));
    card.append(title, detail, accept);
    list.append(card);
  });
}

function renderActive() {
  const list = $("activeOrder");
  list.replaceChildren();
  if (!activeOrder) {
    list.append(element("p", "Chưa có đơn đang thực hiện.", "empty"));
    return;
  }
  const order = activeOrder;
  const card = element("article", undefined, "card");
  card.append(
    element(
      "span",
      statuses[order.status] || order.status || "",
      "order-status " + (order.status || ""),
    ),
  );
  card.append(
    element(
      "h3",
      `${order.restaurantName || "Đơn hàng"} · ${money(order.total)}`,
    ),
  );
  card.append(
    element(
      "p",
      `Người nhận: ${order.recipient || "Chưa có tên"} · ${order.phone || "Chưa có số điện thoại"}`,
    ),
  );
  card.append(element("p", `Địa chỉ: ${order.address || "Chưa có địa chỉ"}`));
  if (order.note) card.append(element("p", `Ghi chú: ${order.note}`));
  const items = element("ul");
  (Array.isArray(order.items) ? order.items : []).forEach((item) => {
    items.append(
      element(
        "li",
        `${item.name || "Món ăn"} × ${item.quantity || 0} · ${money(item.price)} / món`,
      ),
    );
  });
  if (!items.children.length)
    items.append(element("li", "Chưa có thông tin món"));
  card.append(items);

  if (order.phone) {
    const call = element("a", "Gọi người nhận", "button-link");
    call.href = `tel:${order.phone}`;
    card.append(call);
  }
  if (order.address) {
    const map = element("a", "Mở bản đồ", "button-link secondary-link");
    map.href = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(order.address)}`;
    map.target = "_blank";
    map.rel = "noopener";
    card.append(map);
  }

  const actions = element("div", undefined, "actions");
  if (order.status === "ACCEPTED") {
    const picked = element("button", "Đã lấy món");
    picked.type = "button";
    picked.addEventListener("click", () => advance("PICKED_UP", picked));
    const release = element("button", "Trả đơn", "secondary-button");
    release.type = "button";
    release.addEventListener("click", () => {
      if (confirm("Trả đơn này về danh sách chờ?")) releaseOrder(release);
    });
    actions.append(picked, release);
  } else if (order.status === "PICKED_UP") {
    const delivery = element("button", "Bắt đầu giao");
    delivery.type = "button";
    delivery.addEventListener("click", () => advance("DELIVERING", delivery));
    actions.append(delivery);
  } else if (order.status === "DELIVERING") {
    const complete = element("button", "Đã giao và thu tiền");
    complete.type = "button";
    complete.addEventListener("click", () => {
      if (confirm("Xác nhận đã giao hàng và thu tiền COD?"))
        completeOrder(complete);
    });
    actions.append(complete);
  }
  if (
    order.status === "ACCEPTED" ||
    order.status === "PICKED_UP" ||
    order.status === "DELIVERING"
  ) {
    const cancel = element("button", "Hủy đơn", "secondary-button");
    cancel.type = "button";
    cancel.addEventListener("click", () => {
      if (confirm("Hủy đơn này? Khách hàng sẽ thấy đơn đã hủy."))
        cancelOrder(cancel);
    });
    actions.append(cancel);
  }
  if (actions.children.length) card.append(actions);
  list.append(card);
}

function renderHistory() {
  const list = $("history");
  list.replaceChildren();
  const done = history
    .filter(
      (order) => order.status === "COMPLETED" || order.status === "CANCELLED",
    )
    .sort(
      (left, right) =>
        (dateOf(right)?.getTime() || 0) - (dateOf(left)?.getTime() || 0),
    );
  if (!done.length) {
    list.append(element("p", "Chưa có lịch sử giao hàng.", "empty"));
    return;
  }
  done.forEach((order) => {
    const card = element("article", undefined, "card");
    card.append(
      element(
        "span",
        statuses[order.status] || order.status || "",
        "order-status " + order.status,
      ),
    );
    card.append(
      element(
        "h3",
        `${order.restaurantName || "Đơn hàng"} · ${money(order.total)}`,
      ),
    );
    const created = dateOf(order);
    card.append(
      element(
        "p",
        `${created ? created.toLocaleString("vi-VN") : "Chưa có thời gian"} · ${order.address || "Chưa có địa chỉ"}`,
      ),
    );
    list.append(card);
  });
}

async function acceptOffer(orderId, button) {
  if (!uid) return;
  button.disabled = true;
  message.textContent = "";
  try {
    await runTransaction(db, async (transaction) => {
      const driverRef = doc(db, "drivers", uid);
      const offerRef = doc(db, "orderOffers", orderId);
      const driverSnapshot = await transaction.get(driverRef);
      const offerSnapshot = await transaction.get(offerRef);
      if (!driverSnapshot.exists())
        throw new Error("Không tìm thấy hồ sơ tài xế.");
      const currentDriver = driverSnapshot.data();
      if (
        currentDriver.approved !== true ||
        currentDriver.available !== true ||
        currentDriver.activeOrderId
      ) {
        throw new Error("Tài xế chưa sẵn sàng nhận đơn.");
      }
      if (
        !offerSnapshot.exists() ||
        offerSnapshot.data().status !== "SEARCHING"
      ) {
        throw new Error("Đơn đã được tài xế khác nhận.");
      }
      transaction.update(driverRef, { activeOrderId: orderId });
      transaction.update(doc(db, "orders", orderId), {
        status: "ACCEPTED",
        driverId: uid,
        updatedAt: serverTimestamp(),
      });
      transaction.update(offerRef, {
        status: "ACCEPTED",
        driverId: uid,
        updatedAt: serverTimestamp(),
      });
    });
  } catch (error) {
    message.textContent = `Không nhận được đơn: ${error.message}`;
    button.disabled = false;
  }
}

async function advance(status, button) {
  if (!activeOrder) return;
  button.disabled = true;
  message.textContent = "";
  try {
    await updateDoc(doc(db, "orders", activeOrder.id), {
      status,
      updatedAt: serverTimestamp(),
    });
  } catch (error) {
    message.textContent = `Không cập nhật được đơn: ${error.message}`;
    button.disabled = false;
  }
}

async function completeOrder(button) {
  if (!activeOrder || !uid) return;
  button.disabled = true;
  message.textContent = "";
  try {
    await runTransaction(db, async (transaction) => {
      const orderRef = doc(db, "orders", activeOrder.id);
      const orderSnapshot = await transaction.get(orderRef);
      if (!orderSnapshot.exists()) throw new Error("Đơn không còn tồn tại.");
      const order = orderSnapshot.data();
      if (order.status !== "DELIVERING" || order.driverId !== uid)
        throw new Error("Đơn đã thay đổi.");
      transaction.update(orderRef, {
        status: "COMPLETED",
        updatedAt: serverTimestamp(),
      });
      transaction.update(doc(db, "drivers", uid), {
        activeOrderId: deleteField(),
      });
      transaction.update(doc(db, "users", order.customerId), {
        activeOrderId: deleteField(),
      });
    });
  } catch (error) {
    message.textContent = `Không hoàn tất được đơn: ${error.message}`;
    button.disabled = false;
  }
}

async function cancelOrder(button) {
  if (!activeOrder || !uid) return;
  button.disabled = true;
  message.textContent = "";
  try {
    await runTransaction(db, async (transaction) => {
      const orderRef = doc(db, "orders", activeOrder.id);
      const offerRef = doc(db, "orderOffers", activeOrder.id);
      const driverRef = doc(db, "drivers", uid);
      const orderSnapshot = await transaction.get(orderRef);
      const driverSnapshot = await transaction.get(driverRef);
      if (!orderSnapshot.exists() || !driverSnapshot.exists())
        throw new Error("Đơn không còn tồn tại.");
      const order = orderSnapshot.data();
      if (
        !["ACCEPTED", "PICKED_UP", "DELIVERING"].includes(order.status) ||
        order.driverId !== uid ||
        driverSnapshot.data().activeOrderId !== activeOrder.id
      ) {
        throw new Error("Đơn đã thay đổi.");
      }
      transaction.update(orderRef, {
        status: "CANCELLED",
        updatedAt: serverTimestamp(),
      });
      transaction.update(offerRef, {
        status: "CANCELLED",
        updatedAt: serverTimestamp(),
      });
      transaction.update(driverRef, { activeOrderId: deleteField() });
      transaction.update(doc(db, "users", order.customerId), {
        activeOrderId: deleteField(),
      });
    });
  } catch (error) {
    message.textContent = `Không hủy được đơn: ${error.message}`;
    button.disabled = false;
  }
}

async function releaseOrder(button) {
  if (!activeOrder || !uid) return;
  button.disabled = true;
  message.textContent = "";
  try {
    await runTransaction(db, async (transaction) => {
      const orderRef = doc(db, "orders", activeOrder.id);
      const offerRef = doc(db, "orderOffers", activeOrder.id);
      const driverRef = doc(db, "drivers", uid);
      const orderSnapshot = await transaction.get(orderRef);
      const driverSnapshot = await transaction.get(driverRef);
      if (!orderSnapshot.exists() || !driverSnapshot.exists())
        throw new Error("Đơn không còn tồn tại.");
      const order = orderSnapshot.data();
      if (
        order.status !== "ACCEPTED" ||
        order.driverId !== uid ||
        driverSnapshot.data().activeOrderId !== activeOrder.id
      ) {
        throw new Error("Đơn đã thay đổi.");
      }
      transaction.update(orderRef, {
        status: "SEARCHING",
        driverId: deleteField(),
        updatedAt: serverTimestamp(),
      });
      transaction.update(offerRef, {
        status: "SEARCHING",
        driverId: deleteField(),
        updatedAt: serverTimestamp(),
      });
      transaction.update(driverRef, { activeOrderId: deleteField() });
    });
  } catch (error) {
    message.textContent = `Không trả được đơn: ${error.message}`;
    button.disabled = false;
  }
}

function cleanup() {
  stopLocationTracking();
  if (offersUnsubscribe) offersUnsubscribe();
  if (historyUnsubscribe) historyUnsubscribe();
  if (driverUnsubscribe) driverUnsubscribe();
  if (orderUnsubscribe) orderUnsubscribe();
  if (refreshTimer) clearInterval(refreshTimer);
  offersUnsubscribe =
    historyUnsubscribe =
    driverUnsubscribe =
    orderUnsubscribe =
      null;
  refreshTimer = null;
  offers = [];
  history = [];
  activeOrder = null;
  activeOrderId = null;
  driver = null;
  uid = null;
}

function watchActiveOrder(id) {
  if (id === activeOrderId && orderUnsubscribe) return;
  if (orderUnsubscribe) orderUnsubscribe();
  orderUnsubscribe = null;
  activeOrderId = id;
  activeOrder = null;
  if (!id) {
    renderActive();
    return;
  }
  orderUnsubscribe = onSnapshot(
    doc(db, "orders", id),
    (snapshot) => {
      activeOrder = snapshot.exists()
        ? { ...snapshot.data(), id: snapshot.id }
        : null;
      renderActive();
    },
    () => {
      message.textContent = "Không tải được đơn đang thực hiện.";
    },
  );
}

onAuthStateChanged(auth, async (user) => {
  const version = ++authVersion;
  cleanup();
  $("dashboard").hidden = true;
  $("login").hidden = true;
  $("logout").hidden = !user;
  $("driverState").textContent = "Đang tải...";
  if (!user) {
    message.textContent = "";
    $("login").hidden = false;
    return;
  }
  try {
    uid = user.uid;
    const profile = await getDoc(doc(db, "users", uid));
    if (version !== authVersion) return;
    if (!profile.exists() || profile.data().role !== "driver") {
      message.textContent = "Tài khoản này không có vai trò tài xế.";
      await signOut(auth);
      return;
    }
    driverUnsubscribe = onSnapshot(
      doc(db, "drivers", uid),
      (snapshot) => {
        if (!snapshot.exists()) {
          driver = null;
          renderDriver();
          return;
        }
        driver = snapshot.data();
        renderDriver();
        watchActiveOrder(driver.activeOrderId || null);
        if (
          driver.approved === true &&
          (driver.available === true || driver.activeOrderId)
        ) {
          startLocationTracking();
          publishLocation();
        } else {
          stopLocationTracking();
        }
        const canReceiveOffers =
          driver.approved === true &&
          driver.available === true &&
          !driver.activeOrderId;
        if (canReceiveOffers && !offersUnsubscribe) {
          offersUnsubscribe = onSnapshot(
            query(
              collection(db, "orderOffers"),
              where("status", "==", "SEARCHING"),
            ),
            (result) => {
              offers = result.docs.map((item) => ({
                ...item.data(),
                id: item.id,
              }));
              renderOffers();
              if (!refreshTimer)
                refreshTimer = setInterval(renderOffers, 15000);
            },
            () => {
              message.textContent =
                "Không tải được đơn chờ. Kiểm tra quyền tài xế.";
            },
          );
        } else if (!canReceiveOffers && offersUnsubscribe) {
          offersUnsubscribe();
          offersUnsubscribe = null;
          offers = [];
          if (refreshTimer) clearInterval(refreshTimer);
          refreshTimer = null;
          renderOffers();
        }
      },
      () => {
        message.textContent = "Không tải được hồ sơ tài xế.";
      },
    );
    historyUnsubscribe = onSnapshot(
      query(collection(db, "orders"), where("driverId", "==", uid)),
      (result) => {
        history = result.docs.map((item) => ({ ...item.data(), id: item.id }));
        renderHistory();
      },
      () => {
        message.textContent = "Không tải được lịch sử giao hàng.";
      },
    );
    message.textContent = "";
    $("dashboard").hidden = false;
    renderDriver();
    renderOffers();
    renderActive();
    renderHistory();
  } catch (error) {
    if (version === authVersion) {
      message.textContent = `Không thể kiểm tra tài khoản: ${errorText(error)}`;
      $("login").hidden = false;
    }
  }
});
