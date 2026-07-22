export {}

declare global {
  interface Window {
    /** Backend base URL, set by /config.js before the app loads. Empty string means same-origin. */
    __FITLAB_API_BASE__?: string
  }
}
