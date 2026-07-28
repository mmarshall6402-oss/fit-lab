import type {
  AttachmentDto,
  Category,
  CreateItemRequest,
  FullRecommendationDto,
  ItemDto,
  OutfitDto,
  RecommendationDto,
  ScoringConfigDto,
  StyleFeedbackDto,
  StyleProfileDto,
  SubmitItemFeedbackRequest,
  SubmitOutfitFeedbackRequest,
} from '../types'

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

const API_BASE = window.__FITLAB_API_BASE__ ?? ''

/** Prefixes a backend-relative path (e.g. an item's imageUrl) with the configured API base. */
export function resolveUrl(path: string | null): string | null {
  if (!path) return path
  return path.startsWith('http') ? path : `${API_BASE}${path}`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const baseHeaders: Record<string, string> = init?.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { ...baseHeaders, ...(init?.headers as Record<string, string> | undefined) },
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new ApiError(body?.error ?? `Request failed: ${res.status}`, res.status)
  }
  if (res.status === 204) return undefined as T
  return res.json()
}

function adminHeaders(token: string): Record<string, string> {
  return { 'X-Admin-Token': token }
}

export const api = {
  getItems: (category?: Category) =>
    request<ItemDto[]>(`/items${category ? `?category=${category}` : ''}`),

  createItem: (item: CreateItemRequest) =>
    request<ItemDto>('/items', { method: 'POST', body: JSON.stringify(item) }),

  importItems: (items: CreateItemRequest[]) =>
    request<ItemDto[]>('/items/import', { method: 'POST', body: JSON.stringify(items) }),

  updateItem: (id: string, item: CreateItemRequest) =>
    request<ItemDto>(`/items/${id}`, { method: 'PUT', body: JSON.stringify(item) }),

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

  adminGetScoringConfig: (token: string) =>
    request<ScoringConfigDto>('/admin/scoring-config', { headers: adminHeaders(token) }),

  adminUpdateScoringConfig: (token: string, config: ScoringConfigDto) =>
    request<ScoringConfigDto>('/admin/scoring-config', {
      method: 'PUT',
      body: JSON.stringify(config),
      headers: adminHeaders(token),
    }),

  adminResetScoringConfig: (token: string) =>
    request<ScoringConfigDto>('/admin/scoring-config/reset', { method: 'POST', headers: adminHeaders(token) }),

  submitItemFeedback: (itemId: string, feedbackRequest: SubmitItemFeedbackRequest) =>
    request<StyleFeedbackDto>(`/items/${itemId}/feedback`, { method: 'POST', body: JSON.stringify(feedbackRequest) }),

  submitOutfitFeedback: (feedbackRequest: SubmitOutfitFeedbackRequest) =>
    request<StyleFeedbackDto>('/outfit/feedback', { method: 'POST', body: JSON.stringify(feedbackRequest) }),

  getStyleProfile: () => request<StyleProfileDto>('/style-profile'),

  getStyleFeedbackHistory: () => request<StyleFeedbackDto[]>('/style-profile/history'),
}
