import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import styles from './Modal.module.css'

/**
 * Modal — accessible overlay dialog rendered via React portal.
 *
 * @param {boolean}  isOpen
 * @param {function} onClose   Called on backdrop click or Escape key
 * @param {string}   title
 * @param {string}   size      'sm' | 'md' | 'lg'
 * @param {node}     children
 * @param {node}     footer    Rendered below body with a top border
 */
export default function Modal({
  isOpen,
  onClose,
  title,
  size = 'md',
  children,
  footer,
}) {
  const panelRef = useRef(null)

  // Close on Escape key
  useEffect(() => {
    if (!isOpen) return
    const handler = e => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [isOpen, onClose])

  // Move focus into modal when it opens
  useEffect(() => {
    if (isOpen && panelRef.current) {
      panelRef.current.focus()
    }
  }, [isOpen])

  if (!isOpen) return null

  return createPortal(
    <div
      className={styles.backdrop}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <div
        ref={panelRef}
        className={`${styles.panel} ${styles[size] ?? styles.md}`}
        onClick={e => e.stopPropagation()}
        tabIndex={-1}
      >
        <div className={styles.header}>
          <span id="modal-title" className={styles.title}>{title}</span>
          <button className={styles.closeBtn} onClick={onClose} aria-label="Close">×</button>
        </div>
        <div className={styles.body}>{children}</div>
        {footer && <div className={styles.footer}>{footer}</div>}
      </div>
    </div>,
    document.body
  )
}
