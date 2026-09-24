# Ubi-Network Scanner — Android (Kotlin + Jetpack Compose)

Pravi skener lokalne mreže za Android. **Nije simulacija** — koristi:

- `InetAddress.isReachable()` (ICMP echo) + TCP connect fallback za otkrivanje živih hostova
- paralelno skeniranje (do 96 niti) celog /24 opsega
- **reverse DNS / mDNS** za hostname
- čitanje kernel **ARP tabele** (`/proc/net/arp`) za MAC adrese
- **OUI bazu** za prepoznavanje proizvođača (Ubiquiti, Apple, Espressif, TP-Link…)
- **TCP port scan** preko 30+ poznatih portova (ssh, http, smb, rtsp, mqtt, rdp…)
- auto-detekciju tvog subneta preko `ConnectivityManager` + unos **custom segmenta**
  (`192.168.1.0/24`, `10.0.0.5-60`, pojedinačni IP, više odvojenih zarezom)

## Kako dobiti APK

### Opcija A — GitHub Actions (bez instaliranja ičega)
1. Pushuj ovaj repozitorijum na GitHub.
2. Workflow `.github/workflows/android.yml` se pokreće automatski (ili ručno: Actions → *Build Ubi-Network Scanner APK* → Run workflow).
3. Kad završi, skini artifact **UbiNetworkScanner-debug-apk** → unutra je `app-debug.apk`.
4. Prebaci na telefon i instaliraj (dozvoli "Instaliranje iz nepoznatih izvora").

### Opcija B — lokalno
```bash
cd android-app
# treba JDK 17 + Android SDK (Android Studio)
./gradlew assembleDebug        # ili: gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Opcija C — Android Studio
`File → Open → android-app`, sačekaj Gradle sync, pa `Build → Build APK(s)`.

## Napomene
- Za prikaz SSID-a Android traži dozvolu za lokaciju — aplikacija je traži pri pokretanju.
- Neki uređaji ne odgovaraju na ICMP; zato postoji TCP fallback na portove 80/443/22/445/8080/53.
- Skeniranje /24 sa uključenim port scanom traje ~1–3 minuta; bez port scana ~10–20 sekundi.
- Koristi isključivo na mrežama koje posjeduješ ili imaš dozvolu da ih skeniraš.
