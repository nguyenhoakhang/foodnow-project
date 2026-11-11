const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");

async function main() {
  const projectId = process.env.GCLOUD_PROJECT;
  if (projectId !== "nhk-foodnow") {
    throw new Error(
      "Set GCLOUD_PROJECT=nhk-foodnow before granting admin access.",
    );
  }
  const email = process.argv[2];
  if (!email) {
    throw new Error("Usage: npm run admin:claim -- admin@example.com");
  }
  initializeApp({ projectId, credential: applicationDefault() });
  const auth = getAuth();
  const user = await auth.getUserByEmail(email);
  await auth.setCustomUserClaims(user.uid, {
    ...(user.customClaims || {}),
    admin: true,
  });
  console.log(
    `Granted admin access to ${email}. Sign out and sign in again on the web dashboard.`,
  );
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
