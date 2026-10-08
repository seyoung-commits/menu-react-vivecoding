import { useEffect, useRef, useState } from 'react'

const MAX_BYTES = 5 * 1024 * 1024

export default function MenuPhotoField({ existingUrl, selectedFile, disabled, onChange, onErrorChange }) {
  const [previewSelection, setPreviewSelection] = useState(null)
  const [error, setError] = useState('')
  const [brokenUrl, setBrokenUrl] = useState(null)
  const inputRef = useRef(null)
  const previewUrl = previewSelection?.file === selectedFile ? previewSelection.url : existingUrl

  // 외부에서 선택한 AI 사진과 파일 입력 사진이 같은 미리보기 경로를 사용한다.
  useEffect(() => {
    if (!selectedFile) return
    const url = URL.createObjectURL(selectedFile)
    const timer = setTimeout(() => setPreviewSelection({ file: selectedFile, url }), 0)
    return () => { clearTimeout(timer); URL.revokeObjectURL(url) }
  }, [selectedFile])

  function choose(event) {
    const file = event.target.files?.[0]
    if (!file) return
    const issue = !['image/jpeg', 'image/png'].includes(file.type)
      ? 'JPG 또는 PNG 사진을 골라 주세요.'
      : file.size === 0 ? '빈 파일은 올릴 수 없어요.'
        : file.size > MAX_BYTES ? '사진은 5MB 이하로 골라 주세요.' : ''
    setError(issue)
    onErrorChange(Boolean(issue))
    if (issue) {
      event.target.value = ''
      return
    }
    setBrokenUrl(null)
    onChange(file)
  }

  function reset() {
    setError('')
    setBrokenUrl(null)
    inputRef.current.value = ''
    onChange(null)
    onErrorChange(false)
  }

  return (
    <div className="field span-2">
      <label className="field-label label1 label1--bold" htmlFor="menuImage">메뉴 사진 <span className="field-help label2">(선택)</span></label>
      <div className="photo-picker">
        <div className="photo-preview">
          {previewUrl && brokenUrl !== previewUrl ? (
            <img src={previewUrl} alt={selectedFile ? '선택한 메뉴 사진 미리보기' : '현재 메뉴 사진'} onError={() => setBrokenUrl(previewUrl)} />
          ) : (
            <span className="body2">{previewUrl ? '사진을 불러오지 못했어요' : '사진을 선택해 주세요'}</span>
          )}
        </div>
        <input ref={inputRef} id="menuImage" name="menuImage" type="file"
          accept="image/jpeg,image/png" className="photo-input body2"
          disabled={disabled} onChange={choose}
          aria-invalid={Boolean(error)} aria-describedby="menuImage-help menuImage-error" />
        <p id="menuImage-help" className="field-help caption1">
          JPG·PNG, 최대 5MB·2천만 화소 · 등록하거나 수정할 때 저장돼요.
          {existingUrl && ' 새 사진을 선택하지 않으면 현재 사진을 유지해요.'}
        </p>
        {error && <p id="menuImage-error" className="field-error caption1" role="alert">{error}</p>}
        {(selectedFile || error) && (
          <button className="btn btn-outline btn-sm label2" type="button" disabled={disabled} onClick={reset}>
            {existingUrl ? '현재 사진 유지' : '사진 선택 취소'}
          </button>
        )}
      </div>
    </div>
  )
}
