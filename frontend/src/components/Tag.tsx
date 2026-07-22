const COLOR_MAP: Record<string, string> = {
  cream: '#fffdd0',
  grey: '#808080',
}

function swatch(color: string): string {
  return COLOR_MAP[color] ?? color
}

export function ColorTag({ color }: { color: string }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[11px] uppercase tracking-wide text-neutral-300">
      <span
        className="h-2 w-2 rounded-full border border-white/20"
        style={{ backgroundColor: swatch(color) }}
      />
      {color}
    </span>
  )
}

export function VibeTag({ vibe }: { vibe: string }) {
  return (
    <span className="inline-flex items-center rounded-full border border-accent/30 bg-accent/10 px-2 py-0.5 text-[11px] uppercase tracking-wide text-accent">
      {vibe}
    </span>
  )
}
