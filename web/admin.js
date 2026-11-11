import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  getAuth,
  signInWithEmailAndPassword,
  signOut,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  onSnapshot,
  updateDoc,
  setDoc,
  deleteDoc,
  serverTimestamp,
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
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

let orders = [];
let drivers = [];
let users = [];
let restaurants = [];
const foodsByRestaurant = new Map();
let adminUnsubs = [];
let unsubscribe;
let authVersion = 0;

const money = (value) => `${Number(value || 0).toLocaleString("vi-VN")} VND`;
const dateOf = (order) =>
  order.createdAt && order.createdAt.toDate ? order.createdAt.toDate() : null;
const element = (tag, text, className) => {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
};

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

function renderMetrics() {
  const completed = orders.filter((order) => order.status === "COMPLETED");
  const active = orders.filter((order) =>
    ["ACCEPTED", "PICKED_UP", "DELIVERING"].includes(order.status),
  );
  const cards = [
    [
      "Tổng đơn hàng",
      orders.length.toLocaleString("vi-VN"),
      "Tất cả đơn đã ghi nhận",
    ],
    [
      "Đang tìm tài xế",
      orders
        .filter((order) => order.status === "SEARCHING")
        .length.toLocaleString("vi-VN"),
      "Chờ tài xế nhận đơn",
    ],
    [
      "Đang giao",
      active.length.toLocaleString("vi-VN"),
      "Đã nhận, đang trên đường",
    ],
    [
      "Giá trị hoàn tất",
      money(
        completed.reduce((sum, order) => sum + (Number(order.total) || 0), 0),
      ),
      "Tổng COD các đơn hoàn tất",
    ],
  ];
  $("metrics").replaceChildren();
  cards.forEach(([title, value, description]) => {
    const card = element("article", undefined, "metric");
    card.append(
      element("span", title),
      element("strong", value),
      element("small", description),
    );
    $("metrics").append(card);
  });
}

function renderStatusSummary() {
  const list = $("statusSummary");
  list.replaceChildren();
  Object.entries(statuses).forEach(([key, label]) => {
    const count = orders.filter((order) => order.status === key).length;
    const row = element("div", undefined, "status-row");
    const heading = element("div", undefined, "status-heading");
    heading.append(element("span", label), element("strong", `${count} đơn`));
    const track = element("div", undefined, "progress-track");
    const fill = element("div", undefined, `progress-fill ${key}`);
    fill.style.width = `${orders.length ? (count / orders.length) * 100 : 0}%`;
    track.append(fill);
    row.append(heading, track);
    list.append(row);
  });
}

function renderChart() {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const days = [];
  for (let i = 6; i >= 0; i--) {
    const date = new Date(today);
    date.setDate(today.getDate() - i);
    const next = new Date(date);
    next.setDate(date.getDate() + 1);
    days.push({
      date,
      count: orders.filter((order) => {
        const created = dateOf(order);
        return created && created >= date && created < next;
      }).length,
    });
  }
  const max = Math.max(1, ...days.map((day) => day.count));
  $("chart").replaceChildren();
  days.forEach((day) => {
    const column = element("div", undefined, "chart-column");
    const value = element("span", String(day.count), "bar-value");
    const area = element("div", undefined, "bar-area");
    const bar = element("div", undefined, "bar");
    bar.style.height = `${Math.max(day.count ? 4 : 1, (day.count / max) * 100)}%`;
    bar.title = `${day.count} đơn`;
    area.append(bar);
    const label = element(
      "span",
      `${String(day.date.getDate()).padStart(2, "0")}/${String(day.date.getMonth() + 1).padStart(2, "0")}`,
      "bar-label",
    );
    column.append(value, area, label);
    $("chart").append(column);
  });
}

