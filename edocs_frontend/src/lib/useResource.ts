import { useCallback, useEffect, useState } from 'react'
import { api } from '../services/api'

/**
 * Loads data through the API layer and reloads it whenever the data changes
 * (mock writes today; server push once the backend exists). Previous data stays
 * on screen while a reload is in flight, so views don't flicker.
 */
export function useResource<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tick, setTick] = useState(0)

  const reload = useCallback(() => setTick((t) => t + 1), [])

  useEffect(() => api.subscribe(reload), [reload])

  useEffect(() => {
    let alive = true
    load()
      .then((d) => {
        if (!alive) return
        setData(d)
        setError(null)
      })
      .catch((e: unknown) => alive && setError(e instanceof Error ? e.message : String(e)))
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  return { data, error, reload }
}
