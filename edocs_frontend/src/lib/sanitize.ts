import DOMPurify from 'dompurify'

const ALLOWED_TAGS = ['h2', 'h3', 'p', 'ul', 'ol', 'li', 'strong', 'b', 'em', 'i', 'u', 'del', 'ins', 'span', 'font', 'br', 'hr', 'blockquote', 'div']
const ALLOWED_ATTR = ['face', 'size', 'class']

/** Strips scripts, event handlers and unknown markup from document HTML before it reaches the DOM. */
export function sanitizeDocumentHtml(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR })
}

export function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!)
}
