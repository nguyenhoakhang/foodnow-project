const restaurants = [
  {
    id: "seven",
    name: "7 Eleven",
    address: "82 Nguyễn Thị Minh Khai, Quận 3",
    image: "ic_seveneleven",
  },
  {
    id: "circlek",
    name: "Circle K",
    address: "Nguyễn Thị Minh Khai, Quận 1",
    image: "ic_circlek",
  },
  {
    id: "ministop",
    name: "Ministop",
    address: "Võ Văn Tần, Quận 3",
    image: "ic_ministop",
  },
  {
    id: "vinmart",
    name: "VinMart",
    address: "Cách Mạng Tháng Tám, Quận 3",
    image: "ic_vinmart",
  },
  {
    id: "highlands",
    name: "Highlands Coffee",
    address: "Nguyễn Đình Chiểu, Quận 3",
    image: "ic_highland",
    active: false,
  },
];

const foods = [
  { id: "banh-mi", name: "Bánh mì thịt", price: 39000, image: "ic_banh_mi" },
  { id: "banh-mi-ga", name: "Bánh mì gà", price: 35000, image: "ic_banh_mi" },
  {
    id: "ca-phe-den",
    name: "Cà phê đen",
    price: 25000,
    image: "ic_black_coffee",
  },
  {
    id: "ca-phe-sua",
    name: "Cà phê sữa",
    price: 29000,
    image: "ic_black_coffee",
  },
  { id: "tra-sua", name: "Trà sữa", price: 35000, image: "ic_milk_tea" },
  {
    id: "tra-sua-tran-chau",
    name: "Trà sữa trân châu",
    price: 45000,
    image: "ic_milk_tea",
    available: false,
  },
];

function getCatalog() {
  return restaurants.map((restaurant) => ({
    ...restaurant,
    active: restaurant.active !== false,
    cover: "cover_menu_1",
    timezone: "Asia/Ho_Chi_Minh",
    openTime: "06:00",
    closeTime: "23:00",
    foods: foods.map((food) => ({
      ...food,
      available: food.available !== false,
    })),
  }));
}

module.exports = { getCatalog };
