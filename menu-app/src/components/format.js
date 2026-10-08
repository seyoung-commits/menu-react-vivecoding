import { findTopCategory } from '../api/categories.js'

export const formatPrice = (price) => `${Number(price).toLocaleString('ko-KR')}원`

/* 상위 카테고리마다 썸네일 색 하나. 식사 1 · 음료 2 · 디저트 3 */
const TONES = { 1: 'tone-meal', 2: 'tone-drink', 3: 'tone-dessert' }

export function describeCategory(categories, menu) {
  const top = findTopCategory(categories ?? [], menu.categoryCode)
  const isTop = top?.categoryCode === menu.categoryCode
  return {
    tone: TONES[top?.categoryCode] ?? 'tone-etc',
    topName: top && !isTop ? top.categoryName : null,
    name: menu.categoryName,
  }
}

export const orderableLabel = (status) => (status === 'Y' ? '주문 가능' : '주문 불가')
