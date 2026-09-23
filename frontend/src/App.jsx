import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './lib/auth'
import { PageLoader } from './components/ui'
import Layout from './components/Layout'
import Login from './pages/Login'
import Register from './pages/Register'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'

// Every page below is split into its own chunk: a visitor only downloads the code for the screen they
// open. Public marketing pages (some pull in Three.js) and the signed-in app screens both benefit -
// the initial bundle is just the shell, auth screens and router.
const Landing = lazy(() => import('./pages/TeacherLanding'))
const Home = lazy(() => import('./pages/Home'))
const Features = lazy(() => import('./pages/Features'))
const BundleLanding = lazy(() => import('./pages/BundleLanding'))
const About = lazy(() => import('./pages/About'))
const Contact = lazy(() => import('./pages/Contact'))
const SuccessStories = lazy(() => import('./pages/SuccessStories'))
const Blog = lazy(() => import('./pages/Blog'))
const BlogPost = lazy(() => import('./pages/BlogPost'))
const TeacherProfile = lazy(() => import('./pages/TeacherProfile'))
const Checkout = lazy(() => import('./pages/Checkout'))
const VerifyCertificate = lazy(() => import('./pages/VerifyCertificate'))
const StudentVerifyPage = lazy(() => import('./features/student-verification/StudentVerifyPage'))

const CheckIn = lazy(() => import('./pages/CheckIn'))
const GateScan = lazy(() => import('./pages/GateScan'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Students = lazy(() => import('./pages/Students'))
const StudentProfile = lazy(() => import('./pages/StudentProfile'))
const Courses = lazy(() => import('./pages/Courses'))
const CourseDetail = lazy(() => import('./pages/LessonWorkspace'))
const LearningHub = lazy(() => import('./components/LearningHub'))
const Schedule = lazy(() => import('./pages/Schedule'))
const Attendance = lazy(() => import('./pages/Attendance'))
const Exams = lazy(() => import('./pages/Exams'))
const Homework = lazy(() => import('./pages/Homework'))
const Payments = lazy(() => import('./pages/Payments'))
const Leaderboard = lazy(() => import('./pages/Leaderboard'))
const Notifications = lazy(() => import('./pages/Notifications'))
const Rules = lazy(() => import('./pages/Rules'))
const Audit = lazy(() => import('./pages/Audit'))
const Staff = lazy(() => import('./pages/Staff'))
const Leads = lazy(() => import('./pages/Leads'))
const Certificates = lazy(() => import('./pages/Certificates'))
const SupportCenter = lazy(() => import('./pages/SupportCenter'))
const FamilyFinance = lazy(() => import('./pages/FamilyFinance'))
const AccountProfile = lazy(() => import('./pages/AccountProfile'))
const Campaigns = lazy(() => import('./pages/Campaigns'))
const AcademySettings = lazy(() => import('./pages/AcademySettings'))
const Community = lazy(() => import('./pages/Community'))
const PaymentReturn = lazy(() => import('./pages/PaymentReturn'))
const GateLog = lazy(() => import('./pages/GateLog'))
const CardScanner = lazy(() => import('./pages/CardScanner'))
const CardPrint = lazy(() => import('./pages/CardPrint'))
const Reports = lazy(() => import('./pages/Reports'))
const AssistantDesk = lazy(() => import('./pages/AssistantDesk'))
const Bundles = lazy(() => import('./pages/Bundles'))
const Assistants = lazy(() => import('./pages/Assistants'))

function Protected({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="min-h-screen grid place-items-center"><PageLoader /></div>
  if (!user) return <Navigate to="/login" replace />
  return children
}

function FullPageLoader() {
  return <div className="min-h-screen grid place-items-center bg-[#f6fbff]"><PageLoader /></div>
}

/** A lazily loaded full-screen page (no app shell around it). */
const standalone = (element) => <Suspense fallback={<FullPageLoader />}>{element}</Suspense>

export default function App() {
  return (
    <Routes>
      <Route path="/" element={standalone(<Home />)} />
      <Route path="/t/:slug" element={standalone(<Landing />)} />
      <Route path="/features" element={standalone(<Features />)} />
      <Route path="/packages/:slug" element={standalone(<BundleLanding />)} />
      {/* The pricing page was retired; old links land on the home page. */}
      <Route path="/pricing" element={<Navigate to="/" replace />} />
      <Route path="/about" element={standalone(<About />)} />
      <Route path="/contact" element={standalone(<Contact />)} />
      <Route path="/success-stories" element={standalone(<SuccessStories />)} />
      <Route path="/blog" element={standalone(<Blog />)} />
      <Route path="/blog/:slug" element={standalone(<BlogPost />)} />
      <Route path="/teachers/:id" element={standalone(<TeacherProfile />)} />
      <Route path="/checkout/:courseId" element={standalone(<Checkout />)} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/verify/:code" element={standalone(<VerifyCertificate />)} />
      {/* Public on purpose: the QR on a student's card/phone lands here and must never require login. */}
      <Route path="/student/verify/:token" element={standalone(<StudentVerifyPage />)} />
      <Route path="/app/checkin/:token" element={standalone(<CheckIn />)} />
      <Route path="/app/gate/:token" element={standalone(<GateScan />)} />
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
        <Route path="bundles" element={<Bundles />} />
        <Route path="assistant" element={<AssistantDesk />} />
        <Route path="assistants" element={<Assistants />} />
        <Route path="rules" element={<Rules />} />
        <Route path="audit" element={<Audit />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
