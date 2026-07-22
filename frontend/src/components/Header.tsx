export type View = 'builder' | 'gallery'

interface Props {
  view: View
  onViewChange: (view: View) => void
}

export function Header({ view, onViewChange }: Props) {
  return (
    <header className="border-b border-white/10 px-4 py-5 sm:px-8">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4">
        <h1 className="font-mono text-xl font-bold tracking-tight">
          FIT<span className="text-accent">//</span>LAB
        </h1>

        <nav className="flex gap-1 rounded-lg border border-white/10 bg-black/30 p-1">
          <button
            onClick={() => onViewChange('builder')}
            className={`rounded-md px-4 py-1.5 text-xs font-bold uppercase tracking-wide cursor-pointer ${
              view === 'builder' ? 'bg-accent text-ink' : 'text-neutral-400 hover:text-white'
            }`}
          >
            Builder
          </button>
          <button
            onClick={() => onViewChange('gallery')}
            className={`rounded-md px-4 py-1.5 text-xs font-bold uppercase tracking-wide cursor-pointer ${
              view === 'gallery' ? 'bg-accent text-ink' : 'text-neutral-400 hover:text-white'
            }`}
          >
            Community
          </button>
        </nav>

        <p className="hidden font-mono text-xs uppercase tracking-widest text-neutral-500 lg:block">
          mix. match. cop the fit.
        </p>
      </div>
    </header>
  )
}
