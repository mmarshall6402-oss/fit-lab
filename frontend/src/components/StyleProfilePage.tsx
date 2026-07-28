import { useStyleProfile } from '../hooks/useStyleProfile'
import { PlainTag } from './Tag'

export function StyleProfilePage() {
  const { profile, loading, error } = useStyleProfile()

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-8 p-4 sm:p-8">
      <div className="flex items-center justify-between">
        <h1 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">Your Style Profile</h1>
        <a href="#" className="text-xs text-neutral-500 hover:text-neutral-300">
          ← Back to FIT//LAB
        </a>
      </div>

      {error && (
        <p className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p>
      )}

      {loading || !profile ? (
        <p className="text-sm text-neutral-500">Loading…</p>
      ) : profile.feedbackCount === 0 ? (
        <p className="rounded-lg border border-dashed border-white/15 py-10 text-center text-sm text-neutral-500">
          No style feedback yet — tell us why you like a piece or a fit to start building your profile.
        </p>
      ) : (
        <>
          <p className="font-mono text-xs uppercase tracking-widest text-neutral-500">
            Built from {profile.feedbackCount} reflection{profile.feedbackCount === 1 ? '' : 's'} · updated{' '}
            {new Date(profile.updatedAt).toLocaleString()}
          </p>

          <section className="flex flex-col gap-3 rounded-lg border border-white/10 p-5">
            <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">Likes</h2>
            <div className="flex flex-wrap gap-1.5">
              {profile.likes.length === 0 ? (
                <p className="text-sm text-neutral-500">Nothing extracted yet.</p>
              ) : (
                profile.likes.map((like) => <PlainTag key={like} label={like} />)
              )}
            </div>
          </section>

          <section className="flex flex-col gap-3 rounded-lg border border-white/10 p-5">
            <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">Style tags</h2>
            <div className="flex flex-wrap gap-1.5">
              {profile.styleTags.length === 0 ? (
                <p className="text-sm text-neutral-500">Nothing extracted yet.</p>
              ) : (
                profile.styleTags.map((tag) => <PlainTag key={tag} label={tag} />)
              )}
            </div>
          </section>

          <section className="flex flex-col gap-3 rounded-lg border border-white/10 p-5">
            <h2 className="font-mono text-sm font-bold uppercase tracking-widest text-neutral-400">
              In your own words
            </h2>
            {profile.reasoningSummary.trim() === '' ? (
              <p className="text-sm text-neutral-500">Nothing extracted yet.</p>
            ) : (
              <ul className="flex flex-col gap-1.5">
                {profile.reasoningSummary.split('\n').map((line, i) => (
                  <li key={i} className="flex gap-2 text-sm text-neutral-300">
                    <span className="text-accent">»</span>
                    {line}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  )
}
