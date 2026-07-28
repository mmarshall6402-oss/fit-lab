import type { InputMethod, ItemDto, Sentiment, StyleFeedbackDto } from '../types'
import { StyleFeedbackForm } from './StyleFeedbackForm'

interface Props {
  item: ItemDto
  onSubmit: (rawText: string, inputMethod: InputMethod, sentiment: Sentiment) => Promise<StyleFeedbackDto>
  onClose: () => void
}

export function ItemFeedbackModal({ item, onSubmit, onClose }: Props) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" onClick={onClose}>
      <div
        className="flex w-full max-w-md flex-col gap-4 rounded-xl border border-white/10 bg-ink-2 p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">Style Feedback</h2>
          <button onClick={onClose} className="text-neutral-400 hover:text-white cursor-pointer" aria-label="Close">
            ✕
          </button>
        </div>
        <p className="text-sm text-neutral-400">{item.name}</p>

        <StyleFeedbackForm onSubmit={onSubmit} label="Why do you like this piece?" />
      </div>
    </div>
  )
}
