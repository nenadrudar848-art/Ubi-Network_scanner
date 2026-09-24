const TREE = `android-app/                  ← OVO je Android projekat (push ovaj folder)
├── .github/workflows/android.yml   ← auto-build APK-a na GitHub-u
├── .gitignore
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/ubi/scanner/
        │   ├── MainActivity.kt        ← Compose UI (radar, kartice, filter)
        │   └── NetworkScanner.kt      ← PRAVI skener: ICMP, TCP, ARP, DNS, OUI
        └── res/
            ├── drawable/ic_radar.xml
            ├── mipmap/ic_launcher.xml
            ├── mipmap-anydpi-v26/ic_launcher.xml
            └── values/{colors,strings,themes}.xml`;

const STEPS = [
  {
    n: "1",
    t: "Skini kod iz ovog projekta",
    d: "U svom editoru/sandboxu preuzmi (Download / Export ZIP) ceo projekat. Tebi treba SAMO folder android-app/. Web deo (src/, index.html, package.json) ti ne treba za APK.",
  },
  {
    n: "2",
    t: "Napravi prazan GitHub repo",
    d: "github.com → New repository → npr. ubi-network-scanner → Create. Ne dodavaj README (već postoji).",
  },
  {
    n: "3",
    t: "Pushuj SADRŽAJ foldera android-app u koren repoa",
    d: "Bitno: settings.gradle.kts mora biti u korenu repoa, a ne u podfolderu.",
    code: `cd android-app          # uđi U njega, ne iznad
git init
git add -A
git commit -m "Ubi-Network Scanner"
git branch -M main
git remote add origin https://github.com/<tvoj-user>/ubi-network-scanner.git
git push -u origin main`,
  },
  {
    n: "4",
    t: "Sačekaj build na GitHub-u",
    d: "Repo → tab Actions → workflow „Build APK“ startuje sam. Traje ~4–7 min. Ako ne krene: Actions → Build APK → Run workflow.",
  },
  {
    n: "5",
    t: "Skini APK",
    d: "Klikni na završeni (zeleni ✓) run → dole sekcija Artifacts → UbiNetworkScanner-apk → skine se ZIP u kojem je app-debug.apk.",
  },
  {
    n: "6",
    t: "Instaliraj na telefon",
    d: "Prebaci app-debug.apk na telefon (USB, Drive, Telegram…) → otvori ga → dozvoli „Instaliranje nepoznatih aplikacija“ → Install. Pri prvom pokretanju daj dozvolu za lokaciju (Android je traži za Wi-Fi/SSID info).",
  },
];

export default function Howto() {
  return (
    <div className="mt-6 space-y-4">
      <div className="rounded-3xl border border-cyan-400/25 bg-cyan-400/[0.06] p-5 backdrop-blur-xl">
        <h2 className="text-lg font-bold text-white">📦 Kako da skineš i instaliraš pravu Android aplikaciju</h2>
        <p className="mt-1 text-xs text-slate-400">
          Folder koji ti treba je{" "}
          <code className="rounded bg-black/50 px-1.5 py-0.5 font-mono text-emerald-300">android-app/</code>. To je
          kompletan Kotlin/Compose projekat koji stvarno skenira mrežu.
        </p>

        <pre className="mt-4 overflow-auto rounded-2xl border border-white/10 bg-black/60 p-4 font-mono text-[10.5px] leading-relaxed text-slate-300">
{TREE}
        </pre>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {STEPS.map((s) => (
          <div
            key={s.n}
            className="rounded-2xl border border-white/10 bg-white/[0.04] p-4 backdrop-blur-xl transition hover:border-emerald-400/40"
          >
            <div className="flex items-center gap-2.5">
              <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-gradient-to-br from-emerald-400 to-cyan-500 text-xs font-bold text-slate-900">
                {s.n}
              </span>
              <h3 className="text-sm font-semibold text-white">{s.t}</h3>
            </div>
            <p className="mt-2 text-xs leading-relaxed text-slate-400">{s.d}</p>
            {s.code && (
              <pre className="mt-3 overflow-auto rounded-xl border border-white/10 bg-black/60 p-3 font-mono text-[10.5px] leading-relaxed text-emerald-300">
{s.code}
              </pre>
            )}
          </div>
        ))}
      </div>

      <div className="rounded-2xl border border-violet-400/25 bg-violet-400/[0.07] p-4">
        <h3 className="text-sm font-semibold text-violet-200">Alternativa bez GitHub-a</h3>
        <ul className="mt-2 space-y-1.5 text-xs text-slate-300">
          <li>
            <b>Android Studio:</b> File → Open → izaberi folder <code className="font-mono text-emerald-300">android-app</code> →
            sačekaj Gradle sync → Build → Build Bundle(s)/APK(s) → Build APK(s). APK je u{" "}
            <code className="font-mono">app/build/outputs/apk/debug/</code>.
          </li>
          <li>
            <b>Terminal</b> (treba JDK 17 + Android SDK):{" "}
            <code className="rounded bg-black/50 px-1.5 py-0.5 font-mono text-emerald-300">
              cd android-app &amp;&amp; gradle assembleDebug
            </code>
          </li>
        </ul>
      </div>

      <div className="rounded-2xl border border-amber-400/25 bg-amber-400/[0.07] p-4 text-xs text-amber-100/90">
        <b className="text-amber-300">Zašto ne mogu da ti dam gotov .apk odmah?</b> Kompajliranje APK-a zahteva Java
        JDK + Android SDK, kojih nema u ovom okruženju. Zato je dodat GitHub Actions workflow — on to odradi u cloudu
        besplatno za par minuta, bez ijedne instalacije na tvom računaru.
      </div>
    </div>
  );
}
