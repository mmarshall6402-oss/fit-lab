export function Header() {
  return (
    <header className="border-b border-white/10 px-4 py-5 sm:px-8">
      <div className="mx-auto flex max-w-6xl items-center justify-between">
        <h1 className="font-mono text-xl font-bold tracking-tight">
          FIT<span className="text-accent">//</span>LAB
        </h1>
        <div className="flex items-center gap-4">
          <p className="hidden font-mono text-xs uppercase tracking-widest text-neutral-500 sm:block">
            mix. match. cop the fit.
          </p>
          <a
            href="#style-profile"
            className="font-mono text-xs uppercase tracking-widest text-neutral-600 hover:text-accent"
          >
            Style Profile
          </a>
          <a
            href="#admin"
            className="font-mono text-xs uppercase tracking-widest text-neutral-600 hover:text-accent"
          >
            Admin
          </a>
        </div>
      </div>
    </header>
  )
}
