import { createContext, useContext, useState } from 'react'

const Ctx = createContext(null)

const MOCK_JOBS = [
  {
    jobId: 'SR-2026-001',
    requesterId: 'user-1',
    status: 'CONFIRMED',
    serviceTypes: ['Driveway Clearing', 'Walkway'],
    address: '183 Maple Street, East York, ON M4J 3K2',
    scheduledTime: 'ASAP',
    specialNotes: 'Side gate is open. Dog is inside.',
    depositAmountCents: 7345,
    platformFeeCents: 985,
    hstCents: 956,
    netWorkerCents: 5404,
    currentWorkerName: 'Alex M.',
    currentWorkerRating: 4.8,
    currentWorkerDistance: '1.4 km',
    createdAt: '2026-04-03T09:14:00Z',
    completedAt: null,
    photoUrls: [],
  },
  {
    jobId: 'SR-2026-002',
    requesterId: 'user-1',
    status: 'RELEASED',
    serviceTypes: ['Driveway Clearing'],
    address: '42 Oak Avenue, North York, ON M2N 1A1',
    scheduledTime: '2026-03-28T08:00:00Z',
    specialNotes: '',
    depositAmountCents: 5085,
    platformFeeCents: 675,
    hstCents: 663,
    netWorkerCents: 4074,
    currentWorkerName: 'Jamie R.',
    currentWorkerRating: 4.6,
    createdAt: '2026-03-28T07:30:00Z',
    completedAt: '2026-03-28T09:15:00Z',
    photoUrls: [],
  },
]

const MOCK_USER = { displayName: 'Sarah K.', email: 'sarah@example.com', uid: 'user-1' }
const MOCK_WORKER = { displayName: 'Alex M.', email: 'alex@example.com', uid: 'worker-1', averageRating: 4.8, totalJobsCompleted: 47 }

const STATE_ORDER = ['REQUESTED','PENDING_DEPOSIT','CONFIRMED','IN_PROGRESS','COMPLETE','RELEASED']

export function MockStateProvider({ children }) {
  const [role, setRole] = useState('REQUESTER')
  const [jobs, setJobs] = useState(MOCK_JOBS)
  const [nextId, setNextId] = useState(3)

  function setJobStatus(jobId, status) {
    setJobs(prev => prev.map(j => j.jobId === jobId ? { ...j, status, completedAt: status === 'COMPLETE' ? new Date().toISOString() : j.completedAt } : j))
  }

  function addJob(data) {
    const id = `SR-2026-00${nextId}`
    setNextId(n => n + 1)
    const job = {
      jobId: id, requesterId: 'user-1', status: 'REQUESTED',
      currentWorkerName: 'Alex M.', currentWorkerRating: 4.8, currentWorkerDistance: '1.2 km',
      createdAt: new Date().toISOString(), completedAt: null, photoUrls: [],
      depositAmountCents: 0, platformFeeCents: 0, hstCents: 0, netWorkerCents: 0,
      ...data,
    }
    setJobs(prev => [job, ...prev])
    return id
  }

  function advanceJob(jobId) {
    setJobs(prev => prev.map(j => {
      if (j.jobId !== jobId) return j
      const idx = STATE_ORDER.indexOf(j.status)
      const next = idx < STATE_ORDER.length - 1 ? STATE_ORDER[idx + 1] : j.status
      return { ...j, status: next, completedAt: next === 'COMPLETE' ? new Date().toISOString() : j.completedAt }
    }))
  }

  return (
    <Ctx.Provider value={{ role, setRole, jobs, setJobStatus, addJob, advanceJob, mockUser: MOCK_USER, mockWorker: MOCK_WORKER }}>
      {children}
    </Ctx.Provider>
  )
}

export function useMock() {
  return useContext(Ctx)
}
