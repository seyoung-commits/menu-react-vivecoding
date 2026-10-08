import { useEffect, useRef, useState } from 'react'
import { groupCategories } from '../api/categories.js'
import { formatPrice } from './format.js'
import Icon from './Icon.jsx'
import MenuAiAssistant from './MenuAiAssistant.jsx'
import MenuPhotoField from './MenuPhotoField.jsx'

const NAME_MAX = 30 // DB menu_name VARCHAR(30)
const PRICE_MAX = 2147483647 // int

function validate({ menuName, menuPrice, categoryCode }) {
  const errors = {}
  const name = menuName.trim()
  if (!name) errors.menuName = '메뉴 이름을 입력해 주세요.'
  else if (name.length > NAME_MAX) errors.menuName = `${NAME_MAX}자 이내로 입력해 주세요.`

  if (menuPrice === '') errors.menuPrice = '가격을 입력해 주세요.'
  else if (Number(menuPrice) <= 0) errors.menuPrice = '0원보다 큰 금액을 입력해 주세요.'
  else if (Number(menuPrice) > PRICE_MAX) errors.menuPrice = '너무 큰 금액이에요.'

  if (!categoryCode) errors.categoryCode = '카테고리를 골라 주세요.'
  return errors
}

const FIELD_ORDER = ['menuName', 'menuPrice', 'categoryCode']

/* 등록·수정 공용 폼. 비어 있는 값이 있으면 보내지 않고 필드 아래에 알린다.
 * 서버가 오류 응답을 주면 description 과 detail 을 폼 위에 보여준다. */
