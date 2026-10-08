import { useState } from 'react'
import Icon from './Icon.jsx'

/* 이름 검색 + 가격 조건. 입력 중인 값만 state 에 두고, 검색을 누르면 URL 로 올린다.
 * 부모가 URL 값이 바뀔 때 key 를 바꿔 이 폼을 새로 그린다(뒤로가기 시 입력창도 돌아간다). */
export default function MenuFilters({ initialQuery, initialPrice, onSearch }) {
  const [query, setQuery] = useState(initialQuery)
  const [price, setPrice] = useState(initialPrice)

  function handleSubmit(event) {
    event.preventDefault()
    onSearch({ q: query.trim(), price })
  }

  return (
    <form className="filter-row" role="search" onSubmit={handleSubmit}>
      <label className="control">
        <Icon name="search" />
        <span className="sr-only">메뉴 이름</span>
        <input
          className="body2"
          type="search"
          placeholder="메뉴 이름으로 찾기"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </label>
      <label className="control">
        <span className="sr-only">가격 조건 (이 가격을 초과하는 메뉴만)</span>
        <input
          className="body2"
          inputMode="numeric"
          placeholder="가격 조건"
          value={price}
          onChange={(e) => setPrice(e.target.value.replace(/\D/g, '').slice(0, 9))}
        />
        <span className="control-suffix label1">원 초과</span>
      </label>
      <button className="btn btn-primary btn-lg label1 label1--bold" type="submit">검색</button>
    </form>
  )
}
