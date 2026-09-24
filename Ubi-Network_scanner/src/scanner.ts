export type Port = { port: number; service: string; state: "open" | "filtered" };
export type Host = {
  ip: string;
  hostname: string;
  mac: string;
  vendor: string;
  os: string;
  latency: number;
  type: "router" | "phone" | "pc" | "iot" | "printer" | "nas" | "tv" | "cam";
  ports: Port[];
  signal: number;
};

const VENDORS = [
  ["Ubiquiti Inc.", "router"],
  ["Apple, Inc.", "phone"],
  ["Samsung Electronics", "phone"],
  ["Intel Corporate", "pc"],
  ["Raspberry Pi Trading", "iot"],
  ["Espressif Inc.", "iot"],
  ["HP Enterprise", "printer"],
  ["Synology Inc.", "nas"],
  ["LG Electronics", "tv"],
  ["Hikvision", "cam"],
  ["TP-Link Technologies", "router"],
  ["Dell Inc.", "pc"],
] as const;

const NAMES: Record<string, string[]> = {
  router: ["gateway", "udm-pro", "ap-hodnik", "switch-24"],
  phone: ["iphone-marko", "galaxy-s24", "pixel-8", "ipad-dnevna"],
  pc: ["desktop-work", "thinkpad-x1", "ubuntu-dev", "macbook-pro"],
  iot: ["esp32-senzor", "rpi-homelab", "shelly-plug", "tasmota-led"],
  printer: ["hp-laserjet", "epson-eco"],
  nas: ["synology-ds", "truenas-core"],
  tv: ["lg-webos-tv", "chromecast"],
  cam: ["cam-ulaz", "cam-dvoriste"],
};

const PORTS: Record<string, [number, string][]> = {
  router: [[53, "domain"], [80, "http"], [443, "https"], [22, "ssh"], [8443, "https-alt"]],
  phone: [[62078, "iphone-sync"], [5353, "mdns"]],
  pc: [[22, "ssh"], [445, "smb"], [3389, "rdp"], [5900, "vnc"], [3000, "node"]],
  iot: [[80, "http"], [1883, "mqtt"], [8266, "ota"], [23, "telnet"]],
  printer: [[9100, "jetdirect"], [631, "ipp"], [80, "http"]],
  nas: [[5000, "dsm"], [445, "smb"], [548, "afp"], [22, "ssh"], [111, "rpcbind"]],
  tv: [[8008, "cast"], [8009, "cast-tls"], [7000, "airplay"]],
  cam: [[554, "rtsp"], [80, "http"], [8000, "http-alt"]],
};

function rnd<T>(a: readonly T[]) {
  return a[Math.floor(Math.random() * a.length)];
}

export function parseRange(input: string): string[] {
  const out: string[] = [];
  for (const part of input.split(",").map((s) => s.trim()).filter(Boolean)) {
    const cidr = part.match(/^(\d+\.\d+\.\d+)\.(\d+)\/(\d+)$/);
    const range = part.match(/^(\d+\.\d+\.\d+)\.(\d+)\s*-\s*(\d+)$/);
    const single = part.match(/^(\d+\.\d+\.\d+)\.(\d+)$/);
    if (cidr) {
      const bits = parseInt(cidr[3]);
      const count = Math.min(Math.pow(2, 32 - bits), 256);
      for (let i = 1; i < count - 1; i++) out.push(`${cidr[1]}.${i}`);
    } else if (range) {
      for (let i = +range[2]; i <= Math.min(+range[3], 254); i++) out.push(`${range[1]}.${i}`);
    } else if (single) out.push(part);
  }
  return out.length ? out : [];
}

export function generateHosts(ips: string[]): Host[] {
  const hosts: Host[] = [];
  const density = Math.min(0.35, 14 / Math.max(ips.length, 1));
  ips.forEach((ip, idx) => {
    const isGw = ip.endsWith(".1");
    if (!isGw && Math.random() > density) return;
    const [vendor, type] = isGw ? (["Ubiquiti Inc.", "router"] as const) : rnd(VENDORS);
    const t = type as Host["type"];
    const base = rnd(NAMES[t]);
    const pool = PORTS[t];
    const ports: Port[] = pool
      .filter(() => Math.random() > 0.35)
      .map(([port, service]) => ({ port, service, state: Math.random() > 0.85 ? "filtered" : "open" }));
    hosts.push({
      ip,
      hostname: isGw ? "gateway.local" : `${base}-${idx}.local`,
      mac: Array.from({ length: 6 }, () =>
        Math.floor(Math.random() * 256).toString(16).padStart(2, "0").toUpperCase()
      ).join(":"),
      vendor,
      os: t === "pc" ? rnd(["Linux 5.x", "Windows 11", "macOS 14"]) : t === "phone" ? rnd(["iOS 17", "Android 14"]) : "Embedded",
      latency: +(Math.random() * 40 + 0.6).toFixed(1),
      type: t,
      ports: ports.length ? ports : [{ port: pool[0][0], service: pool[0][1], state: "open" }],
      signal: Math.floor(Math.random() * 60) + 40,
    });
  });
  return hosts;
}

export const ICONS: Record<Host["type"], string> = {
  router: "🛰️",
  phone: "📱",
  pc: "💻",
  iot: "🔌",
  printer: "🖨️",
  nas: "🗄️",
  tv: "📺",
  cam: "🎥",
};
