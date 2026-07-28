import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { InputMethod, Sentiment, StyleFeedbackDto, StyleProfileDto, SubmitOutfitFeedbackRequest } from '../types'

export function useStyleProfile() {
  const [profile, setProfile] = useState<StyleProfileDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setProfile(await api.getStyleProfile())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load style profile')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const submitItemFeedback = useCallback(
    async (itemId: string, rawText: string, inputMethod: InputMethod, sentiment: Sentiment): Promise<StyleFeedbackDto> => {
      const feedback = await api.submitItemFeedback(itemId, { rawText, inputMethod, sentiment })
      await refresh()
      return feedback
    },
    [refresh],
  )

  const submitOutfitFeedback = useCallback(
    async (request: SubmitOutfitFeedbackRequest): Promise<StyleFeedbackDto> => {
      const feedback = await api.submitOutfitFeedback(request)
      await refresh()
      return feedback
    },
    [refresh],
  )

  return { profile, loading, error, refresh, submitItemFeedback, submitOutfitFeedback }
}
