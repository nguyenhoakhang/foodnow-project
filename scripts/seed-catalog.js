const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getCatalog } = require("./catalog-data");

async function createIfMissing(reference, data) {
  try {
    await reference.create(data);
    return true;
  } catch (error) {
    if (error.code === 6 || error.code === "already-exists") return false;
    throw error;
  }
}

async function main() {
  const projectId = process.env.GCLOUD_PROJECT;
  if (projectId !== "nhk-foodnow") {
    throw new Error("Set GCLOUD_PROJECT=nhk-foodnow before running the seed.");
  }
  const options = { projectId };
  if (!process.env.FIRESTORE_EMULATOR_HOST)
    options.credential = applicationDefault();
  initializeApp(options);
  const db = getFirestore();
  let created = 0;
  for (const restaurant of getCatalog()) {
    const { id, foods, ...data } = restaurant;
    const ref = db.collection("restaurants").doc(id);
    if (await createIfMissing(ref, data)) created++;
    for (const food of foods) {
      const { id: foodId, ...foodData } = food;
      if (await createIfMissing(ref.collection("foods").doc(foodId), foodData))
        created++;
    }
  }
  if (await createIfMissing(db.doc("appConfig/public"), { deliveryFee: 15000 }))
    created++;
  console.log(
    `Created ${created} documents. Existing documents were preserved.`,
  );
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
