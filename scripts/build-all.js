const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "..");
const distWeb = path.join(root, "dist", "web");
const apk = path.join(
  root,
  "app",
  "build",
  "outputs",
  "apk",
  "debug",
  "app-debug.apk",
);
const args = process.argv.slice(2);
const withTests = args.includes("--with-tests");
const install = args.includes("--install");
const webOnly = args.includes("--web-only");
const isWindows = process.platform === "win32";

function run(command, commandArgs) {
  console.log(`\n> ${command} ${commandArgs.join(" ")}`);
  const needsShell = isWindows && /\.(bat|cmd)$/i.test(command);
  const result = needsShell
    ? spawnSync(
        process.env.ComSpec || "cmd.exe",
        ["/d", "/s", "/c", [command, ...commandArgs].join(" ")],
        { cwd: root, stdio: "inherit" },
      )
    : spawnSync(command, commandArgs, { cwd: root, stdio: "inherit" });
  if (result.status !== 0) {
    throw new Error(`${command} exited with code ${result.status}`);
  }
}

function checkJavaScript(file) {
  const result = spawnSync(process.execPath, ["--check", file], {
    cwd: root,
    stdio: "inherit",
  });
  if (result.status !== 0)
    throw new Error(`JavaScript syntax check failed: ${file}`);
}

function buildAndroid() {
  console.log("\n[1/3] Android customer + driver APK");
  const gradle = isWindows ? ".\\gradlew.bat" : "./gradlew";
  const gradleArgs = ["assembleDebug"];
  if (withTests) gradleArgs.push("testDebugUnitTest", "lintDebug");
  run(gradle, gradleArgs);
  if (!fs.existsSync(apk)) throw new Error(`APK not found: ${apk}`);
  if (install) run("adb", ["install", "-r", apk]);
  if (withTests) run(isWindows ? "npm.cmd" : "npm", ["test"]);
  return apk;
}

function buildWeb() {
  console.log("\n[2/3] Admin web + [3/3] Shipper web");
  const files = [
    "index.html",
    "admin.js",
    "shipper.html",
    "shipper.js",
    "style.css",
    "firebase-config.js",
  ];
  for (const file of files) {
    const source = path.join(root, "web", file);
    if (!fs.existsSync(source)) throw new Error(`Missing web file: ${source}`);
  }
  for (const file of ["admin.js", "shipper.js", "firebase-config.js"]) {
    checkJavaScript(path.join(root, "web", file));
  }
  for (const file of ["index.js", "notifications.js"]) {
    checkJavaScript(path.join(root, "functions", file));
  }

  fs.rmSync(distWeb, { recursive: true, force: true });
  fs.mkdirSync(distWeb, { recursive: true });
  for (const file of files) {
    fs.copyFileSync(path.join(root, "web", file), path.join(distWeb, file));
  }

  const config = fs.readFileSync(
    path.join(distWeb, "firebase-config.js"),
    "utf8",
  );
  if (config.includes("REPLACE_ME")) {
    console.warn(
      "\nWARNING: web/firebase-config.js still contains REPLACE_ME.",
    );
  } else {
    const appId = (config.match(/appId:\s*"([^"]+)"/) || [])[1] || "";
    if (!/^1:\d+:(web|android):/.test(appId)) {
      console.warn(
        "\nWARNING: Firebase appId should be a Web app ID such as 1:...:web:...",
      );
    }
  }

  fs.writeFileSync(
    path.join(distWeb, "build-info.json"),
    JSON.stringify(
      {
        builtAt: new Date().toISOString(),
        modules: ["admin-web", "shipper-web"],
        firebaseProject: "nhk-foodnow",
      },
      null,
      2,
    ),
  );
  return distWeb;
}

function main() {
  const android = webOnly ? null : buildAndroid();
  const web = buildWeb();
  console.log("\nBuild complete");
  if (android) console.log(`Android customer/driver: ${android}`);
  console.log(`Admin web:               ${path.join(web, "index.html")}`);
  console.log(`Shipper web:             ${path.join(web, "shipper.html")}`);
}

try {
  main();
} catch (error) {
  console.error(`\nBUILD FAILED: ${error.message}`);
  process.exitCode = 1;
}
