import { useCallback, useEffect, useRef } from 'react'

/** Freehand signature canvas. Reports a PNG data URL (or null when cleared). */
export function SignaturePad({ onChange, clearSignal }: { onChange: (dataUrl: string | null) => void; clearSignal: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const hasInk = useRef(false)

  const setup = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const ratio = window.devicePixelRatio || 1
    const { width, height } = canvas.getBoundingClientRect()
    canvas.width = width * ratio
    canvas.height = height * ratio
    const ctx = canvas.getContext('2d')!
    ctx.scale(ratio, ratio)
    ctx.lineWidth = 2
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.strokeStyle = '#0a0a0a'
    hasInk.current = false
  }, [])

  useEffect(() => {
    setup()
    onChange(null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clearSignal])

  useEffect(() => {
    // Mobile browsers fire resize when the URL bar hides; only reset on real width changes.
    let lastWidth = window.innerWidth
    const onResize = () => {
      if (window.innerWidth === lastWidth) return
      lastWidth = window.innerWidth
      setup()
      onChange(null)
    }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [setup, onChange])

  function point(e: React.PointerEvent<HTMLCanvasElement>) {
    const r = e.currentTarget.getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top }
  }

  return (
    <canvas
      ref={canvasRef}
      className="h-40 w-full cursor-crosshair touch-none"
      aria-label="Signature drawing area. Draw with a mouse, pen or finger."
      onPointerDown={(e) => {
        drawing.current = true
        e.currentTarget.setPointerCapture(e.pointerId)
        const ctx = e.currentTarget.getContext('2d')!
        const { x, y } = point(e)
        ctx.beginPath()
        ctx.moveTo(x, y)
      }}
      onPointerMove={(e) => {
        if (!drawing.current) return
        const ctx = e.currentTarget.getContext('2d')!
        const { x, y } = point(e)
        ctx.lineTo(x, y)
        ctx.stroke()
        hasInk.current = true
      }}
      onPointerUp={(e) => {
        drawing.current = false
        if (hasInk.current) onChange(e.currentTarget.toDataURL('image/png'))
      }}
    />
  )
}
