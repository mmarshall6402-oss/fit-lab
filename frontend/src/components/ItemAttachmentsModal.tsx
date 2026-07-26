import { useEffect, useRef, useState } from 'react'
import type { AttachmentDto, ItemDto } from '../types'
import { api, resolveUrl } from '../api/client'

interface Props {
  item: ItemDto
  onClose: () => void
}

function FileIcon({ contentType }: { contentType: string }) {
  if (contentType.startsWith('image/')) {
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-5 w-5 shrink-0">
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <circle cx="8.5" cy="9.5" r="1.5" />
        <path d="m21 16-5-5-4 4-3-3-6 6" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    )
  }
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="h-5 w-5 shrink-0">
      <path d="M6 3h9l5 5v13H6V3Z" strokeLinejoin="round" strokeLinecap="round" />
      <path d="M15 3v5h5" strokeLinejoin="round" strokeLinecap="round" />
    </svg>
  )
}

export function ItemAttachmentsModal({ item, onClose }: Props) {
  const [attachments, setAttachments] = useState<AttachmentDto[]>([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let cancelled = false
    api
      .listAttachments(item.id)
      .then((list) => !cancelled && setAttachments(list))
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Could not load files.'))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [item.id])

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return
    setUploading(true)
    setError(null)
    try {
      for (const file of Array.from(files)) {
        const created = await api.uploadAttachment(item.id, file)
        setAttachments((prev) => [...prev, created])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not upload file.')
    } finally {
      setUploading(false)
    }
  }

  async function handleDelete(attachment: AttachmentDto) {
    try {
      await api.deleteAttachment(item.id, attachment.id)
      setAttachments((prev) => prev.filter((a) => a.id !== attachment.id))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete file.')
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" onClick={onClose}>
      <div
        className="flex max-h-[80vh] w-full max-w-md flex-col gap-4 rounded-xl border border-white/10 bg-ink-2 p-6 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="font-mono text-lg font-bold uppercase tracking-wide text-accent">Files</h2>
          <button onClick={onClose} className="text-neutral-400 hover:text-white cursor-pointer" aria-label="Close">
            ✕
          </button>
        </div>
        <p className="text-sm text-neutral-400">{item.name}</p>

        {error && <p className="text-sm text-red-400">{error}</p>}

        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <p className="text-sm text-neutral-500">Loading…</p>
          ) : attachments.length === 0 ? (
            <p className="rounded-lg border border-dashed border-white/15 py-6 text-center text-sm text-neutral-500">
              No files yet — receipts, size charts, anything.
            </p>
          ) : (
            <ul className="flex flex-col gap-2">
              {attachments.map((a) => (
                <li
                  key={a.id}
                  className="flex items-center gap-2 rounded-md border border-white/10 bg-black/30 px-3 py-2 text-sm"
                >
                  <FileIcon contentType={a.contentType} />
                  <a
                    href={resolveUrl(a.url)!}
                    target="_blank"
                    rel="noreferrer"
                    className="flex-1 truncate text-neutral-200 hover:text-accent"
                    title={a.filename}
                  >
                    {a.filename}
                  </a>
                  <button
                    onClick={() => handleDelete(a)}
                    className="text-neutral-400 hover:text-red-400 cursor-pointer"
                    aria-label={`Delete ${a.filename}`}
                  >
                    ✕
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <input
          ref={fileInput}
          type="file"
          multiple
          className="hidden"
          onChange={(e) => {
            handleFiles(e.target.files)
            e.target.value = ''
          }}
        />
        <button
          onClick={() => fileInput.current?.click()}
          disabled={uploading}
          className="rounded-md bg-accent px-4 py-2 text-sm font-bold uppercase text-ink hover:bg-accent-dim disabled:opacity-50 cursor-pointer"
        >
          {uploading ? 'Uploading…' : '+ Add files'}
        </button>
      </div>
    </div>
  )
}
