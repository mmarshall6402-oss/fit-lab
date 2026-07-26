import type {
  AttachmentDto,
  Category,
  CreateItemRequest,
  FullRecommendationDto,
  ItemDto,
  OutfitDto,
  RecommendationDto,
} from '../types'

export class ApiError extends Error {}

const API_BASE = window.__FITLAB_API_BASE__ ?? ''

/** Prefixes a backend-relative path (e.g. an item's imageUrl) with the configured API base. */
export function resolveUrl(path: string | null): string | null {
  if (!path) return path
  return path.startsWith('http') ? path : `${API_BASE}${path}`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: init?.body instanceof FormData ? undefined : { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(body?.error ?? `Request failed: ${res.status}`)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

export const api = {
  getItems: (category?: Category) =>
    request<ItemDto[]>(`/items${category ? `?category=${category}` : ''}`),

  createItem: (item: CreateItemRequest) =>
    request<ItemDto>('/items', { method: 'POST', body: JSON.stringify(item) }),

  importItems: (items: CreateItemRequest[]) =>
    request<ItemDto[]>('/items/import', { method: 'POST', body: JSON.stringify(items) }),

  deleteItem: (id: string) => request<void>(`/items/${id}`, { method: 'DELETE' }),

  uploadImage: (id: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<ItemDto>(`/items/${id}/image`, { method: 'POST', body: form })
  },

  listAttachments: (itemId: string) => request<AttachmentDto[]>(`/items/${itemId}/attachments`),

  uploadAttachment: (itemId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<AttachmentDto>(`/items/${itemId}/attachments`, { method: 'POST', body: form })
  },

  deleteAttachment: (itemId: string, attachmentId: string) =>
    request<void>(`/items/${itemId}/attachments/${attachmentId}`, { method: 'DELETE' }),

  recommend: (anchorId: string, category: Category) =>
    request<RecommendationDto[]>(`/recommend?anchorId=${anchorId}&category=${category}`),

  recommendFull: (shirtId: string) =>
    request<FullRecommendationDto>(`/recommend/full?shirtId=${shirtId}`),

  buildOutfit: (anchorId: string) => request<OutfitDto>(`/outfit/build?anchorId=${anchorId}`),

  scoreOutfit: (shirtId: string, bottomId: string, shoesId: string) =>
    request<OutfitDto>(`/outfit/score?shirtId=${shirtId}&bottomId=${bottomId}&shoesId=${shoesId}`),
}
