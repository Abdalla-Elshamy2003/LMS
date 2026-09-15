import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './lib/auth'
import { PageLoader } from './components/ui'
import Layout from './components/Layout'
import Login from './pages/Login'
import Register from './pages/Register'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import Dashboard from './pages/Dashboard'
import Students from './pages/Students'
import StudentProfile from './pages/StudentProfile'
import Courses from './pages/Courses'
import CourseDetail from './pages/LessonWorkspace'
import LearningHub from './components/LearningHub'
import Schedule from './pages/Schedule'
import Attendance from './pages/Attendance'
import Exams from './pages/Exams'
import Homework from './pages/Homework'
import Payments from './pages/Payments'
import Leaderboard from './pages/Leaderboard'
import Notifications from './pages/Notifications'
import Rules from './pages/Rules'
import Audit from './pages/Audit'
import Staff from './pages/Staff'
import Leads from './pages/Leads'
import Certificates from './pages/Certificates'
import VerifyCertificate from './pages/VerifyCertificate'
import CheckIn from './pages/CheckIn'
import SupportCenter from './pages/SupportCenter'
import FamilyFinance from './pages/FamilyFinance'
import AccountProfile from './pages/AccountProfile'
import Campaigns from './pages/Campaigns'
import AcademySettings from './pages/AcademySettings'
import Community from './pages/Community'
import PaymentReturn from './pages/PaymentReturn'
import GateScan from './pages/GateScan'
import GateLog from './pages/GateLog'
import CardScanner from './pages/CardScanner'
import CardPrint from './pages/CardPrint'
import Reports from './pages/Reports'

// Lazy-loaded: these are all public-only marketing pages (some pull in Three.js for 3D scenes)
// split into their own chunks — an authenticated user going straight to /app never pays that cost.
const Landing = lazy(() => import('./pages/TeacherLanding'))
const Home = lazy(() => import('./pages/Home'))
const Features = lazy(() => import('./pages/Features'))
const Pricing = lazy(() => import('./pages/Pricing'))
const About = lazy(() => import('./pages/About'))
const Contact = lazy(() => import('./pages/Contact'))
const SuccessStories = lazy(() => import('./pages/SuccessStories'))
const Blog = lazy(() => import('./pages/Blog'))
const BlogPost = lazy(() => import('./pages/BlogPost'))
const TeacherProfile = lazy(() => import('./pages/TeacherProfile'))
const Checkout = lazy(() => import('./pages/Checkout'))

function Protected({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="min-h-screen grid place-items-center"><PageLoader /></div>
  if (!user) return <Navigate to="/login" replace />
  return children
}

function FullPageLoader() {
  return <div className="min-h-screen grid place-items-center bg-[#f6fbff]"><PageLoader /></div>
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Suspense fallback={<FullPageLoader />}><Home /></Suspense>} />
      <Route path="/t/:slug" element={<Suspense fallback={<FullPageLoader />}><Landing /></Suspense>} />
      <Route path="/features" element={<Suspense fallback={<FullPageLoader />}><Features /></Suspense>} />
      <Route path="/pricing" element={<Suspense fallback={<FullPageLoader />}><Pricing /></Suspense>} />
      <Route path="/about" element={<Suspense fallback={<FullPageLoader />}><About /></Suspense>} />
      <Route path="/contact" element={<Suspense fallback={<FullPageLoader />}><Contact /></Suspense>} />
      <Route path="/success-stories" element={<Suspense fallback={<FullPageLoader />}><SuccessStories /></Suspense>} />
      <Route path="/blog" element={<Suspense fallback={<FullPageLoader />}><Blog /></Suspense>} />
      <Route path="/blog/:slug" element={<Suspense fallback={<FullPageLoader />}><BlogPost /></Suspense>} />
      <Route path="/teachers/:id" element={<Suspense fallback={<FullPageLoader />}><TeacherProfile /></Suspense>} />
      <Route path="/checkout/:courseId" element={<Suspense fallback={<FullPageLoader />}><Checkout /></Suspense>} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/verify/:code" element={<VerifyCertificate />} />
      <Route path="/app/checkin/:token" element={<CheckIn />} />
      <Route path="/app/gate/:token" element={<GateScan />} />
      <Route path="/app" element={<Protected><Layout /></Protected>}>
        <Route index element={<Dashboard />} />
        <Route path="students" element={<Students />} />
        <Route path="students/:id" element={<StudentProfile />} />
        <Route path="profile" element={<AccountProfile />} />
        <Route path="academic-profile" element={<StudentProfile />} />
        <Route path="family" element={<Dashboard />} />
        <Route path="family/finance" element={<FamilyFinance />} />
        <Route path="courses" element={<Courses />} />
        <Route path="payment/return" element={<PaymentReturn />} />
        <Route path="learning" element={<LearningHub />} />
        <Route path="schedule" element={<Schedule />} />
        <Route path="courses/:id" element={<CourseDetail />} />
        <Route path="attendance" element={<Attendance />} />
        <Route path="gate-log" element={<GateLog />} />
        <Route path="card-scanner" element={<CardScanner />} />
        <Route path="cards" element={<CardPrint />} />
        <Route path="reports" element={<Reports />} />
        <Route path="exams" element={<Exams />} />
        <Route path="homework" element={<Homework />} />
        <Route path="payments" element={<Payments />} />
        <Route path="staff" element={<Staff />} />
        <Route path="leads" element={<Leads />} />
        <Route path="certificates" element={<Certificates />} />
        <Route path="leaderboard" element={<Leaderboard />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="support" element={<SupportCenter />} />
        <Route path="community" element={<Community />} />
        <Route path="campaigns" element={<Campaigns />} />
        <Route path="academy" element={<AcademySettings />} />
        <Route path="rules" element={<Rules />} />
        <Route path="audit" element={<Audit />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
