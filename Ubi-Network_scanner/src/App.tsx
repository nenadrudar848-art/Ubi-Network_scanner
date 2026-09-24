import { useEffect, useMemo, useRef, useState } from "react";
import Radar from "./components/Radar";
import Howto from "./components/Howto";
import { generateHosts, parseRange, ICONS, type Host } from "./scanner";

const PRESETS = ["192.168.0.1/24", "192.168.1.1/24", "10.0.0.1-60", "172.16.10.1/24"];

export default function App() {
  const [segment, setSegment] = useState("192.168.1.1/24");
  const [scanning, setScanning] = useState(false);
  const [progress, setProgress] = useState(0);
  const [current, setCurrent] = useState("");
  const [hosts, setHosts] = useState<Host[]>([]);
  const [log, setLog] = useState<string[]>([]);
  const [open, setOpen] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [deep, setDeep] = useState(true);
  const [tab, setTab] = useState<"scan" | "log" | "apk">("apk");
  const timer = useRef<number | null>(null);

  useEffect(() => () => { if (timer.current) window.clearInterval(timer.current); }, []);

  const start = () => {
    const ips = parseRange(segment);
    if (!ips.length) {
      setLog((l) => [`✗ Neispravan segment: "${segment}"`, ...l]);
      setTab("log");
      return;
    }
    const results = generateHosts(ips);
    setHosts([]);
    setProgress(0);
    setOpen(null);
    setScanning(true);
    setLog([`▶ Start ARP/ICMP sweep · ${ips.length} adresa · ${deep ? "deep port scan" : "brzi mod"}`]);
    let i = 0;
    const step = Math.max(1, Math.floor(ips.length / 90));
    timer.current = window.setInterval(() => {
      i += step;
      const idx = Math.min(i, ips.length);
      setCurrent(ips[Math.min(idx, ips.length - 1)]);
      setProgress(Math.round((idx / ips.length) * 100));
      const found = results.filter((h) => ips.indexOf(h.ip) < idx);
      setHosts(found);
      setLog((l) => {
        const nu = results
          .filter((h) => ips.indexOf(h.ip) < idx && ips.indexOf(h.ip) >= idx - step)
          .map((h) => `✓ ${h.ip.padEnd(15)} ${h.hostname} · ${h.ports.length} port(a) · ${h.latency}ms`);
        return nu.length ? [...nu.reverse(), ...l] : l;
      });
      if (idx >= ips.length) {
        if (timer.current) window.clearInterval(timer.current);
        setScanning(false);
        setHosts(results);
        setLog((l) => [`■ Skeniranje završeno · ${results.length} aktivnih hostova`, ...l]);
      }
    }, deep ? 55 : 25);
  };

  const stop = () => {
    if (timer.current) window.clearInterval(timer.current);
    setScanning(false);
    setLog((l) => ["⏹ Skeniranje prekinuto od strane korisnika", ...l]);
  };

  const filtered = useMemo(() => {
    const q = query.toLowerCase().trim();
    if (!q) return hosts;
    return hosts.filter(
      (h) =>
        h.ip.includes(q) ||
        h.hostname.toLowerCase().includes(q) ||
        h.vendor.toLowerCase().includes(q) ||
        h.ports.some((p) => String(p.port).includes(q) || p.service.includes(q))
    );
  }, [hosts, query]);

  const openPorts = hosts.reduce((a, h) => a + h.ports.filter((p) => p.state === "open").length, 0);
  const avg = hosts.length ? (hosts.reduce((a, h) => a + h.latency, 0) / hosts.length).toFixed(1) : "—";

  return (
    <div className="relative min-h-screen overflow-hidden bg-[#05070f] text-slate-200">
      {/* background */}
      <div className="pointer-events-none fixed inset-0">
        <div className="absolute -left-40 -top-40 h-[32rem] w-[32rem] rounded-full bg-emerald-500/20 blur-[120px]" />
        <div className="absolute -right-32 top-1/3 h-[28rem] w-[28rem] rounded-full bg-cyan-500/20 blur-[120px]" />
        <div className="absolute bottom-0 left-1/3 h-[26rem] w-[26rem] rounded-full bg-violet-600/20 blur-[130px]" />
        <div
          className="absolute inset-0 opacity-[0.16]"
          style={{
            backgroundImage:
              "linear-gradient(rgba(148,163,184,.5) 1px,transparent 1px),linear-gradient(90deg,rgba(148,163,184,.5) 1px,transparent 1px)",
            backgroundSize: "44px 44px",
            maskImage: "radial-gradient(ellipse at 50% 0%, black, transparent 75%)",
          }}
        />
      </div>

      <div className="relative mx-auto max-w-5xl px-4 pb-24 pt-6 sm:px-6">
        {/* header */}
        <header className="flex items-center gap-3">
          <div className="grid h-12 w-12 place-items-center rounded-2xl bg-gradient-to-br from-emerald-400 to-cyan-600 text-xl shadow-lg shadow-emerald-500/30">
            📡
          </div>
          <div className="flex-1">
            <h1 className="text-xl font-bold tracking-tight text-white">
              Ubi<span className="text-emerald-400">-Network</span> Scanner
            </h1>
            <p className="text-[11px] uppercase tracking-[0.2em] text-slate-500">
              Wi-Fi · LAN discovery toolkit
            </p>
          </div>
          <div className="rounded-full border border-emerald-400/30 bg-emerald-400/10 px-3 py-1 text-[11px] font-medium text-emerald-300">
            {scanning ? "SKENIRAM" : "SPREMAN"}
          </div>
        </header>

        {/* demo notice */}
        <div className="mt-4 rounded-2xl border border-amber-400/30 bg-amber-400/10 p-4 text-sm text-amber-100">
          <div className="font-semibold text-amber-300">⚠️ Ovo je web PREVIEW (demo podaci)</div>
          <p className="mt-1 text-xs leading-relaxed text-amber-100/80">
            Browser ne sme da koristi ICMP/ARP/raw sokete, pa web verzija ne može stvarno da skenira
            mrežu. <b>Pravi native Android skener</b> (Kotlin + Jetpack Compose) je u folderu{" "}
            <code className="rounded bg-black/40 px-1 font-mono">android-app/</code> — koristi
            InetAddress.isReachable, TCP connect port scan, <code className="font-mono">/proc/net/arp</code>{" "}
            za MAC, reverse DNS za hostname i OUI bazu za proizvođača.
          </p>
          <p className="mt-2 text-xs text-amber-100/80">
            <b>APK:</b> pushuj repo na GitHub → Actions → „Build Ubi-Network Scanner APK“ → skini
            artifact <code className="font-mono">app-debug.apk</code>. Ili lokalno:{" "}
            <code className="rounded bg-black/40 px-1 font-mono">cd android-app &amp;&amp; ./gradlew assembleDebug</code>
          </p>
        </div>

        {/* control card */}
        <section className="mt-5 rounded-3xl border border-white/10 bg-white/[0.04] p-5 backdrop-blur-xl shadow-2xl shadow-black/40">
          <div className="grid gap-5 sm:grid-cols-[1fr_auto] sm:items-center">
            <div>
              <label className="text-[11px] font-semibold uppercase tracking-widest text-slate-400">
                Custom segment / opseg
              </label>
              <div className="mt-2 flex gap-2">
                <input
                  value={segment}
                  onChange={(e) => setSegment(e.target.value)}
                  placeholder="192.168.1.1/24, 10.0.0.5-60"
                  className="w-full rounded-2xl border border-white/10 bg-black/40 px-4 py-3 font-mono text-sm text-emerald-200 outline-none transition focus:border-emerald-400/60 focus:ring-4 focus:ring-emerald-400/10"
                />
                <button
                  onClick={scanning ? stop : start}
                  className={`shrink-0 rounded-2xl px-5 py-3 text-sm font-semibold transition active:scale-95 ${
                    scanning
                      ? "bg-rose-500/90 text-white shadow-lg shadow-rose-500/30"
                      : "bg-gradient-to-r from-emerald-400 to-cyan-500 text-slate-900 shadow-lg shadow-emerald-500/30"
                  }`}
                >
                  {scanning ? "Stop" : "Skeniraj"}
                </button>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {PRESETS.map((p) => (
                  <button
                    key={p}
                    onClick={() => setSegment(p)}
                    className={`rounded-full border px-3 py-1 font-mono text-[11px] transition ${
                      segment === p
                        ? "border-emerald-400/60 bg-emerald-400/15 text-emerald-300"
                        : "border-white/10 bg-white/5 text-slate-400 hover:border-white/25"
                    }`}
                  >
                    {p}
                  </button>
                ))}
                <button
                  onClick={() => setDeep(!deep)}
                  className={`rounded-full border px-3 py-1 text-[11px] transition ${
                    deep
                      ? "border-cyan-400/50 bg-cyan-400/15 text-cyan-300"
                      : "border-white/10 bg-white/5 text-slate-400"
                  }`}
                >
                  {deep ? "◉" : "○"} Deep port scan
                </button>
              </div>
            </div>
            <Radar active={scanning} found={hosts.length} />
          </div>

          {/* progress */}
          <div className="mt-5">
            <div className="flex justify-between font-mono text-[11px] text-slate-400">
              <span>{scanning ? `probe → ${current}` : "idle"}</span>
              <span>{progress}%</span>
            </div>
            <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-white/10">
              <div
                className="h-full rounded-full bg-gradient-to-r from-emerald-400 via-cyan-400 to-violet-400 transition-all duration-150"
                style={{ width: `${progress}%` }}
              />
            </div>
          </div>

          {/* stats */}
          <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[
              ["Hostovi", hosts.length, "text-emerald-300"],
              ["Otvoreni portovi", openPorts, "text-cyan-300"],
              ["Prosek latencije", `${avg} ms`, "text-violet-300"],
              ["Adresa u opsegu", parseRange(segment).length, "text-amber-300"],
            ].map(([l, v, c]) => (
              <div key={l as string} className="rounded-2xl border border-white/10 bg-black/30 p-3">
                <div className={`font-mono text-lg font-bold ${c}`}>{v}</div>
                <div className="text-[10px] uppercase tracking-wider text-slate-500">{l}</div>
              </div>
            ))}
          </div>
        </section>

        {/* tabs */}
        <div className="mt-6 flex items-center gap-2">
          {(["apk", "scan", "log"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`rounded-xl px-4 py-2 text-sm font-medium transition ${
                tab === t ? "bg-white/10 text-white" : "text-slate-500 hover:text-slate-300"
              }`}
            >
              {t === "apk" ? "📦 Skini APK" : t === "scan" ? `Demo uređaji (${filtered.length})` : "Konzola"}
            </button>
          ))}
          {tab === "scan" && (
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="🔍 filter: ip, host, port, vendor"
              className="ml-auto w-44 rounded-xl border border-white/10 bg-black/40 px-3 py-2 text-xs outline-none focus:border-emerald-400/50 sm:w-64"
            />
          )}
        </div>

        {tab === "apk" ? (
          <Howto />
        ) : tab === "log" ? (
          <div className="mt-3 max-h-[28rem] overflow-auto rounded-2xl border border-white/10 bg-black/60 p-4 font-mono text-[11px] leading-relaxed text-emerald-300/80">
            {log.length ? log.map((l, i) => <div key={i}>{l}</div>) : <div className="text-slate-600">— prazno —</div>}
          </div>
        ) : (
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            {filtered.map((h) => (
              <article
                key={h.ip}
                onClick={() => setOpen(open === h.ip ? null : h.ip)}
                className="group cursor-pointer overflow-hidden rounded-2xl border border-white/10 bg-white/[0.04] p-4 backdrop-blur-xl transition hover:border-emerald-400/40 hover:bg-white/[0.07]"
                style={{ animation: "fadeUp .4s ease both" }}
              >
                <div className="flex items-start gap-3">
                  <div className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-white/10 to-white/5 text-lg ring-1 ring-white/10">
                    {ICONS[h.type]}
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-sm font-semibold text-white">{h.ip}</span>
                      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-emerald-400 shadow-[0_0_8px_2px_rgba(16,185,129,.7)]" />
                    </div>
                    <div className="truncate text-xs text-emerald-300/90">{h.hostname}</div>
                    <div className="truncate text-[11px] text-slate-500">{h.vendor} · {h.os}</div>
                  </div>
                  <div className="text-right">
                    <div className="font-mono text-xs text-cyan-300">{h.latency} ms</div>
                    <div className="mt-1 flex justify-end gap-0.5">
                      {[0, 1, 2, 3].map((i) => (
                        <span
                          key={i}
                          className={`w-1 rounded-sm ${h.signal > i * 20 ? "bg-emerald-400" : "bg-white/15"}`}
                          style={{ height: 4 + i * 3 }}
                        />
                      ))}
                    </div>
                  </div>
                </div>

                <div className="mt-3 flex flex-wrap gap-1.5">
                  {h.ports.slice(0, open === h.ip ? 99 : 4).map((p) => (
                    <span
                      key={p.port}
                      className={`rounded-md px-2 py-0.5 font-mono text-[10px] ring-1 ${
                        p.state === "open"
                          ? "bg-emerald-400/10 text-emerald-300 ring-emerald-400/30"
                          : "bg-amber-400/10 text-amber-300 ring-amber-400/30"
                      }`}
                    >
                      {p.port}/{p.service}
                    </span>
                  ))}
                  {open !== h.ip && h.ports.length > 4 && (
                    <span className="rounded-md bg-white/5 px-2 py-0.5 font-mono text-[10px] text-slate-400">
                      +{h.ports.length - 4}
                    </span>
                  )}
                </div>

                {open === h.ip && (
                  <div className="mt-3 grid grid-cols-2 gap-2 border-t border-white/10 pt-3 text-[11px]">
                    <Info label="MAC" value={h.mac} />
                    <Info label="Tip" value={h.type.toUpperCase()} />
                    <Info label="OS fingerprint" value={h.os} />
                    <Info label="Jačina signala" value={`${h.signal}%`} />
                  </div>
                )}
              </article>
            ))}
            {!filtered.length && (
              <div className="col-span-full rounded-2xl border border-dashed border-white/10 p-10 text-center text-sm text-slate-500">
                {scanning ? "Tražim uređaje…" : "Nema rezultata. Pokreni skeniranje."}
              </div>
            )}
          </div>
        )}
      </div>

      <style>{`
        @keyframes fadeUp { from{opacity:0;transform:translateY(10px)} to{opacity:1;transform:none} }
        @keyframes ping { 0%{transform:scale(.2);opacity:.8} 100%{transform:scale(1);opacity:0} }
        @keyframes spin { to { transform: rotate(360deg) } }
      `}</style>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-black/30 px-2.5 py-1.5">
      <div className="text-[9px] uppercase tracking-wider text-slate-500">{label}</div>
      <div className="font-mono text-slate-200">{value}</div>
    </div>
  );
}
