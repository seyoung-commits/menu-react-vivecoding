import { useCallback, useEffect, useState } from 'react'

/* api/ 의 요청 함수를 부르고 { data, error, loading, reload } 를 돌려준다.
 * key 가 바뀌면 다시 요청하고, 늦게 도착한 이전 응답은 버린다. */
export function useAsync(request, key) {
  const [version, setVersion] = useState(0)
  const [state, setState] = useState({ key: null, data: undefined, error: null })
  const requestKey = `${key}#${version}`

  useEffect(() => {
    let ignore = false
    request().then(
      (data) => !ignore && setState({ key: requestKey, data, error: null }),
      (error) => !ignore && setState({ key: requestKey, data: undefined, error }),
    )
    return () => {
      ignore = true
    }
    // request 는 렌더마다 새로 만들어지므로 key 로만 다시 요청한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey])

  const reload = useCallback(() => setVersion((v) => v + 1), [])
  const loading = state.key !== requestKey
  return {
    data: loading ? undefined : state.data,
    error: loading ? null : state.error,
    loading,
    reload,
  }
}
