import { Routes, Route, Navigate } from 'react-router-dom'
import RequesterLayout from './layouts/RequesterLayout'
import WorkerLayout from './layouts/WorkerLayout'
import AdminLayout from './layouts/AdminLayout'
import DevRoleSwitcher from './components/DevRoleSwitcher'

import RequesterHome from './pages/requester/Home'
import PostJob from './pages/requester/PostJob'
import JobList from './pages/requester/JobList'
import JobStatus from './pages/requester/JobStatus'

import WorkerEarnings from './pages/worker/Earnings'
import JobRequest from './pages/worker/JobRequest'
import ActiveJob from './pages/worker/ActiveJob'

import AdminDashboard from './pages/admin/Dashboard'
import AdminJobDetail from './pages/admin/JobDetail'

export default function App() {
  return (
    <>
      <Routes>
        <Route path="/" element={<Navigate to="/requester" replace />} />

        <Route path="/requester" element={<RequesterLayout />}>
          <Route index element={<RequesterHome />} />
          <Route path="post-job" element={<PostJob />} />
          <Route path="jobs" element={<JobList />} />
          <Route path="jobs/:id" element={<JobStatus />} />
        </Route>

        <Route path="/worker" element={<WorkerLayout />}>
          <Route index element={<WorkerEarnings />} />
          <Route path="job-request" element={<JobRequest />} />
          <Route path="active-job" element={<ActiveJob />} />
        </Route>

        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<AdminDashboard />} />
          <Route path="jobs" element={<AdminDashboard tab="jobs" />} />
          <Route path="jobs/:id" element={<AdminJobDetail />} />
          <Route path="users" element={<AdminDashboard tab="users" />} />
          <Route path="disputes" element={<AdminDashboard tab="disputes" />} />
        </Route>

        <Route path="*" element={<Navigate to="/requester" replace />} />
      </Routes>
      <DevRoleSwitcher />
    </>
  )
}
