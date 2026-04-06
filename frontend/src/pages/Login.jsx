import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import s from './Auth.module.css'

export default function Login() {
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async e => {
    e.preventDefault()
    setError(''); setLoading(true)
    try {
      await login(form.email, form.password)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid credentials. Please try again.')
    } finally { setLoading(false) }
  }

  return (
    <div className={s.page}>
      <div className={s.card}>
        <div className={s.brand}>
          <div className={s.brandMark}>
            <svg width="22" height="22" viewBox="0 0 32 32" fill="none">
              <path d="M4 8h24L16 28 4 8z" fill="url(#lg)" opacity="0.9"/>
              <defs><linearGradient id="lg" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse"><stop stopColor="#6366f1"/><stop offset="1" stopColor="#22d3ee"/></linearGradient></defs>
            </svg>
          </div>
          <span className={s.brandName}>FinTech</span>
        </div>

        <h1 className={s.title}>Welcome back</h1>
        <p className={s.subtitle}>Sign in to your Finance OS</p>

        {error && <div className={s.error}>⚠ {error}</div>}

        <form onSubmit={handleSubmit} className={s.form}>
          <div className={s.field}>
            <label>Email address</label>
            <input type="email" placeholder="you@company.com" value={form.email}
              onChange={e => setForm(f => ({ ...f, email: e.target.value }))} required autoFocus />
          </div>
          <div className={s.field}>
            <label>Password</label>
            <input type="password" placeholder="••••••••" value={form.password}
              onChange={e => setForm(f => ({ ...f, password: e.target.value }))} required />
          </div>
          <button type="submit" className={s.btn} disabled={loading}>
            {loading ? <span className={s.spinner} /> : 'Sign In →'}
          </button>
        </form>

        <p className={s.footer}>No account? <Link to="/register">Create one</Link></p>

     
      </div>
    </div>
  )
}
