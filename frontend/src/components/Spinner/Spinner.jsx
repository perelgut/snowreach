/* Spinner — pure CSS animated ring, no external dependencies */

const SIZES = { sm: 16, md: 24, lg: 40 }

export default function Spinner({ size = 'md', color = 'var(--color-primary)' }) {
  const px = SIZES[size] ?? SIZES.md
  const thickness = size === 'sm' ? 2 : size === 'lg' ? 4 : 3

  return (
    <>
      <style>{`
        @keyframes ysm-spin { to { transform: rotate(360deg); } }
      `}</style>
      <span
        aria-label="Loading"
        style={{
          display: 'inline-block',
          width: px,
          height: px,
          borderRadius: '50%',
          border: `${thickness}px solid ${color}`,
          borderTopColor: 'transparent',
          animation: 'ysm-spin 0.7s linear infinite',
          flexShrink: 0,
        }}
      />
    </>
  )
}
