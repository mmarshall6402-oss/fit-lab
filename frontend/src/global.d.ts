export {}

declare global {
  interface Window {
    /** Backend base URL, set by /config.js before the app loads. Empty string means same-origin. */
    __FITLAB_API_BASE__?: string
    /** Browser-native speech-to-text, used to transcribe voice-based style feedback client-side. Chromium-only. */
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }

  /**
   * Minimal hand-rolled ambient types for the Web Speech API - only the
   * members StyleFeedbackForm actually uses, not a full DOM lib surface.
   */
  interface SpeechRecognitionConstructor {
    new (): SpeechRecognitionInstance
  }

  interface SpeechRecognitionInstance {
    continuous: boolean
    interimResults: boolean
    lang: string
    start(): void
    stop(): void
    onresult: ((event: SpeechRecognitionResultEvent) => void) | null
    onerror: ((event: Event) => void) | null
    onend: (() => void) | null
  }

  interface SpeechRecognitionResultEvent {
    results: SpeechRecognitionResultList
  }

  interface SpeechRecognitionResultList {
    length: number
    [index: number]: SpeechRecognitionResult
  }

  interface SpeechRecognitionResult {
    [index: number]: SpeechRecognitionAlternative
  }

  interface SpeechRecognitionAlternative {
    transcript: string
  }
}