function renderOrders() {
  const term = $("search").value.trim().toLocaleLowerCase("vi");
  const filter = $("statusFilter").value;
  const filtered = orders.filter((order) => {
    if (filter !== "all" && order.status !== filter) return false;
    const values = [
      order.id,
      order.recipient,
      order.phone,
      order.address,
      order.restaurantName,
    ];
    return values.some((value) =>
      String(value || "")
        .toLocaleLowerCase("vi")
        .includes(term),
    );
  });

  $("orders").replaceChildren();
  $("count").textContent =
    `Hiển thị ${filtered.length} / ${orders.length} đơn hàng`;
  $("empty").hidden = filtered.length > 0;

  filtered.forEach((order) => {
    const row = element("tr");
    const orderCell = element("td");
    orderCell.append(
      element("strong", `#${order.id.slice(0, 8)}`),
      element("small", order.restaurantName || "Chưa có cửa hàng"),
    );
    const recipientCell = element("td");
    recipientCell.append(
      element("strong", order.recipient || "Chưa có tên"),
      element("small", order.phone || "Chưa có số điện thoại"),
    );
    const created = dateOf(order);
    const dateCell = element(
      "td",
      created
        ? created.toLocaleString("vi-VN", {
            day: "2-digit",
            month: "2-digit",
            year: "numeric",
            hour: "2-digit",
            minute: "2-digit",
          })
        : "Chưa có thời gian",
    );
    const totalCell = element("td", money(order.total), "amount");
    const statusCell = element("td");
    statusCell.append(
      element(
        "span",
        statuses[order.status] || "Khác",
        `order-status ${order.status || ""}`,
      ),
    );
    const detailCell = element("td");
    const toggle = element("button", "Chi tiết", "detail-button");
    toggle.type = "button";
    toggle.setAttribute("aria-expanded", "false");
    detailCell.append(toggle);
    row.append(
      orderCell,
      recipientCell,
      dateCell,
      totalCell,
      statusCell,
      detailCell,
    );
    $("orders").append(row);

    const detailRow = element("tr", undefined, "detail-row");
    detailRow.hidden = true;
    const detailCellFull = element("td");
    detailCellFull.colSpan = 6;
    const box = element("div", undefined, "detail-content");
    box.append(
      element("strong", `Mã đơn: ${order.id}`),
      element("p", `Địa chỉ: ${order.address || "Chưa có địa chỉ"}`),
      element(
        "p",
        `Người nhận: ${order.recipient || "Chưa có thông tin"} · ${order.phone || "Chưa có số điện thoại"}`,
      ),
      element("p", `Ghi chú: ${order.note || "Không có"}`),
    );
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
    box.append(items);
    detailCellFull.append(box);
    detailRow.append(detailCellFull);
    $("orders").append(detailRow);

    toggle.addEventListener("click", () => {
      detailRow.hidden = !detailRow.hidden;
      toggle.setAttribute("aria-expanded", String(!detailRow.hidden));
      toggle.textContent = detailRow.hidden ? "Chi tiết" : "Thu gọn";
    });
  });
}

$("search").addEventListener("input", renderOrders);
$("statusFilter").addEventListener("change", renderOrders);

const userName = (id) =>
  (users.find((item) => item.id === id) || {}).name || id.slice(0, 8);
const foodWatchIds = new Set();

function renderDrivers() {
  const list = $("adminDrivers");
  list.replaceChildren();
  if (!drivers.length) {
    list.append(element("p", "Chưa có tài xế.", "empty"));
    return;
  }
  drivers
    .slice()
    .sort((left, right) => Number(left.approved) - Number(right.approved))
    .forEach((driver) => {
      const card = element("article", undefined, "card");
      card.append(element("h3", userName(driver.id)));
      card.append(
        element(
          "p",
          `${driver.vehicle || "Chưa có phương tiện"} · ${driver.available ? "Đang nhận đơn" : "Đang tắt"}`,
        ),
      );
      card.append(
        element(
          "span",
          driver.approved ? "Đã duyệt" : "Chờ duyệt",
          `order-status ${driver.approved ? "COMPLETED" : "SEARCHING"}`,
        ),
      );
      const button = element(
        "button",
        driver.approved ? "Tạm khóa" : "Duyệt tài xế",
        driver.approved ? "secondary-button" : "",
      );
      button.type = "button";
      button.addEventListener("click", async () => {
        button.disabled = true;
        try {
          await updateDoc(doc(db, "drivers", driver.id), {
            approved: !driver.approved,
          });
        } catch (error) {
          message.textContent = `Không cập nhật được tài xế: ${error.message}`;
          button.disabled = false;
        }
      });
      const actions = element("div", undefined, "actions");
      actions.append(button);
      card.append(actions);
      list.append(card);
    });
}

function renderUsers() {
  const list = $("adminUsers");
  list.replaceChildren();
  if (!users.length) {
    list.append(element("p", "Chưa có người dùng.", "empty"));
    return;
  }
  users
    .slice()
    .sort((left, right) =>
      String(left.role || "").localeCompare(String(right.role || "")),
    )
    .forEach((user) => {
      const card = element("article", undefined, "card");
      card.append(
        element(
          "h3",
          `${user.name || "Chưa có tên"} · ${user.role || "Không rõ"}`,
        ),
      );
      card.append(
        element(
          "p",
          `${user.phone || "Chưa có số điện thoại"} · ${user.address || "Chưa có địa chỉ"}`,
        ),
      );
      card.append(element("p", `UID: ${user.id}`));
      list.append(card);
    });
}

