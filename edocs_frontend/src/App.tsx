import type { ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './state/auth'
import { ToastProvider } from './state/toast'
import { RequireAuth, RequirePermission } from './components/guards'
import type { Permission } from './lib/rbac'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import DocumentsPage from './pages/DocumentsPage'
import EditorPage from './pages/EditorPage'
import WorkflowPage from './pages/WorkflowPage'
import SignPage from './pages/SignPage'
import ArchivePage from './pages/ArchivePage'
import TemplatesPage from './pages/TemplatesPage'
import CompliancePage from './pages/CompliancePage'
import SettingsPage from './pages/SettingsPage'
import HelpPage from './pages/HelpPage'
import NotFoundPage from './pages/NotFoundPage'

function Protected({ children, permission }: { children: ReactNode; permission?: Permission }) {
  return <RequireAuth>{permission ? <RequirePermission permission={permission}>{children}</RequirePermission> : children}</RequireAuth>
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<Protected><DashboardPage /></Protected>} />
            <Route path="/documents" element={<Protected><DocumentsPage /></Protected>} />
            <Route path="/editor" element={<Navigate to="/documents" replace />} />
            <Route path="/editor/:id" element={<Protected><EditorPage /></Protected>} />
            <Route path="/workflow" element={<Navigate to="/documents?status=out_for_signature" replace />} />
            <Route path="/workflow/:id" element={<Protected><WorkflowPage /></Protected>} />
            <Route path="/sign" element={<Navigate to="/documents?status=out_for_signature" replace />} />
            <Route path="/sign/:id" element={<Protected permission="document:sign"><SignPage /></Protected>} />
            <Route path="/archive" element={<Protected permission="audit:read"><ArchivePage /></Protected>} />
            <Route path="/templates" element={<Protected><TemplatesPage /></Protected>} />
            <Route path="/compliance" element={<Protected><CompliancePage /></Protected>} />
            <Route path="/settings" element={<Protected><SettingsPage /></Protected>} />
            <Route path="/help" element={<Protected><HelpPage /></Protected>} />
            <Route path="*" element={<Protected><NotFoundPage /></Protected>} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
