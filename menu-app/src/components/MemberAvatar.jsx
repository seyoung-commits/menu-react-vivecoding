import { useState } from 'react'

export default function MemberAvatar({ user, size = 'small' }) {
  const [failedUrl, setFailedUrl] = useState(null)
  const imageUrl = user.profileImageUrl
  const nickname = user.nickname || '카카오 사용자'
  const showImage = imageUrl && failedUrl !== imageUrl

  return (
    <span className={`member-avatar member-avatar--${size} ${size === 'large' ? 'title2 title2--bold' : 'label1 label1--bold'}`}>
      {showImage ? (
        <img src={imageUrl} alt={`${nickname}님의 프로필 사진`} referrerPolicy="no-referrer" decoding="async"
          onError={() => setFailedUrl(imageUrl)} />
      ) : <span aria-label={`${nickname}님의 기본 프로필`}>{[...nickname][0]}</span>}
    </span>
  )
}