function renderRestaurants() {
  const list = $("adminRestaurants");
  list.replaceChildren();
  if (!restaurants.length) {
    list.append(element("p", "Chưa có cửa hàng.", "empty"));
    return;
  }
  restaurants.forEach((restaurant) => {
    const card = element("article", undefined, "card");
    card.append(
      element(
        "h3",
        `${restaurant.name || restaurant.id} · ${restaurant.active ? "Đang mở" : "Tạm đóng"}`,
      ),
    );
    card.append(
      element(
        "p",
        `${restaurant.address || ""} · ${restaurant.openTime || "--:--"} - ${restaurant.closeTime || "--:--"}`,
      ),
    );
    const actions = element("div", undefined, "actions");
    const toggle = element(
      "button",
      restaurant.active ? "Tạm đóng cửa hàng" : "Mở cửa hàng",
      "secondary-button",
    );
    toggle.type = "button";
    toggle.addEventListener("click", () => toggleRestaurant(restaurant));
    const add = element("button", "Thêm món");
    add.type = "button";
    add.addEventListener("click", () => addFood(restaurant.id));
    actions.append(toggle, add);
    card.append(actions);

    const foods = foodsByRestaurant.get(restaurant.id) || [];
    if (!foods.length) card.append(element("p", "Chưa có món.", "empty"));
    foods
      .slice()
      .sort((left, right) => (left.name || "").localeCompare(right.name || ""))
      .forEach((food) => {
        const row = element("div", undefined, "food-row");
        const info = element("div", undefined, "food-info");
        info.append(element("strong", food.name || food.id));
        info.append(
          element(
            "small",
            `${money(food.price)} · ${food.available ? "Đang bán" : "Tạm hết"}`,
          ),
        );
        const foodActions = element("div", undefined, "actions");
        const edit = element("button", "Sửa", "secondary-button");
        edit.type = "button";
        edit.addEventListener("click", () => editFood(restaurant.id, food));
        const availability = element(
          "button",
          food.available ? "Tạm hết" : "Mở bán",
          "secondary-button",
        );
        availability.type = "button";
        availability.addEventListener("click", () =>
          toggleFood(restaurant.id, food),
        );
        const remove = element("button", "Xóa", "secondary-button");
        remove.type = "button";
        remove.addEventListener("click", () => deleteFood(restaurant.id, food));
        foodActions.append(edit, availability, remove);
        row.append(info, foodActions);
        card.append(row);
      });
    list.append(card);
  });
}

async function toggleRestaurant(restaurant) {
  try {
    await updateDoc(doc(db, "restaurants", restaurant.id), {
      active: !restaurant.active,
    });
  } catch (error) {
    message.textContent = `Không cập nhật được cửa hàng: ${error.message}`;
  }
}

async function toggleFood(restaurantId, food) {
  try {
    await updateDoc(doc(db, "restaurants", restaurantId, "foods", food.id), {
      available: !food.available,
    });
  } catch (error) {
    message.textContent = `Không cập nhật được món: ${error.message}`;
  }
}

async function editFood(restaurantId, food) {
  const name = window.prompt("Tên món", food.name || "");
  if (name === null || !name.trim()) return;
  const price = Number(window.prompt("Giá (VND)", String(food.price || 0)));
  if (!Number.isSafeInteger(price) || price < 1) {
    message.textContent = "Giá không hợp lệ.";
    return;
  }
  try {
    await updateDoc(doc(db, "restaurants", restaurantId, "foods", food.id), {
      name: name.trim(),
      price,
    });
  } catch (error) {
    message.textContent = `Không sửa được món: ${error.message}`;
  }
}

async function addFood(restaurantId) {
  const id = window.prompt("Mã món (chữ, số, gạch ngang)");
  if (id === null || !/^[a-zA-Z0-9_-]{1,60}$/.test(id.trim())) return;
  const name = window.prompt("Tên món");
  if (name === null || !name.trim()) return;
  const price = Number(window.prompt("Giá (VND)", "25000"));
  if (!Number.isSafeInteger(price) || price < 1) {
    message.textContent = "Giá không hợp lệ.";
    return;
  }
  const image =
    window.prompt(
      "Ảnh: ic_banh_mi / ic_black_coffee / ic_milk_tea",
      "ic_banh_mi",
    ) || "ic_banh_mi";
  try {
    const foodRef = doc(db, "restaurants", restaurantId, "foods", id.trim());
    if ((await getDoc(foodRef)).exists()) {
      message.textContent = "Mã món đã tồn tại. Chọn mã khác.";
      return;
    }
    await setDoc(foodRef, {
      name: name.trim(),
      price,
      image,
      available: true,
      createdAt: serverTimestamp(),
    });
  } catch (error) {
    message.textContent = `Không thêm được món: ${error.message}`;
  }
}

