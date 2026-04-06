import { useEffect, useState, useCallback, useRef } from 'react'
import api from '../api'
import { useAuth } from '../context/AuthContext'
import s from './Transactions.module.css'

const fmt = n => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n ?? 0)
const EMPTY = { amount: '', type: 'EXPENSE', categoryId: '', date: new Date().toISOString().split('T')[0], notes: '' }
const CATS = [
  { id: 1, name: 'Food' }, { id: 2, name: 'Transport' }, { id: 3, name: 'Salary' },
  { id: 4, name: 'Entertainment' }, { id: 5, name: 'Utilities' }, { id: 6, name: 'Healthcare' }, { id: 7, name: 'Other' }
]

function Spinner({ size = 20 }) {
  return <div className={s.spinner} style={{ width: size, height: size }} />
}

function Badge({ type }) {
  return <span className={`${s.badge} ${type === 'INCOME' ? s.badgeIncome : s.badgeExpense}`}>{type}</span>
}

export default function Transactions() {
  const { isAnalyst, isAdmin } = useAuth()
  const [txs, setTxs]             = useState([])
  const [loading, setLoading]     = useState(true)
  const [page, setPage]           = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [total, setTotal]         = useState(0)
  const [filters, setFilters]     = useState({ type: '', categoryId: '', from: '', to: '', search: '' })
  const [showDeleted, setShowDeleted] = useState(false)
  const [modal, setModal]         = useState(null)
  const [editTx, setEditTx]       = useState(null)
  const [form, setForm]           = useState(EMPTY)
  const [saving, setSaving]       = useState(false)
  const [anomaly, setAnomaly]     = useState(null)
  const [velocity, setVelocity]   = useState(null)
  const [error, setError]         = useState('')
  const [historyTx, setHistoryTx] = useState(null)
  const [history, setHistory]     = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [selected, setSelected]   = useState(new Set())
  const searchRef = useRef(null)

  const fetchTxs = useCallback(async () => {
    setLoading(true)
    try {
      const endpoint = showDeleted ? '/transactions/deleted' : '/transactions'
      const params = { page, size: 15, ...Object.fromEntries(Object.entries(filters).filter(([k, v]) => v && k !== 'search')) }
      const { data } = await api.get(endpoint, { params })
      let content = data.content || []
      // Client-side search filter (notes + category)
      if (filters.search) {
        const q = filters.search.toLowerCase()
        content = content.filter(t =>
          t.notes?.toLowerCase().includes(q) ||
          t.categoryName?.toLowerCase().includes(q) ||
          t.createdBy?.toLowerCase().includes(q) ||
          t.merchantName?.toLowerCase().includes(q)
        )
      }
      setTxs(content)
      setTotalPages(data.totalPages || 0)
      setTotal(data.totalElements || 0)
    } catch { setTxs([]) }
    finally { setLoading(false) }
  }, [page, filters, showDeleted])

  useEffect(() => { fetchTxs() }, [fetchTxs])
  useEffect(() => { setPage(0) }, [filters, showDeleted])

  const openCreate = () => { setForm(EMPTY); setEditTx(null); setModal('form'); setError(''); setAnomaly(null); setVelocity(null) }
  const openEdit   = tx => { setForm({ amount: tx.amount, type: tx.type, categoryId: tx.categoryId || '', date: tx.date, notes: tx.notes || '' }); setEditTx(tx); setModal('form'); setError('') }

  const openHistory = async tx => {
    setHistoryTx(tx); setModal('history'); setHistoryLoading(true)
    try {
      const { data } = await api.get(`/transactions/${tx.id}/history`)
      setHistory(data.history || [])
    } catch { setHistory([]) }
    finally { setHistoryLoading(false) }
  }

  const handleSave = async e => {
    e.preventDefault()
    if (!form.amount || parseFloat(form.amount) <= 0) { setError('Amount must be greater than 0'); return }
    setSaving(true); setAnomaly(null); setVelocity(null); setError('')
    try {
      const payload = { ...form, amount: parseFloat(form.amount), categoryId: form.categoryId ? parseInt(form.categoryId) : null }
      if (modal === 'form' && !editTx) {
        const res = await api.post('/transactions', payload)
        if (res.headers['x-anomaly-warning'] === 'true') setAnomaly(res.headers['x-anomaly-detail'] || 'Unusual spend detected')
        const vs = res.headers['x-velocity-score']
        if (vs) setVelocity(parseFloat(vs).toFixed(1))
        if (!anomaly) { setModal(null); fetchTxs() }
      } else {
        await api.put(`/transactions/${editTx.id}`, payload)
        setModal(null); fetchTxs()
      }
    } catch (err) {
      setError(err.response?.data?.message || err.response?.data?.fieldErrors ? JSON.stringify(err.response.data.fieldErrors) : 'Failed to save')
    } finally { setSaving(false) }
  }

  const handleDelete = async id => {
    if (!window.confirm('Soft-delete this transaction? It can be restored later.')) return
    try { await api.delete(`/transactions/${id}`); fetchTxs() } catch {}
  }

  const handleRestore = async id => {
    try { await api.post(`/transactions/${id}/restore`); fetchTxs() } catch {}
  }

  const handleExport = async format => {
    try {
      const res = await api.get('/transactions/export', { params: { format }, responseType: 'blob' })
      const url = URL.createObjectURL(res.data)
      const a = document.createElement('a')
      a.href = url; a.download = `fintech-transactions.${format === 'excel' ? 'xlsx' : 'csv'}`
      a.click(); URL.revokeObjectURL(url)
    } catch {}
  }

  const toggleSelect = id => setSelected(prev => { const n = new Set(prev); n.has(id) ? n.delete(id) : n.add(id); return n })
  const selectAll    = () => setSelected(txs.length === selected.size ? new Set() : new Set(txs.map(t => t.id)))

  const incomeTotal   = txs.filter(t => t.type === 'INCOME').reduce((a, t) => a + parseFloat(t.amount), 0)
  const expenseTotal  = txs.filter(t => t.type === 'EXPENSE').reduce((a, t) => a + parseFloat(t.amount), 0)

  return (
    <div className={s.page}>
      {/* Header */}
      <div className={s.pageHeader}>
        <div>
          <h1 className={s.pageTitle}>{showDeleted ? 'Recycle Bin' : 'Transactions'}</h1>
          <p className={s.pageSub}>{showDeleted ? 'Soft-deleted records — restore anytime' : `${total} records`}</p>
        </div>
        <div className={s.headerActions}>
          <button className={s.btnGhost} onClick={() => handleExport('csv')}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            CSV
          </button>
          <button className={s.btnGhost} onClick={() => handleExport('excel')}>
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
            Excel
          </button>
          {isAdmin && (
            <button className={`${s.btnGhost} ${showDeleted ? s.btnGhostActive : ''}`} onClick={() => setShowDeleted(d => !d)}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/></svg>
              {showDeleted ? 'Active' : 'Deleted'}
            </button>
          )}
          {isAnalyst && !showDeleted && (
            <button className={s.btnPrimary} onClick={openCreate}>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
              New Transaction
            </button>
          )}
        </div>
      </div>

      {/* Summary strip */}
      {!showDeleted && !loading && txs.length > 0 && (
        <div className={s.summaryStrip}>
          <div className={s.summaryItem}>
            <span className={s.summaryLabel}>Page Income</span>
            <span className={s.summaryVal} style={{ color: '#10b981' }}>+{fmt(incomeTotal)}</span>
          </div>
          <div className={s.summaryDivider} />
          <div className={s.summaryItem}>
            <span className={s.summaryLabel}>Page Expenses</span>
            <span className={s.summaryVal} style={{ color: '#f43f5e' }}>-{fmt(expenseTotal)}</span>
          </div>
          <div className={s.summaryDivider} />
          <div className={s.summaryItem}>
            <span className={s.summaryLabel}>Net</span>
            <span className={s.summaryVal} style={{ color: incomeTotal - expenseTotal >= 0 ? '#10b981' : '#f43f5e' }}>
              {fmt(incomeTotal - expenseTotal)}
            </span>
          </div>
        </div>
      )}

      {/* Filters */}
      {!showDeleted && (
        <div className={s.filterBar}>
          <div className={s.searchWrap}>
            <svg className={s.searchIcon} width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input ref={searchRef} className={s.searchInput} placeholder="Search notes, category, merchant…"
              value={filters.search} onChange={e => setFilters(f => ({ ...f, search: e.target.value }))} />
            {filters.search && <button className={s.searchClear} onClick={() => setFilters(f => ({ ...f, search: '' }))}>✕</button>}
          </div>
          <select className={s.filterSelect} value={filters.type} onChange={e => setFilters(f => ({ ...f, type: e.target.value }))}>
            <option value="">All Types</option>
            <option value="INCOME">Income</option>
            <option value="EXPENSE">Expense</option>
          </select>
          <select className={s.filterSelect} value={filters.categoryId} onChange={e => setFilters(f => ({ ...f, categoryId: e.target.value }))}>
            <option value="">All Categories</option>
            {CATS.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <input className={s.filterInput} type="date" value={filters.from} onChange={e => setFilters(f => ({ ...f, from: e.target.value }))} />
          <input className={s.filterInput} type="date" value={filters.to} onChange={e => setFilters(f => ({ ...f, to: e.target.value }))} />
          {(filters.type || filters.categoryId || filters.from || filters.to || filters.search) && (
            <button className={s.clearBtn} onClick={() => setFilters({ type: '', categoryId: '', from: '', to: '', search: '' })}>Clear</button>
          )}
        </div>
      )}

      {/* Table */}
      <div className={s.tableCard}>
        {loading ? (
          <div className={s.loadingWrap}><Spinner size={28} /></div>
        ) : txs.length === 0 ? (
          <div className={s.emptyState}>
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--z-text-4)" strokeWidth="1.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
            <p>{showDeleted ? 'No deleted transactions' : 'No transactions found'}</p>
            {isAnalyst && !showDeleted && <button className={s.btnPrimary} onClick={openCreate}>Create your first transaction</button>}
          </div>
        ) : (
          <table className={s.table}>
            <thead>
              <tr>
                {(isAnalyst || isAdmin) && <th><input type="checkbox" checked={selected.size === txs.length && txs.length > 0} onChange={selectAll} className={s.checkbox} /></th>}
                <th>Date</th>
                <th>Type</th>
                <th>Category</th>
                <th>Amount</th>
                <th>Merchant</th>
                <th>Notes</th>
                <th>By</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {txs.map(tx => (
                <tr key={tx.id} className={selected.has(tx.id) ? s.rowSelected : ''}>
                  {(isAnalyst || isAdmin) && (
                    <td><input type="checkbox" checked={selected.has(tx.id)} onChange={() => toggleSelect(tx.id)} className={s.checkbox} /></td>
                  )}
                  <td className={s.tdDate}>{tx.date}</td>
                  <td><Badge type={tx.type} /></td>
                  <td className={s.tdCat}>{tx.categoryName || <span className={s.muted}>—</span>}</td>
                  <td className={`${s.tdAmt} ${tx.type === 'INCOME' ? s.green : s.red}`}>
                    {tx.type === 'INCOME' ? '+' : '-'}{fmt(tx.amount)}
                  </td>
                  <td>{tx.merchantName ? <span className={s.merchantChip}>{tx.merchantName}</span> : <span className={s.muted}>—</span>}</td>
                  <td className={s.tdNotes} title={tx.notes}>{tx.notes || <span className={s.muted}>—</span>}</td>
                  <td className={s.tdBy}>{tx.createdBy}</td>
                  <td>
                    <div className={s.actions}>
                      <button className={s.actionBtn} onClick={() => openHistory(tx)} title="Audit history">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                      </button>
                      {showDeleted ? (
                        isAdmin && <button className={`${s.actionBtn} ${s.actionRestore}`} onClick={() => handleRestore(tx.id)} title="Restore">↩</button>
                      ) : (
                        <>
                          {isAnalyst && <button className={`${s.actionBtn} ${s.actionEdit}`} onClick={() => openEdit(tx)} title="Edit">✎</button>}
                          {isAdmin && <button className={`${s.actionBtn} ${s.actionDelete}`} onClick={() => handleDelete(tx.id)} title="Delete">✕</button>}
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className={s.pagination}>
          <button disabled={page === 0} onClick={() => setPage(0)}>«</button>
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>‹ Prev</button>
          {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
            const p = totalPages <= 7 ? i : Math.max(0, Math.min(page - 3, totalPages - 7)) + i
            return (
              <button key={p} className={p === page ? s.pageActive : ''} onClick={() => setPage(p)}>{p + 1}</button>
            )
          })}
          <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next ›</button>
          <button disabled={page >= totalPages - 1} onClick={() => setPage(totalPages - 1)}>»</button>
        </div>
      )}

      {/* ── Transaction Form Modal ── */}
      {modal === 'form' && (
        <div className={s.overlay} onClick={e => e.target === e.currentTarget && setModal(null)}>
          <div className={s.modal}>
            <div className={s.modalHeader}>
              <div>
                <h2 className={s.modalTitle}>{editTx ? 'Edit Transaction' : 'New Transaction'}</h2>
                <p className={s.modalSub}>{editTx ? `Editing #${editTx.id}` : 'All fields marked * are required'}</p>
              </div>
              <button className={s.closeBtn} onClick={() => setModal(null)}>✕</button>
            </div>

            {anomaly && (
              <div className={s.anomalyBanner}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                <div>
                  <strong>Anomaly Detected</strong>
                  <p>{anomaly}</p>
                </div>
                <button onClick={() => { setAnomaly(null); setModal(null); fetchTxs() }}>Dismiss ✕</button>
              </div>
            )}

            {velocity && !anomaly && (
              <div className={s.velocityBanner}>
                <span>Velocity Score: <strong>{velocity}</strong></span>
                <span className={s.velocityBar}><span style={{ width: `${velocity}%`, background: parseFloat(velocity) > 75 ? '#f43f5e' : '#6366f1' }} /></span>
              </div>
            )}

            {error && <div className={s.errorBanner}>{error}</div>}

            <form onSubmit={handleSave} className={s.modalForm}>
              <div className={s.row2}>
                <div className={s.field}>
                  <label>Amount *</label>
                  <div className={s.inputWrap}>
                    <span className={s.inputPrefix}>₹</span>
                    <input type="number" step="0.01" min="0.01" required value={form.amount}
                      onChange={e => setForm(f => ({ ...f, amount: e.target.value }))} placeholder="0.00" className={s.inputPrefixed} />
                  </div>
                </div>
                <div className={s.field}>
                  <label>Type *</label>
                  <div className={s.typeToggle}>
                    {['EXPENSE','INCOME'].map(t => (
                      <button key={t} type="button"
                        className={`${s.typeBtn} ${form.type === t ? (t === 'INCOME' ? s.typeBtnIncome : s.typeBtnExpense) : ''}`}
                        onClick={() => setForm(f => ({ ...f, type: t }))}>
                        {t === 'INCOME' ? '↑ Income' : '↓ Expense'}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              <div className={s.row2}>
                <div className={s.field}>
                  <label>Date *</label>
                  <input type="date" required value={form.date} onChange={e => setForm(f => ({ ...f, date: e.target.value }))} />
                </div>
                <div className={s.field}>
                  <label>Category</label>
                  <select value={form.categoryId} onChange={e => setForm(f => ({ ...f, categoryId: e.target.value }))}>
                    <option value="">Select category</option>
                    {CATS.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </div>
              </div>
              <div className={s.field}>
                <label>Notes <span className={s.optional}>(merchant keywords auto-detected)</span></label>
                <textarea rows={3} value={form.notes} onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
                  placeholder="e.g. Swiggy dinner, Netflix subscription…" />
              </div>
              <div className={s.modalActions}>
                <button type="button" className={s.btnGhost} onClick={() => setModal(null)}>Cancel</button>
                <button type="submit" className={s.btnPrimary} disabled={saving}>
                  {saving ? <Spinner size={14} /> : editTx ? 'Save Changes' : 'Create Transaction'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Audit History Drawer ── */}
      {modal === 'history' && historyTx && (
        <div className={s.overlay} onClick={e => e.target === e.currentTarget && setModal(null)}>
          <div className={s.modal}>
            <div className={s.modalHeader}>
              <div>
                <h2 className={s.modalTitle}>Audit History</h2>
                <p className={s.modalSub}>Transaction #{historyTx.id} · {historyTx.categoryName} · {fmt(historyTx.amount)}</p>
              </div>
              <button className={s.closeBtn} onClick={() => setModal(null)}>✕</button>
            </div>
            <div className={s.historyBody}>
              {historyLoading ? (
                <div className={s.loadingWrap}><Spinner /></div>
              ) : history.length === 0 ? (
                <div className={s.emptyHistory}>No audit records yet</div>
              ) : (
                <div className={s.timeline}>
                  {history.map((h, i) => (
                    <div key={i} className={s.timelineItem}>
                      <div className={s.timelineDot} />
                      <div className={s.timelineContent}>
                        <div className={s.timelineAction}>{h.action}</div>
                        <div className={s.timelineTime}>{new Date(h.timestamp).toLocaleString()}</div>
                        {h.newValue && <pre className={s.timelineVal}>{h.newValue}</pre>}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
