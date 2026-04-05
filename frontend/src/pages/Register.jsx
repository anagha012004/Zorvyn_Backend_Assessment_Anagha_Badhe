import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import api from '../api'
import s from './Auth.module.css'

export default function Register() {
  const [form, setForm] = useState({ email: '', password: '', fullName: '' })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async e => {
    e.preventDefault()
    if (form.password.length < 8) { setError('Password must be at least 8 characters'); return }
    setError(''); setLoading(true)
    try {
      await api.post('/auth/register', form)
      await login(form.email, form.password)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed. Email may already be in use.')
    } finally { setLoading(false) }
  }

  return (
    <div className={s.page}>
      <div className={s.card}>
        <div className={s.brand}>
          <div className={s.brandMark}>
            <svg width="22" height="22" viewBox="0 0 32 32" fill="none">
              <path d="M4 8h24L16 28 4 8z" fill="url(#lg2)" opacity="0.9"/>
              <defs><linearGradient id="lg2" x1="0" y1="0" x2="32" y2="32" gradientUnits="userSpaceOnUse"><stop stopColor="#6366f1"/><stop offset="1" stopColor="#22d3ee"/></linearGradient></defs>
            </svg>
          </div>
          <span className={s.brandName}>Zorvyn</span>
        </div>

        <h1 className={s.title}>Create account</h1>
        <p className={s.subtitle}>Join the Zorvyn Finance OS</p>

        {error && <div className={s.error}>⚠ {error}</div>}

        <form onSubmit={handleSubmit} className={s.form}>
          <div className={s.field}>
            <label>Full Name</label>
            <input type="text" placeholder="Jane Doe" value={form.fullName}
              onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))} required autoFocus />
          </div>
          <div className={s.field}>
            <label>Email address</label>
            <input type="email" placeholder="you@company.com" value={form.email}
              onChange={e => setForm(f => ({ ...f, email: e.target.value }))} required />
          </div>
          <div className={s.field}>
            <label>Password</label>
            <input type="password" placeholder="Min. 8 characters" value={form.password}
              onChange={e => setForm(f => ({ ...f, password: e.target.value }))} required minLength={8} />
          </div>
          <button type="submit" className={s.btn} disabled={loading}>
            {loading ? <span className={s.spinner} /> : 'Create Account →'}
          </button>
        </form>

        <p className={s.footer}>Already have an account? <Link to="/login">Sign in</Link></p>

        <div className={s.hint}>
          New accounts receive <strong>Viewer</strong> role by default.<br />
          Contact an admin to upgrade access.
        </div>
      </div>
    </div>
  )
}
