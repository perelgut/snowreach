import styles from './Card.module.css'

/**
 * Card — white rounded container with optional header, footer, and click handler.
 *
 * @param {string}   padding   'sm' | 'md' | 'lg'
 * @param {boolean}  shadow    Drop shadow (default: true)
 * @param {node}     header    Rendered above content with a bottom border
 * @param {node}     footer    Rendered below content with a top border
 * @param {function} onClick   If provided, card is interactive with hover lift
 * @param {node}     children
 */
export default function Card({
  padding = 'md',
  shadow = true,
  header,
  footer,
  onClick,
  children,
}) {
  const hasSlots = header || footer

  const className = [
    styles.card,
    styles[padding] ?? styles.md,
    shadow ? styles.shadow : '',
    onClick ? styles.clickable : '',
    !hasSlots ? styles.plain : '',
  ].filter(Boolean).join(' ')

  return (
    <div className={className} onClick={onClick}>
      {header && <div className={styles.header}>{header}</div>}
      {hasSlots ? (
        <div className={styles.body}>{children}</div>
      ) : (
        children
      )}
      {footer && <div className={styles.footer}>{footer}</div>}
    </div>
  )
}
