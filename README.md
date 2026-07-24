# APK download site

A responsive, build-free GitHub Pages site for distributing an Android APK.
No APK is included.

## Customize before publishing

1. The site is branded for StreamDeck IPTV version 1.3.
2. Update the version, release date, file size, minimum Android version, and
   SHA-256 checksum whenever the APK changes.
3. Place your APK at `app.apk`, or update the download link
   in `index.html` to point to a GitHub Release asset.
4. The custom domain is set to `download.b33r.top` in `CNAME`.
   Update or delete `CNAME` if the domain changes.
5. Update the description and social metadata in `index.html`.

## Calculate the checksum

Windows PowerShell:

```powershell
Get-FileHash .\downloads\app-release.apk -Algorithm SHA256
```

macOS or Linux:

```sh
shasum -a 256 downloads/app-release.apk
```

## Publish with GitHub Pages

1. Create a GitHub repository and upload these files to its default branch.
2. In the repository, open **Settings → Pages**.
3. Under **Build and deployment**, select **Deploy from a branch**.
4. Select the default branch and `/ (root)`, then save.
5. If using a custom domain, configure its DNS records as shown by GitHub and
   enable **Enforce HTTPS** after the certificate is ready.

The `.nojekyll` file tells GitHub Pages to publish the folder exactly as-is.

## Fire TV and Downloader

- The main page automatically sends common Fire TV, Silk, and Downloader user
  agents to `tv.html`, which has one large remote-friendly download button.
- The shortest route is the direct APK address:
  `https://download.b33r.top/app.apk`.
- After the real custom domain is live, you can use Downloader's built-in
  short-code/URL-shortening option to make the address even easier to enter.
- Point the `download.b33r.top` DNS record to the value GitHub shows in the
  repository's Pages settings.
