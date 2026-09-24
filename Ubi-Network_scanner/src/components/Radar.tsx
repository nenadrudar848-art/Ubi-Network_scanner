export default function Radar({ active, found }: { active: boolean; found: number }) {
  return (
    <div className="relative mx-auto aspect-square w-44 select-none">
      <div className="absolute inset-0 rounded-full border border-emerald-400/30" />
      <div className="absolute inset-[14%] rounded-full border border-emerald-400/20" />
      <div className="absolute inset-[30%] rounded-full border border-emerald-400/15" />
      <div className="absolute inset-[46%] rounded-full border border-emerald-400/10" />
      <div className="absolute left-1/2 top-0 h-full w-px bg-emerald-400/10" />
      <div className="absolute top-1/2 h-px w-full bg-emerald-400/10" />
      {active && (
        <div
          className="absolute inset-0 rounded-full"
          style={{
            background:
              "conic-gradient(from 0deg, rgba(16,185,129,0) 0deg, rgba(16,185,129,0) 300deg, rgba(16,185,129,.45) 360deg)",
            animation: "spin 1.6s linear infinite",
          }}
        />
      )}
      {active &&
        [0, 1, 2].map((i) => (
          <span
            key={i}
            className="absolute inset-0 rounded-full border border-emerald-400/40"
            style={{ animation: `ping 2.4s cubic-bezier(0,0,.2,1) ${i * 0.8}s infinite` }}
          />
        ))}
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-mono text-3xl font-bold text-emerald-300 drop-shadow-[0_0_12px_rgba(16,185,129,.7)]">
          {found}
        </span>
        <span className="text-[10px] uppercase tracking-[0.25em] text-emerald-500/70">uređaja</span>
      </div>
    </div>
  );
}