async function deleteFood(restaurantId, food) {
  if (!window.confirm(`Xóa món ${food.name || food.id}?`)) return;
  try {
    await deleteDoc(doc(db, "restaurants", restaurantId, "foods", food.id));
  } catch (error) {
    message.textContent = `Không xóa được món: ${error.message}`;
  }
}

function watchFoods(restaurantId) {
  if (foodWatchIds.has(restaurantId)) return;
  foodWatchIds.add(restaurantId);
  adminUnsubs.push(
    onSnapshot(
      collection(db, "restaurants", restaurantId, "foods"),
      (snapshot) => {
        foodsByRestaurant.set(
          restaurantId,
          snapshot.docs.map((item) => ({ ...item.data(), id: item.id })),
        );
        renderRestaurants();
      },
      () => {
        message.textContent = "Không tải được thực đơn.";
      },
    ),
  );
}

function watchAdminData() {
  adminUnsubs.push(
    onSnapshot(
      collection(db, "drivers"),
      (snapshot) => {
        drivers = snapshot.docs.map((item) => ({
          ...item.data(),
          id: item.id,
        }));
        renderDrivers();
      },
      () => {
        message.textContent = "Không tải được tài xế.";
      },
    ),
  );
  adminUnsubs.push(
    onSnapshot(
      collection(db, "users"),
      (snapshot) => {
        users = snapshot.docs.map((item) => ({ ...item.data(), id: item.id }));
        renderUsers();
        renderDrivers();
      },
      () => {
        message.textContent = "Không tải được người dùng.";
      },
    ),
  );
  adminUnsubs.push(
    onSnapshot(
      collection(db, "restaurants"),
      (snapshot) => {
        restaurants = snapshot.docs.map((item) => ({
          ...item.data(),
          id: item.id,
        }));
        restaurants.forEach((item) => watchFoods(item.id));
        renderRestaurants();
      },
      () => {
        message.textContent = "Không tải được danh mục.";
      },
    ),
  );
  renderDrivers();
  renderUsers();
  renderRestaurants();
}

function clearAdminData() {
  adminUnsubs.forEach((stop) => stop());
  adminUnsubs = [];
  foodWatchIds.clear();
  foodsByRestaurant.clear();
  drivers = [];
  users = [];
  restaurants = [];
}

onAuthStateChanged(auth, async (user) => {
  const version = ++authVersion;
  if (unsubscribe) unsubscribe();
  unsubscribe = null;
  clearAdminData();
  orders = [];
  $("dashboard").hidden = true;
  $("login").hidden = true;
  $("logout").hidden = !user;
  if (!user) {
    $("login").hidden = false;
    return;
  }
  try {
    const token = await user.getIdTokenResult(true);
    if (version !== authVersion) return;
    if (token.claims.admin !== true) {
      message.textContent = "Tài khoản này chưa được cấp quyền quản trị.";
      await signOut(auth);
      return;
    }
    message.textContent = "";
    $("dashboard").hidden = false;
    renderMetrics();
    renderStatusSummary();
    renderChart();
    renderOrders();
    $("count").textContent = "Đang tải dữ liệu...";
    watchAdminData();
    unsubscribe = onSnapshot(
      collection(db, "orders"),
      (snapshot) => {
        orders = snapshot.docs
          .map((doc) => ({ ...doc.data(), id: doc.id }))
          .sort(
            (left, right) =>
              (dateOf(right)?.getTime() || 0) - (dateOf(left)?.getTime() || 0),
          );
        message.textContent = "";
        renderMetrics();
        renderStatusSummary();
        renderChart();
        renderOrders();
      },
      () => {
        message.textContent =
          "Không tải được đơn hàng. Kiểm tra quyền admin và Firestore Rules.";
      },
    );
  } catch (error) {
    if (version === authVersion) {
      message.textContent = "Không thể kiểm tra quyền quản trị.";
      $("login").hidden = false;
    }
  }
});
