import type { Category } from '../types'

/** Minimal line-art glyphs so empty slots/tabs read at a glance without external icon fonts. */
export function CategoryIcon({ category, className }: { category: Category; className?: string }) {
  switch (category) {
    case 'SHIRT':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className={className}>
          <path
            d="M8 3 3 7l2.5 3L7 9v11h10V9l1.5 1L21 7l-5-4-2 2h-4L8 3Z"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        </svg>
      )
    case 'BOTTOM':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className={className}>
          <path d="M6 3h12l1 8-2 10h-4l-1-9-1 9H7L5 11l1-8Z" strokeLinejoin="round" strokeLinecap="round" />
        </svg>
      )
    case 'SHOES':
      return (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className={className}>
          <path
            d="M3 17c0-2 1.5-3 3-4.5S8 8 8 6l4 1v3l4 2 4 1.5c1 .4 1 1.5 1 2.5v1H3v-1Z"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        </svg>
      )
  }
}
