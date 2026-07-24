const copyButton = document.querySelector("[data-copy-checksum]");
const checksumValue = document.querySelector("#checksum-value");
const copyStatus = document.querySelector("[data-copy-status]");
const year = document.querySelector("[data-current-year]");
const versionHistory = document.querySelector("[data-version-history]");

const isTvBrowser = /AFT|Silk\/|Downloader/i.test(navigator.userAgent);
const isHomePage = /\/(?:index\.html)?$/.test(window.location.pathname);

if (isTvBrowser && isHomePage && !sessionStorage.getItem("skipTvView")) {
  window.location.replace("./tv.html");
}

if (year) {
  year.textContent = new Date().getFullYear();
}

const formatSize = (bytes) => `${(bytes / 1024 / 1024).toFixed(2)} MB`;

const formatDate = (date) =>
  new Intl.DateTimeFormat("en-US", {
    month: "long",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));

const applyLatestRelease = (release) => {
  document.querySelectorAll("[data-latest-version]").forEach((element) => {
    element.textContent = release.versionName;
  });
  document.querySelectorAll("[data-latest-size]").forEach((element) => {
    element.textContent = formatSize(release.sizeBytes);
  });
  document.querySelectorAll("[data-latest-date]").forEach((element) => {
    element.textContent = formatDate(release.releaseDate);
  });
  document.querySelectorAll("[data-latest-download]").forEach((link) => {
    link.href = release.apkUrl;
    link.download = `StreamDeck-IPTV-${release.versionName}.apk`;
  });
  if (checksumValue) {
    checksumValue.textContent = release.sha256.toUpperCase();
  }
};

const renderHistory = (releases, latestVersion) => {
  if (!versionHistory) return;
  versionHistory.replaceChildren();

  releases.forEach((release) => {
    const article = document.createElement("article");
    const isLatest = release.versionName === latestVersion;
    article.className = `version-entry${isLatest ? " version-current" : ""}`;

    const heading = document.createElement("div");
    heading.className = "version-heading";

    const titleGroup = document.createElement("div");
    const label = document.createElement("p");
    label.className = "card-label";
    label.textContent = isLatest ? "Latest stable release" : "Previous release";
    const title = document.createElement("h3");
    title.textContent = `Version ${release.versionName}`;
    titleGroup.append(label, title);
    heading.append(titleGroup);

    if (isLatest) {
      const badge = document.createElement("span");
      badge.className = "release-badge";
      badge.textContent = "Latest";
      heading.append(badge);
    }

    const date = document.createElement("p");
    date.className = "version-date";
    date.textContent = formatDate(release.releaseDate);

    const notes = document.createElement("ul");
    release.notes.forEach((note) => {
      const item = document.createElement("li");
      item.textContent = note;
      notes.append(item);
    });

    article.append(heading, date, notes);
    if (release.apkUrl) {
      const download = document.createElement("a");
      download.className = "version-download";
      download.href = release.apkUrl;
      download.textContent = `Download ${release.versionName}`;
      article.append(download);
    }
    versionHistory.append(article);
  });
};

const loadReleaseData = async () => {
  try {
    const [latestResponse, historyResponse] = await Promise.all([
      fetch("./versions/latest.json", { cache: "no-store" }),
      fetch("./versions/history.json", { cache: "no-store" }),
    ]);
    if (!latestResponse.ok || !historyResponse.ok) {
      throw new Error("Release metadata unavailable");
    }
    const latest = await latestResponse.json();
    const history = await historyResponse.json();
    applyLatestRelease(latest);
    renderHistory(history.releases, history.latest);
  } catch {
    // The page contains a complete static latest-release fallback.
  }
};

loadReleaseData();

if (copyButton && checksumValue && copyStatus) {
  copyButton.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(checksumValue.textContent.trim());
      copyButton.textContent = "Copied";
      copyStatus.textContent = "Checksum copied to clipboard.";
    } catch {
      copyStatus.textContent = "Select the checksum text and copy it manually.";
    }

    window.setTimeout(() => {
      copyButton.textContent = "Copy";
      copyStatus.textContent = "";
    }, 2400);
  });
}
