export type Category = 'SHIRT' | 'BOTTOM' | 'SHOES'

export const CATEGORIES: Category[] = ['SHIRT', 'BOTTOM', 'SHOES']

export interface ItemDto {
  id: string
  name: string
  category: Category
  imageUrl: string | null
  colors: string[]
  vibes: string[]
}

export interface AttachmentDto {
  id: string
  url: string
  filename: string
  contentType: string
}

export interface CreateItemRequest {
  name: string
  category: Category
  imageUrl?: string | null
  colors: string[]
  vibes: string[]
}

export interface RecommendationDto {
  item: ItemDto
  score: number
}

export interface FullRecommendationDto {
  shirt: ItemDto
  bottoms: RecommendationDto[]
  shoes: RecommendationDto[]
}

export interface OutfitDto {
  shirt: ItemDto
  bottom: ItemDto
  shoes: ItemDto
  score: number
  reasons: string[]
}

export type Slots = {
  SHIRT: ItemDto | null
  BOTTOM: ItemDto | null
  SHOES: ItemDto | null
}

export interface ScoringConfigDto {
  colorWeight: number
  vibeWeight: number
  neutralColorThreshold: number
  neutralColors: string[]
  sharedVibeReasonEnabled: boolean
  sharedColorReasonEnabled: boolean
  neutralCounterbalanceReasonEnabled: boolean
}

export type FeedbackTarget = 'ITEM' | 'OUTFIT'
export type InputMethod = 'TEXT' | 'VOICE'

export interface StyleFeedbackDto {
  id: string
  target: FeedbackTarget
  itemId: string | null
  shirtId: string | null
  bottomId: string | null
  shoesId: string | null
  inputMethod: InputMethod
  rawText: string
  extractionSucceeded: boolean
  extractedLikes: string[]
  extractedStyleTags: string[]
  extractedReasoning: string | null
  createdAt: string
}

export interface StyleProfileDto {
  likes: string[]
  styleTags: string[]
  reasoningSummary: string
  feedbackCount: number
  updatedAt: string
}

export interface SubmitItemFeedbackRequest {
  rawText: string
  inputMethod: InputMethod
}

export interface SubmitOutfitFeedbackRequest {
  shirtId: string
  bottomId: string
  shoesId: string
  rawText: string
  inputMethod: InputMethod
}
