const { initializeApp } = require("firebase-admin/app");
const { notifyOrderUpdated, notifyPriorityOffer } = require("./notifications");

initializeApp();
exports.notifyOrderUpdated = notifyOrderUpdated;
exports.notifyPriorityOffer = notifyPriorityOffer;
