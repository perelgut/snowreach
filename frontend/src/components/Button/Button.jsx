import Spinner from '../Spinner/Spinner'
import styles from './Button.module.css'

/**
 * Button — primary interactive element.
 *
 * @param {string}   variant   'primary' | 'secondary' | 'ghost' | 'danger'
 * @param {string}   size      'sm' | 'md' | 'lg'
 * @param {boolean}  loading   Shows spinner and blocks interaction
 * @param {boolean}  disabled
 * @param {boolean}  fullWidth
 * @param {string}   type      'button' | 'submit' | 'reset'
 * @param {function} onClick
 * @param {node}     children
 */
export default function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  fullWidth = false,
  type = 'button',
  onClick,
  children,
}) {
  const isDisabled = disabled || loading

  const className = [
    styles.btn,
    styles[variant] ?? styles.primary,
    styles[size] ?? styles.md,
    fullWidth ? styles.fullWidth : '',
    isDisabled ? styles.disabled : '',
  ].filter(Boolean).join(' ')

  return (
    <button
      type={type}
      className={className}
      disabled={isDisabled}
      onClick={isDisabled ? undefined : onClick}
    >
      {loading ? (
        <Spinner size="sm" color="currentColor" />
      ) : (
        children
      )}
    </button>
  )
}
