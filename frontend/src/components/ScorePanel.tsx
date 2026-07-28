import type { InputMethod, OutfitDto, StyleFeedbackDto } from '../types'
import { StyleFeedbackForm } from './StyleFeedbackForm'

function tier(score: number): { label: string; color: string } {
  if (score >= 70) return { label: 'FIRE', color: 'text-accent' }
  if (score >= 40) return { label: 'DECENT', color: 'text-amber-400' }
  return { label: 'MISMATCHED', color: 'text-neutral-400' }
}

interface Props {
  outfit: OutfitDto | null
  loading: boolean
  onSubmitFeedback: (rawText: string, inputMethod: InputMethod) => Promise<StyleFeedbackDto>
}

export function ScorePanel({ outfit, loading, onSubmitFeedback }: Props) {
  if (loading) {
    return (
      <div className="flex items-center justify-center rounded-lg border border-white/10 bg-black/30 p-6">
        <p className="font-mono text-xs uppercase tracking-widest text-neutral-500">Scoring…</p>
      </div>
    )
  }

  if (!outfit) return null

  const t = tier(outfit.score)

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-white/10 bg-black/30 p-4">
      <div className="flex items-baseline justify-between">
        <span className="font-mono text-xs uppercase tracking-widest text-neutral-500">Cohesion</span>
        <span className={`font-mono text-xs font-bold uppercase tracking-widest ${t.color}`}>{t.label}</span>
      </div>
      <div className="flex items-end gap-2">
        <span className="font-mono text-4xl font-bold tabular-nums">{outfit.score.toFixed(0)}</span>
        <span className="pb-1 font-mono text-sm text-neutral-500">/ 100</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-white/10">
        <div
          className="h-full rounded-full bg-accent transition-all"
          style={{ width: `${Math.min(100, Math.max(0, outfit.score))}%` }}
        />
      </div>
      <ul className="flex flex-col gap-1.5 pt-1">
        {outfit.reasons.map((reason) => (
          <li key={reason} className="flex gap-2 text-sm text-neutral-300">
            <span className="text-accent">»</span>
            {reason}
          </li>
        ))}
      </ul>

      <StyleFeedbackForm onSubmit={onSubmitFeedback} label="Why does this fit work?" />
    </div>
  )
}
