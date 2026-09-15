import { useParams, Link, Navigate } from 'react-router-dom'
import { Clock, ArrowLeft, ChevronRight } from 'lucide-react'
import MarketingNav from '../components/marketing/MarketingNav'
import MarketingFooter from '../components/marketing/MarketingFooter'
import { findBlogPost, BLOG_POSTS } from '../lib/blogPosts'
import { fmtDate } from '../lib/format'

function renderInline(text) {
  const parts = text.split(/\*\*(.+?)\*\*/g)
  return parts.map((part, i) => (i % 2 === 1 ? <strong key={i} className="font-extrabold text-ink-800">{part}</strong> : part))
}

export default function BlogPost() {
  const { slug } = useParams()
  const post = findBlogPost(slug)
  if (!post) return <Navigate to="/blog" replace />

  const related = BLOG_POSTS.filter((p) => p.slug !== slug).slice(0, 2)

  return (
    <div className="min-h-screen overflow-x-clip bg-[#f6fbff] text-ink-800">
      <MarketingNav solid />

      <article className="mx-auto max-w-3xl px-5 pb-20 pt-32 sm:pt-40">
        <Link to="/blog" className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-600 hover:underline"><ChevronRight size={16} /> كل المقالات</Link>
        <span className="mt-6 chip bg-brand-50 text-brand-700">{post.category}</span>
        <h1 className="mt-4 text-3xl font-black leading-snug text-ink-800 sm:text-4xl">{post.title}</h1>
        <div className="mt-4 flex items-center gap-4 text-sm text-ink-400">
          <span>{fmtDate(post.date)}</span>
          <span className="flex items-center gap-1"><Clock size={13} /> {post.readTime}</span>
        </div>

        <div className="prose-none mt-8 space-y-5 leading-loose text-ink-700">
          {post.content.map((p, i) => <p key={i}>{renderInline(p)}</p>)}
        </div>

        <div className="mt-12 rounded-3xl bg-gradient-to-l from-brand-600 to-brand-800 p-7 text-center text-white">
          <p className="text-lg font-black">جاهز تطبّق ده في مؤسستك؟</p>
          <Link to="/register" className="btn mt-4 inline-flex bg-white px-6 py-2.5 text-brand-700 hover:bg-brand-50">ابدأ مجاناً <ArrowLeft size={16} /></Link>
        </div>

        {related.length > 0 && (
          <div className="mt-14">
            <p className="mb-4 font-extrabold text-ink-800">مقالات ذات صلة</p>
            <div className="grid gap-4 sm:grid-cols-2">
              {related.map((p) => (
                <Link key={p.slug} to={`/blog/${p.slug}`} className="card block p-5 transition hover:shadow-glow">
                  <p className="font-bold text-ink-800">{p.title}</p>
                  <p className="mt-1.5 line-clamp-2 text-sm text-ink-500">{p.excerpt}</p>
                </Link>
              ))}
            </div>
          </div>
        )}
      </article>

      <MarketingFooter />
    </div>
  )
}
