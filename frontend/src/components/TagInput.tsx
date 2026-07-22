import { useState, type KeyboardEvent } from 'react'

interface Props {
  label: string
  placeholder: string
  values: string[]
  onChange: (values: string[]) => void
}

/** Free-text chip input: Enter or comma commits a tag, Backspace on empty input removes the last one. */
export function TagInput({ label, placeholder, values, onChange }: Props) {
  const [draft, setDraft] = useState('')

  function commit() {
    const tag = draft.trim().toLowerCase()
    if (tag && !values.includes(tag)) {
      onChange([...values, tag])
    }
    setDraft('')
  }

  function handleKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      commit()
    } else if (e.key === 'Backspace' && draft === '' && values.length > 0) {
      onChange(values.slice(0, -1))
    }
  }

  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">{label}</span>
      <div className="flex flex-wrap items-center gap-1.5 rounded-md border border-white/15 bg-black/30 p-2 focus-within:border-accent">
        {values.map((tag) => (
          <span
            key={tag}
            className="flex items-center gap-1 rounded-full bg-white/10 px-2 py-0.5 text-xs text-neutral-200"
          >
            {tag}
            <button
              type="button"
              onClick={() => onChange(values.filter((v) => v !== tag))}
              className="text-neutral-400 hover:text-red-400 cursor-pointer"
              aria-label={`Remove ${tag}`}
            >
              ×
            </button>
          </span>
        ))}
        <input
          key="tag-input"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={commit}
          placeholder={values.length === 0 ? placeholder : ''}
          className="min-w-24 flex-1 bg-transparent text-sm outline-none placeholder:text-neutral-600"
        />
      </div>
    </label>
  )
}
