import { Link } from 'react-router-dom'
import { AppShell } from '../layouts/AppShell'

export default function NotFoundPage() {
  return (
    <AppShell breadcrumb="Not found">
      <main className="mx-auto flex max-w-xl flex-col items-start gap-4 px-4 py-24 sm:px-8">
        <p className="hash text-sm text-muted">404</p>
        <h1 className="text-3xl font-semibold tracking-tight">This page doesn't exist</h1>
        <p className="text-ink-2">The link may be out of date, or the document may have moved to the archive.</p>
        <Link to="/" className="btn btn-primary">Go to dashboard</Link>
      </main>
    </AppShell>
  )
}
