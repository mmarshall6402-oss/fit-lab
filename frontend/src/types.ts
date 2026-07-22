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