export default function MenuForm({ initial, categories, submitLabel, onSubmit, onCancel }) {
  const groups = groupCategories(categories)
  const subCategories = groups.flatMap((g) => g.children)
  const initialIsSub = initial && subCategories.some((c) => c.categoryCode === initial.categoryCode)

  const [values, setValues] = useState(() => ({
    menuName: initial?.menuName ?? '',
    menuIngredients: initial?.menuIngredients ?? '',
    menuDescription: initial?.menuDescription ?? '',
    menuPrice: initial ? String(initial.menuPrice) : '',
    categoryCode: initialIsSub ? String(initial.categoryCode) : '',
    orderableStatus: initial?.orderableStatus === 'N' ? 'N' : 'Y',
  }))
  const [aiBusy, setAiBusy] = useState(false)
  const [imageFile, setImageFile] = useState(null)
  const [imageError, setImageError] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [saving, setSaving] = useState(false)
  const [serverError, setServerError] = useState(null)
  const noticeRef = useRef(null)

  const errors = validate(values)
  const shownError = (field) => (submitted ? errors[field] : undefined)
  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.value }))

  useEffect(() => {
    if (serverError) noticeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }, [serverError])

  async function handleSubmit(event) {
    event.preventDefault()
    if (aiBusy) return
    setSubmitted(true)
    const firstInvalid = FIELD_ORDER.find((field) => errors[field])
    if (firstInvalid) {
      event.currentTarget.elements.namedItem(firstInvalid)?.focus()
      return
    }
    if (imageError) {
      event.currentTarget.elements.namedItem('menuImage')?.focus()
      return
    }
    setSaving(true)
    setServerError(null)
    try {
      await onSubmit({ ...values, imageFile }) // 성공하면 부모가 다른 화면으로 이동한다
    } catch (error) {
      setServerError(error)
      setSaving(false)
    }
  }

  return (
    <form className="form-card" noValidate onSubmit={handleSubmit}>
      {serverError && (
        <div ref={noticeRef} className="notice" role="alert">
          <Icon name="alert" />
          <div>
            <p className="notice-title label1 label1--bold">{serverError.description}</p>
            <p className="notice-detail caption1">
              {serverError.detail}
              {serverError.code && ` · ${serverError.code}`}
            </p>
          </div>
        </div>
      )}

      <div className="form-grid">
        <MenuPhotoField key={imageFile?.lastModified ?? "photo"} existingUrl={initial?.imageUrl} selectedFile={imageFile} disabled={saving || aiBusy} onChange={setImageFile} onErrorChange={setImageError} />
        <div className="field span-2">
          <label className="field-label label1 label1--bold" htmlFor="menuName">
            메뉴 이름<span className="required" aria-hidden="true">*</span>
          </label>
          <div className={`control${shownError('menuName') ? ' is-invalid' : ''}`}>
            <input
              id="menuName"
              name="menuName" disabled={saving || aiBusy}
              className="body1"
              placeholder="예: 딸기 민트 빙수"
              maxLength={NAME_MAX}
              autoComplete="off"
              value={values.menuName}
              onChange={set('menuName')}
              aria-invalid={Boolean(shownError('menuName'))}
              aria-describedby="menuName-msg"
            />
          </div>
          <div className="field-foot caption1">
            <span id="menuName-msg" className={shownError('menuName') ? 'field-error' : 'field-help'}>
              {shownError('menuName') ?? '같은 이름의 메뉴가 있어도 등록돼요.'}
            </span>
            <span className="field-count">{values.menuName.trim().length}/{NAME_MAX}</span>
          </div>
        </div>

        <div className="field">
          <label className="field-label label1 label1--bold" htmlFor="menuPrice">
            가격<span className="required" aria-hidden="true">*</span>
          </label>
          <div className={`control${shownError('menuPrice') ? ' is-invalid' : ''}`}>
            <input
              id="menuPrice"
              name="menuPrice" disabled={saving || aiBusy}
              className="body1"
              inputMode="numeric"
              placeholder="0"
              autoComplete="off"
              value={values.menuPrice}
              onChange={(e) => setValues((v) => ({ ...v, menuPrice: e.target.value.replace(/\D/g, '').slice(0, 10) }))}
              aria-invalid={Boolean(shownError('menuPrice'))}
              aria-describedby="menuPrice-msg"
            />
            <span className="control-suffix label1">원</span>
          </div>
          <div className="field-foot caption1">
            <span id="menuPrice-msg" className={shownError('menuPrice') ? 'field-error' : 'field-help'}>
              {shownError('menuPrice') ?? (values.menuPrice ? formatPrice(values.menuPrice) : '숫자만 입력해요.')}
            </span>
          </div>
        </div>

        <div className="field">
          <label className="field-label label1 label1--bold" htmlFor="categoryCode">
            카테고리<span className="required" aria-hidden="true">*</span>
          </label>
          <div className={`control${shownError('categoryCode') ? ' is-invalid' : ''}`}>
            <select
              id="categoryCode"
              name="categoryCode" disabled={saving || aiBusy}
              className="body1"
              value={values.categoryCode}
              onChange={set('categoryCode')}
              aria-invalid={Boolean(shownError('categoryCode'))}
              aria-describedby="categoryCode-msg"
            >
              <option value="">카테고리 선택</option>
              {groups.map((group) => (
                <optgroup key={group.categoryCode} label={group.categoryName}>
                  {group.children.map((c) => (
                    <option key={c.categoryCode} value={c.categoryCode}>{c.categoryName}</option>
                  ))}
                </optgroup>
              ))}
            </select>
            <span className="select-arrow"><Icon name="down" size="sm" /></span>
          </div>
          <div className="field-foot caption1">
            <span id="categoryCode-msg" className={shownError('categoryCode') ? 'field-error' : 'field-help'}>
              {shownError('categoryCode') ??
                (initial && !initialIsSub
                  ? `지금은 상위 카테고리 ‘${initial.categoryName}’에 있어요. 하위 카테고리를 골라 주세요.`
                  : '하위 카테고리만 고를 수 있어요.')}
            </span>
          </div>
        </div>

        <div className="field span-2">
          <label className="field-label label1 label1--bold" htmlFor="menuIngredients">재료 (선택)</label>
          <textarea id="menuIngredients" className="ai-textarea body2" value={values.menuIngredients} onChange={set('menuIngredients')} maxLength={1000} disabled={saving || aiBusy} placeholder="예: 우유, 딸기, 민트" />
        </div>
        <div className="field span-2">
          <label className="field-label label1 label1--bold" htmlFor="menuDescription">메뉴 설명 (선택)</label>
          <textarea id="menuDescription" className="ai-textarea body2" value={values.menuDescription} onChange={set('menuDescription')} maxLength={2000} disabled={saving || aiBusy} placeholder="직접 작성하거나 AI 설명을 적용해 주세요." />
        </div>
        <MenuAiAssistant key={[values.menuName, values.menuPrice, values.categoryCode, values.menuIngredients].join("|")} menu={{ ...values, menuPrice: values.menuPrice ? Number(values.menuPrice) : null, categoryName: subCategories.find((category) => String(category.categoryCode) === values.categoryCode)?.categoryName ?? '' }}
          disabled={saving} onDescription={(text) => setValues((value) => ({ ...value, menuDescription: text }))}
          onImage={(file) => { setImageFile(file); setImageError(false) }} onBusyChange={setAiBusy} />
        <fieldset className="field fieldset span-2">
          <legend className="field-label label1 label1--bold">
            주문 가능 여부
          </legend>
          <div className="segmented label1">
            {[['Y', '주문 가능'], ['N', '주문 불가']].map(([value, label]) => (
              <label key={value}>
                <input
                  type="radio"
                  name="orderableStatus" disabled={saving || aiBusy}
                  value={value}
                  checked={values.orderableStatus === value}
                  onChange={set('orderableStatus')}
                />
                {label}
              </label>
            ))}
          </div>
        </fieldset>
      </div>

      <div className="form-actions">
        <button className="btn btn-outline btn-lg label1 label1--bold" type="button" onClick={onCancel} disabled={saving || aiBusy}>
          취소
        </button>
        <button className="btn btn-primary btn-lg label1 label1--bold" type="submit" disabled={saving || aiBusy}>
          {saving ? '저장하는 중…' : submitLabel}
        </button>
      </div>
    </form>
  )
}
