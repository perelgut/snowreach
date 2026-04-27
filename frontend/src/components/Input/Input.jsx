import styles from './Input.module.css'

/**
 * Input — labelled form field with error and hint support.
 *
 * @param {string}   label
 * @param {string}   error       Shown below input in error colour
 * @param {string}   hint        Shown below input in gray
 * @param {string}   type        HTML input type (default: 'text')
 * @param {boolean}  required
 * @param {boolean}  disabled
 * @param {string}   placeholder
 * @param {string}   id          Auto-derived from label if omitted
 * @param {string}   autoComplete  HTML autocomplete attribute (e.g. "current-password", "new-password")
 * @param {boolean}  multiline   Renders <textarea> instead of <input>
 * @param {*}        value, onChange, onBlur  — forwarded to the element
 */
export default function Input({
  label,
  error,
  hint,
  type = 'text',
  required = false,
  disabled = false,
  placeholder,
  id,
  autoComplete,
  multiline = false,
  value,
  onChange,
  onBlur,
}) {
  // Auto-generate id from label text if not provided
  const inputId = id ?? (label ? label.toLowerCase().replace(/\s+/g, '-') : undefined)

  const inputClass = [
    styles.input,
    multiline ? styles.textarea : '',
    error ? styles.hasError : '',
  ].filter(Boolean).join(' ')

  return (
    <div className={styles.field}>
      {label && (
        <label htmlFor={inputId} className={styles.label}>
          {label}
          {required && <span className={styles.required}>*</span>}
        </label>
      )}

      {multiline ? (
        <textarea
          id={inputId}
          className={inputClass}
          disabled={disabled}
          placeholder={placeholder}
          value={value}
          onChange={onChange}
          onBlur={onBlur}
        />
      ) : (
        <input
          id={inputId}
          type={type}
          className={inputClass}
          disabled={disabled}
          placeholder={placeholder}
          autoComplete={autoComplete}
          value={value}
          onChange={onChange}
          onBlur={onBlur}
        />
      )}

      {error && <span className={styles.error}>{error}</span>}
      {!error && hint && <span className={styles.hint}>{hint}</span>}
    </div>
  )
}
