import { useEffect, useState, useCallback } from 'react'
import api from '../api'
import s from './Users.module.css'

const ROLES = ['VIEWER', 'ANALYST', 'ADMIN']
const ROLE_COLOR = { ADMIN: '#6366f1', ANALYST: '#22d3ee', VIEWER: '#10b981' }

export default function Users() {
  const [users, setUsers]     = useState([])
  const [loading, setLoading] = useState(true)
  const [modal, setModal]     = useState(null)
  const [form, setForm]       = useState({ fullName: '', active: true, roles: ['VIEWER'] })
  const [saving, setSaving]   = useState(false)
  const [error, setError]     = useState('')
  const [risk, setRisk]       = useState({})

  const fetchUsers = useCallback(async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/users')
      setUsers(data)
      // Fetch risk profiles in parallel
      const profiles = await Promise.allSettled(data.map(u => api.get(`/users/${u.id}/risk-profile`)))
      const map = {}
      profiles.forEach((r, i) => { if (r.status === 'fulfilled') map[data[i].id] = r.value.data })
      setRisk(map)
    } catch {}
    finally { setLoading(false) }
  }, [])

  useEffect(() => { fetchUsers() }, [fetchUsers])

  const openEdit = user => {
    setForm({ fullName: user.fullName, active: user.active, roles: [...user.roles] })
    setModal(user); setError('')
  }

  const toggleRole = role => setForm(f => ({
    ...f, roles: f.roles.includes(role) ? f.roles.filter(r => r !== role) : [...f.roles, role]
  }))

  const handleSave = async e => {
    e.preventDefault()
    if (!form.roles.length) { setError('Select at least one role'); return }
    setSaving(true); setError('')
    try {
      await api.put(`/users/${modal.id}`, form)
      setModal(null); fetchUsers()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update user')
    } finally { setSaving(false) }
  }

  const handleDeactivate = async id => {
    if (!window.confirm('Deactivate this user? They will not be able to log in.')) return
    try { await api.delete(`/users/${id}`); fetchUsers() } catch {}
  }

  const riskColor = level => level === 'HIGH' ? '#f43f5e' : level === 'MEDIUM' ? '#f59e0b' : '#10b981'

  return (
    <div className={s.page}>
      <div className={s.pageHeader}>
        <div>
          <h1 className={s.pageTitle}>User Management</h1>
          <p className={s.pageSub}>Manage roles, access levels, and risk profiles</p>
        </div>
        <div className={s.countPill}>{users.length} users</div>
      </div>

      {loading ? (
        <div className={s.loadingWrap}><div className={s.spinner} /></div>
      ) : (
        <div className={s.grid}>
          {users.map(u => {
            const r = risk[u.id]
            return (
              <div key={u.id} className={`${s.card} ${!u.active ? s.cardInactive : ''}`}>
                <div className={s.cardTop}>
                  <div className={s.avatar} style={{ background: `${ROLE_COLOR[u.roles?.[0]] || '#6366f1'}22`, color: ROLE_COLOR[u.roles?.[0]] || '#6366f1' }}>
                    {u.email[0].toUpperCase()}
                  </div>
                  <div className={s.userInfo}>
                    <div className={s.userName}>{u.fullName}</div>
                    <div className={s.userEmail}>{u.email}</div>
                  </div>
                  <div className={`${s.statusPill} ${u.active ? s.statusActive : s.statusInactive}`}>
                    {u.active ? 'Active' : 'Inactive'}
                  </div>
                </div>

                <div className={s.roles}>
                  {[...u.roles].map(role => (
                    <span key={role} className={s.roleBadge} style={{ background: `${ROLE_COLOR[role]}18`, color: ROLE_COLOR[role], borderColor: `${ROLE_COLOR[role]}30` }}>
                      {role}
                    </span>
                  ))}
                </div>

                {r && (
                  <div className={s.riskRow}>
                    <div className={s.riskItem}>
                      <span className={s.riskLabel}>Risk</span>
                      <span className={s.riskVal} style={{ color: riskColor(r.riskLevel) }}>{r.riskLevel}</span>
                    </div>
                    <div className={s.riskItem}>
                      <span className={s.riskLabel}>Velocity</span>
                      <span className={s.riskVal}>{r.velocityScore?.toFixed(0) ?? '—'}</span>
                    </div>
                    <div className={s.riskItem}>
                      <span className={s.riskLabel}>24h Spend</span>
                      <span className={s.riskVal}>₹{(r.spend24h ?? 0).toLocaleString('en-IN')}</span>
                    </div>
                    <div className={s.riskItem}>
                      <span className={s.riskLabel}>7d Spend</span>
                      <span className={s.riskVal}>₹{(r.spend7d ?? 0).toLocaleString('en-IN')}</span>
                    </div>
                  </div>
                )}

                <div className={s.cardMeta}>Joined {u.createdAt ? new Date(u.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'}</div>

                <div className={s.cardActions}>
                  <button className={s.btnEdit} onClick={() => openEdit(u)}>Edit</button>
                  {u.active && <button className={s.btnDeactivate} onClick={() => handleDeactivate(u.id)}>Deactivate</button>}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {modal && (
        <div className={s.overlay} onClick={e => e.target === e.currentTarget && setModal(null)}>
          <div className={s.modal}>
            <div className={s.modalHeader}>
              <div>
                <h2 className={s.modalTitle}>Edit User</h2>
                <p className={s.modalSub}>{modal.email}</p>
              </div>
              <button className={s.closeBtn} onClick={() => setModal(null)}>✕</button>
            </div>
            {error && <div className={s.errorBanner}>{error}</div>}
            <form onSubmit={handleSave} className={s.modalForm}>
              <div className={s.field}>
                <label>Full Name</label>
                <input value={form.fullName} onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))} required />
              </div>
              <div className={s.field}>
                <label>Status</label>
                <div className={s.toggle}>
                  {[true, false].map(v => (
                    <button key={String(v)} type="button"
                      className={`${s.toggleBtn} ${form.active === v ? (v ? s.toggleActive : s.toggleInactive) : ''}`}
                      onClick={() => setForm(f => ({ ...f, active: v }))}>
                      {v ? '● Active' : '○ Inactive'}
                    </button>
                  ))}
                </div>
              </div>
              <div className={s.field}>
                <label>Roles</label>
                <div className={s.roleToggles}>
                  {ROLES.map(r => (
                    <button key={r} type="button"
                      className={`${s.roleToggle} ${form.roles.includes(r) ? s.roleSelected : ''}`}
                      style={form.roles.includes(r) ? { background: `${ROLE_COLOR[r]}18`, color: ROLE_COLOR[r], borderColor: `${ROLE_COLOR[r]}40` } : {}}
                      onClick={() => toggleRole(r)}>{r}
                    </button>
                  ))}
                </div>
              </div>
              <div className={s.modalActions}>
                <button type="button" className={s.btnGhost} onClick={() => setModal(null)}>Cancel</button>
                <button type="submit" className={s.btnPrimary} disabled={saving}>
                  {saving ? '…' : 'Save Changes'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
