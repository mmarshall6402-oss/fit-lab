import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { InputMethod, Sentiment, StyleFeedbackDto } from '../types'

interface Props {
  onSubmit: (rawText: string, inputMethod: InputMethod, sentiment: Sentiment) => Promise<StyleFeedbackDto>
  label?: string
}

/**
 * Shared "why do you like this" capture widget - a like/dislike toggle, a
 * textarea, and an optional mic button that transcribes speech to text live
 * via the browser's native SpeechRecognition API (no audio ever leaves the
 * client). Works identically for item-level and outfit-level feedback; the
 * caller just wires onSubmit.
 */
export function StyleFeedbackForm({ onSubmit, label = 'Why do you like this?' }: Props) {
  const [sentiment, setSentiment] = useState<Sentiment>('LIKE')
  const [text, setText] = useState('')
  const [inputMethod, setInputMethod] = useState<InputMethod>('TEXT')
  const [listening, setListening] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<'full' | 'pending' | null>(null)
  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null)

  const SpeechRecognitionCtor = window.SpeechRecognition ?? window.webkitSpeechRecognition
  const voiceSupported = !!SpeechRecognitionCtor

  useEffect(() => {
    return () => {
      recognitionRef.current?.stop()
    }
  }, [])

  function toggleListening() {
    if (!SpeechRecognitionCtor) return
    if (listening) {
      recognitionRef.current?.stop()
      return
    }
    const recognition = new SpeechRecognitionCtor()
    recognition.continuous = true
    recognition.interimResults = true
    recognition.lang = 'en-US'
    recognition.onresult = (event) => {
      setInputMethod('VOICE')
      let transcript = ''
      for (let i = 0; i < event.results.length; i++) {
        transcript += event.results[i][0].transcript
      }
      setText(transcript)
    }
    recognition.onerror = () => setListening(false)
    recognition.onend = () => setListening(false)
    recognitionRef.current = recognition
    recognition.start()
    setListening(true)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!text.trim() || saving) return
    setSaving(true)
    setError(null)
    setSaved(null)
    try {
      const feedback = await onSubmit(text.trim(), inputMethod, sentiment)
      setText('')
      setInputMethod('TEXT')
      setSentiment('LIKE')
      setSaved(feedback.extractionSucceeded ? 'full' : 'pending')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save your feedback.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2 rounded-lg border border-white/10 bg-black/20 p-3">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wide text-neutral-400">{label}</span>
        <div className="flex gap-1 rounded-full border border-white/10 bg-black/30 p-0.5">
          <button
            type="button"
            onClick={() => setSentiment('LIKE')}
            className={`rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide cursor-pointer ${
              sentiment === 'LIKE' ? 'bg-accent text-ink' : 'text-neutral-400 hover:text-white'
            }`}
          >
            👍 Works
          </button>
          <button
            type="button"
            onClick={() => setSentiment('DISLIKE')}
            className={`rounded-full px-2.5 py-1 text-[11px] font-bold uppercase tracking-wide cursor-pointer ${
              sentiment === 'DISLIKE' ? 'bg-red-400 text-ink' : 'text-neutral-400 hover:text-white'
            }`}
          >
            👎 Doesn't work
          </button>
        </div>
      </div>
      <div className="flex gap-2">
        <textarea
          value={text}
          onChange={(e) => {
            setText(e.target.value)
            setSaved(null)
          }}
          placeholder={
            sentiment === 'LIKE'
              ? 'e.g. the oversized jacket balances the slim pants, and the neutral colors keep it clean…'
              : "e.g. the colors clash and the fit feels boxy…"
          }
          rows={3}
          className="flex-1 resize-none rounded-md border border-white/15 bg-black/30 px-3 py-2 text-sm outline-none focus:border-accent"
        />
        <button
          type="button"
          onClick={toggleListening}
          disabled={!voiceSupported}
          title={
            voiceSupported
              ? listening
                ? 'Stop recording'
                : 'Speak your reasoning'
              : "Voice input isn't supported in this browser — try Chrome"
          }
          aria-label={listening ? 'Stop voice input' : 'Start voice input'}
          className={`flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center self-start rounded-full border disabled:cursor-not-allowed disabled:opacity-30 ${
            listening
              ? 'animate-pulse border-accent bg-accent/20 text-accent'
              : 'border-white/15 text-neutral-400 hover:text-accent'
          }`}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-4 w-4">
            <rect x="9" y="3" width="6" height="11" rx="3" />
            <path d="M5 11a7 7 0 0 0 14 0M12 18v3" strokeLinecap="round" />
          </svg>
        </button>
      </div>

      {error && <p className="text-sm text-red-400">{error}</p>}
      {saved === 'full' && <p className="text-sm text-accent">Saved.</p>}
      {saved === 'pending' && <p className="text-sm text-amber-400">Saved — style profile update pending.</p>}

      <div className="flex justify-end">
        <button
          type="submit"
          disabled={!text.trim() || saving}
          className="rounded-md bg-accent px-4 py-1.5 text-xs font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>
    </form>
  )
}
