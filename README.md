# Haalandent widget

Android-app (Samsung/One UI) med hjemskjerm-widgets for modulene på haalandent.no.
Appen tegner widgets generelt ut fra **widget-kontrakt v1** (`claude/kjoreregler.md`
avsnitt 9 i prosjektet). Nye moduler krever ingen endring her, bare at modulen leverer
`/api/widget/v1`.

- Kotlin, Jetpack Glance (widgets), Compose (oppsett), WorkManager (henting hvert 15. min,
  ved `gyldig_til`, ved trykk og når appen åpnes).
- Token kryptert med AES-GCM-nøkkel i Android Keystore.
- QR skannes i appen (zxing-android-embedded) eller åpnes som lenke
  `haalandent-widget://legg-til?url=...&kode=wpk_...`.
- Innebygd demo-modul (`url=demo`) med oppdiktede data for test uten server.

## Bygg og utgivelse

- Push til `main`: GitHub Actions bygger debug (kompileringssjekk).
- Tag `vX.Y.Z`: Actions bygger signert release-APK og legger den på GitHub Releases.
  `versionCode = X*10000 + Y*100 + Z`. Obtainium henter derfra.

## Signeringsnøkkel (én gang, lokalt hos Karl)

Nøkkelen lages lokalt og ligger bare i GitHub Actions secrets og i passordbehandleren.
Mister du den, kan appen ikke oppdateres (må avinstalleres og settes opp på nytt).

På boksen (eller annen Linux med openssl):

```sh
cd ~ && umask 077
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes \
  -keyout widget-key.pem -out widget-cert.pem -days 10000 -subj "/CN=haalandent widget"
openssl pkcs12 -export -inkey widget-key.pem -in widget-cert.pem -name widget -out widget.p12
base64 -w0 widget.p12 > widget.p12.b64
```

`pkcs12 -export` spør om passord: lag et sterkt og legg det i passordbehandleren.

GitHub → `haalandent/widget` → Settings → Secrets and variables → Actions → New repository secret:

| Navn | Innhold |
|---|---|
| `WIDGET_KEYSTORE_B64` | innholdet i `widget.p12.b64` (én lang linje) |
| `WIDGET_KEYSTORE_PASSWORD` | passordet |

Legg `widget.p12` + passord i passordbehandleren, og slett filene på boksen:
`rm ~/widget-key.pem ~/widget-cert.pem ~/widget.p12 ~/widget.p12.b64`.

## Obtainium

Legg til app → URL `https://github.com/haalandent/widget` → kilde GitHub.
(Er repoet privat: legg inn et fine-grained GitHub-token med lesetilgang til
`haalandent/widget` under Obtainium → Innstillinger → GitHub.)
