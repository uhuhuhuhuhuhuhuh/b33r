# APK download site

A responsive, build-free GitHub Pages site for distributing StreamDeck IPTV.
Large APKs are published as GitHub Release assets rather than committed to the
Pages repository.

## Customize before publishing

1. The site is branded for StreamDeck IPTV.
2. Publish each APK as a GitHub Release asset named
   `StreamDeck-IPTV-<version>.apk`.
3. Update `versions/latest.json`, `versions/history.json`, and the matching
   `versions/<version>/release.json` entry whenever the APK changes.
4. The website reads those files to show the latest release and visible
   version history. The Android app also checks `versions/latest.json`.
5. The custom domain is set to `download.b33r.top` in `CNAME`.
   Update or delete `CNAME` if the domain changes.
6. Update the description and social metadata in `index.html` when needed.

## Calculate the checksum

Windows PowerShell:

```powershell
Get-FileHash .\StreamDeck-IPTV-1.9.1.apk -Algorithm SHA256
```

macOS or Linux:

```sh
shasum -a 256 StreamDeck-IPTV-1.9.1.apk
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
- The shortest route is `https://download.b33r.top`; the TV page resolves the
  latest GitHub Release asset from `versions/latest.json`.
- After the real custom domain is live, you can use Downloader's built-in
  short-code/URL-shortening option to make the address even easier to enter.
- Point the `download.b33r.top` DNS record to the value GitHub shows in the
  repository's Pages settings.
