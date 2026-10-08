import axios from 'axios'

/* 서버 주소는 이 인스턴스 한 곳에만 적는다. */
const client = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000,
})

/* 오류 응답 템플릿 { code, description, detail } 을 그대로 담는 오류.
 * 오류 응답에는 httpStatus 가 없으므로 code 와 description 으로 판단한다. */
export class ApiError extends Error {
  constructor({ code, description, detail = '', status = 0 }) {
    super(description)
    this.name = 'ApiError'
    this.code = code
    this.description = description
    this.detail = detail
    this.status = status
  }
}

export const ERROR = {
  MENU_NOT_FOUND: 'ERROR_CODE_00001',
  CATEGORY_NOT_FOUND: 'ERROR_CODE_00002',
  BAD_REQUEST: 'ERROR_CODE_00003',
  SERVER: 'ERROR_CODE_99999',
  NETWORK: 'NETWORK_ERROR',
}

function toApiError(error) {
  const { response } = error
  if (response?.data?.code) {
    return new ApiError({ ...response.data, status: response.status })
  }
  if (response) {
    return new ApiError({
      code: `HTTP_${response.status}`,
      description: '요청을 처리하지 못했어요',
      detail: `서버가 ${response.status} 상태로 응답했어요.`,
      status: response.status,
    })
  }
  return new ApiError({
    code: ERROR.NETWORK,
    description: '서버에 연결할 수 없어요',
    detail: `${client.defaults.baseURL} 서버가 실행 중인지 확인해 주세요.`,
  })
}

/* 정상 응답 템플릿 { httpStatus, message, result } 에서 result 만 꺼낸다.
 * 상태 코드로 분기하지 않는다. 삭제는 본문 httpStatus 가 204 여도 실제 HTTP 는 200 이다. */
client.interceptors.response.use(
  (response) => response.data?.result ?? {},
  (error) => Promise.reject(toApiError(error)),
)

export default client
