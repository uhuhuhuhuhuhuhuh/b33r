const copyButton = document.querySelector("[data-copy-checksum]");
const checksumValue = document.querySelector("#checksum-value");
const copyStatus = document.querySelector("[data-copy-status]");
const year = document.querySelector("[data-current-year]");

const isTvBrowser = /AFT|Silk\/|Downloader/i.test(navigator.userAgent);
const isHomePage = /\/(?:index\.html)?$/.test(window.location.pathname);

if (isTvBrowser && isHomePage && !sessionStorage.getItem("skipTvView")) {
  window.location.replace("./tv.html");
}

if (year) {
  year.textContent = new Date().getFullYear();
}

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
