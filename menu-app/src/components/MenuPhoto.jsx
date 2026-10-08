import { useState } from 'react'

// 주소가 바뀌면 새 이미지를 시도하고, 없는 사진/깨진 주소는 기존 글자 디자인을 쓴다.
export default function MenuPhoto({ menu, tone = '', large = false }) {
  const [failedUrl, setFailedUrl] = useState(null)
  const showImage = menu.imageUrl && failedUrl !== menu.imageUrl
  return (
    <div className={`thumb ${large ? 'thumb-lg' : ''} ${tone}`}>
      {showImage ? (
        <img className="menu-photo" src={menu.imageUrl} alt={`${menu.menuName} 사진`}
          loading={large ? 'eager' : 'lazy'} onError={() => setFailedUrl(menu.imageUrl)} />
      ) : (
        <span className={`thumb-letter ${large ? 'display1 display1--bold' : 'display2 display2--bold'}`} aria-hidden="true">
          {menu.menuName.trim().charAt(0)}
        </span>
      )}
    </div>
  )
}
