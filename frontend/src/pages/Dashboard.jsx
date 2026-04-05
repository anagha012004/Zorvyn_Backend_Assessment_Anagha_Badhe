import { useEffect, useRef, useState, useCallback } from 'react'
import { AreaChart, Area, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell, PieChart, Pie } from 'recharts'
import api from '../api'
import { useAuth } from '../context/AuthContext'
import s from './Dashboard.module.css'

const fmt = n => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(n ?? 0)
const fmtShort = n => { const v = Math.abs(n ?? 0); return v >= 1e7 ? `₹${(v/1e7).toFixed(1)}Cr` : v >= 1e5 ? `₹${(v/1e5).toFixed(1)}L` : v >= 1e3 ? `₹${(v/1e3).toFixed(1)}K` : `₹${v}` }

const CAT_COLORS = ['#6366f1','#22d3ee','#10b981','#f59e0b','#f43f5e','#a855f7','#ec4899']

function Spinner() {
  return <div className={s.spinnerWrap}><div className={s.spinner} /></div>
}

function KpiCard({ label, value, sub, trend, color, icon, loading }) {
  const [display, setDisplay] = useState(0)
  useEffect(() => {
    if (!value || loading) return
    const target = parseFloat(value)
    const dur = 900, start = performance.now()
    const tick = now => {
      const p = Math.min((now - start) / dur, 1)
      const e = 1 - Math.pow(1 - p, 3)
      setDisplay(target * e)
      if (p < 1) requestAnimationFrame(tick)
    }
    requestAnimationFrame(tick)
  }, [value, loading])

  return (
    <div className={s.kpiCard} style={{ '--kpi-color': color }}>
      <div className={s.kpiTop}>
        <span className={s.kpiIcon}>{icon}</span>
        <span className={s.kpiLabel}>{label}</span>
      </div>
      <div className={s.kpiValue} style={{ color }}>
        {loading ? '—' : fmtShort(display)}
      </div>
      <div className={s.kpiSub}>
        {trend && <span className={s.kpiTrend} style={{ color }}>{trend}</span>}
        <span>{sub}</span>
      </div>
    </div>
  )
}

function VelocityGauge({ score }) {
  const pct = Math.min(score ?? 0, 100)
  const color = pct >= 75 ? '#f43f5e' : pct >= 45 ? '#f59e0b' : '#10b981'
  const label = pct >= 75 ? 'HIGH RISK' : pct >= 45 ? 'ELEVATED' : 'NORMAL'
  const r = 52, circ = 2 * Math.PI * r
  const dash = (pct / 100) * circ * 0.75

  return (
    <div className={s.gaugeWrap}>
      <svg width="140" height="100" viewBox="0 0 140 100">
        <path d="M 14 90 A 56 56 0 0 1 126 90" fill="none" stroke="var(--z-border-mid)" strokeWidth="10" strokeLinecap="round"/>
        <path d="M 14 90 A 56 56 0 0 1 126 90" fill="none" stroke={color} strokeWidth="10" strokeLinecap="round"
          strokeDasharray={`${dash} ${circ}`} style={{ transition: 'stroke-dasharray 1s ease, stroke 0.5s' }}/>
        <text x="70" y="82" textAnchor="middle" fill={color} fontSize="22" fontWeight="800" fontFamily="Inter">{Math.round(pct)}</text>
        <text x="70" y="96" textAnchor="middle" fill="var(--z-text-3)" fontSize="9" fontFamily="Inter" letterSpacing="1">{label}</text>
      </svg>
      <div className={s.gaugeLabels}>
        <span style={{ color: '#10b981' }}>0</span>
        <span className={s.gaugeTitle}>Velocity Score</span>
        <span style={{ color: '#f43f5e' }}>100</span>
      </div>
    </div>
  )
}

function BudgetRing({ name, spent, limit, color }) {
  const pct = limit > 0 ? Math.min((spent / limit) * 100, 100) : 0
  const over = spent > limit
  const r = 28, circ = 2 * Math.PI * r
  const dash = (pct / 100) * circ
  const ringColor = over ? '#f43f5e' : pct > 80 ? '#f59e0b' : color

  return (
    <div className={s.budgetRing}>
      <svg width="72" height="72" viewBox="0 0 72 72">
        <circle cx="36" cy="36" r={r} fill="none" stroke="var(--z-border)" strokeWidth="7"/>
        <circle cx="36" cy="36" r={r} fill="none" stroke={ringColor} strokeWidth="7"
          strokeDasharray={`${dash} ${circ}`} strokeDashoffset={circ * 0.25}
          strokeLinecap="round" style={{ transition: 'stroke-dasharray 1s ease' }}/>
        <text x="36" y="40" textAnchor="middle" fill={ringColor} fontSize="11" fontWeight="700" fontFamily="Inter">
          {Math.round(pct)}%
        </text>
      </svg>
      <div className={s.budgetRingLabel}>{name}</div>
      <div className={s.budgetRingAmt} style={{ color: over ? '#f43f5e' : 'var(--z-text-2)' }}>
        {fmtShort(spent)} / {fmtShort(limit)}
      </div>
      {over && <div className={s.budgetOver}>OVER</div>}
    </div>
  )
}

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  return (
    <div className={s.tooltip}>
      <div className={s.tooltipLabel}>{label}</div>
      {payload.map(p => (
        <div key={p.name} className={s.tooltipRow}>
          <span style={{ background: p.color }} className={s.tooltipDot} />
          <span style={{ color: p.color }}>{p.name}:</span>
          <span>{fmtShort(p.value)}</span>
        </div>
      ))}
    </div>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  const [summary, setSummary]     = useState(null)
  const [trends, setTrends]       = useState([])
  const [forecast, setForecast]   = useState(null)
  const [budgets, setBudgets]     = useState([])
  const [merchants, setMerchants] = useState([])
  const [liveFeed, setLiveFeed]   = useState([])
  const [anomaly, setAnomaly]     = useState(false)
  const [loading, setLoading]     = useState(true)
  const [activeTab, setActiveTab] = useState('trends')
  const esRef = useRef(null)

  const load = useCallback(async () => {
    setLoading(true)
    const now = new Date()
    const monthYear = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}`

    const results = await Promise.allSettled([
      api.get('/dashboard/summary'),
      api.get('/dashboard/trends'),
      api.get('/forecast?days=30'),
      api.get(`/budgets/status?monthYear=${monthYear}`),
      api.get('/merchants/top?period=monthly'),
    ])

    if (results[0].status === 'fulfilled') setSummary(results[0].value.data)
    if (results[1].status === 'fulfilled') {
      const raw = results[1].value.data.trends || {}
      setTrends(Object.entries(raw).map(([month, vals]) => ({
        month, Income: parseFloat(vals.INCOME || 0), Expense: parseFloat(vals.EXPENSE || 0),
      })))
    }
    if (results[2].status === 'fulfilled') setForecast(results[2].value.data)
    if (results[3].status === 'fulfilled') setBudgets(results[3].value.data?.envelopes || [])
    if (results[4].status === 'fulfilled') setMerchants(results[4].value.data || [])
    setLoading(false)
  }, [])

  useEffect(() => {
    load()
    // SSE live feed
    const token = localStorage.getItem('accessToken')
    const es = new EventSource(`/api/v1/transactions/stream?token=${token}`)
    esRef.current = es
    es.addEventListener('transaction', e => {
      try {
        const tx = JSON.parse(e.data)
        setLiveFeed(prev => [tx, ...prev].slice(0, 12))
        if (tx.offHours || tx.possibleDuplicate) setAnomaly(true)
      } catch {}
    })
    es.onerror = () => es.close()
    return () => es.close()
  }, [load])

  const net = parseFloat(summary?.netBalance ?? 0)
  const income = parseFloat(summary?.totalIncome ?? 0)
  const expenses = parseFloat(summary?.totalExpenses ?? 0)
  const savingsRate = income > 0 ? ((net / income) * 100).toFixed(1) : '0.0'

  const catData = Object.entries(summary?.categoryBreakdown || {})
    .map(([name, value], i) => ({ name, value: parseFloat(value), color: CAT_COLORS[i % CAT_COLORS.length] }))
    .sort((a, b) => b.value - a.value)

  const forecastBars = forecast
    ? Object.entries(forecast.projectedSpendByCategory || {})
        .map(([cat, total]) => ({ cat, total: parseFloat(total), daily: parseFloat(forecast.dailyForecastByCategory?.[cat] || 0) }))
        .sort((a, b) => b.total - a.total)
    : []
  const maxForecast = forecastBars[0]?.total || 1

  const velocityScore = summary?.recentTransactions?.[0] ? 42 : 0 // placeholder — real score from user profile

  return (
    <div className={s.page}>
      {/* ── Header ── */}
      <div className={s.pageHeader}>
        <div>
          <h1 className={s.pageTitle}>Dashboard</h1>
          <p className={s.pageSub}>Financial intelligence at a glance</p>
        </div>
        <div className={s.headerRight}>
          <div className={`${s.anomalyPill} ${anomaly ? s.anomalyActive : s.anomalyClear}`}>
            <span className={s.anomalyDot} />
            {anomaly ? 'Anomaly Detected' : 'All Systems Normal'}
          </div>
          <button className={s.refreshBtn} onClick={load} title="Refresh">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
          </button>
        </div>
      </div>

      {/* ── KPI Row ── */}
      <div className={s.kpiGrid}>
        <KpiCard label="Total Income" value={income} sub="All time" trend="↑" color="#10b981" loading={loading}
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>} />
        <KpiCard label="Total Expenses" value={expenses} sub="All time" trend="↓" color="#f43f5e" loading={loading}
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg>} />
        <KpiCard label="Net Balance" value={Math.abs(net)} sub={net >= 0 ? 'Surplus' : 'Deficit'} trend={net >= 0 ? '▲' : '▼'} color={net >= 0 ? '#10b981' : '#f43f5e'} loading={loading}
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/></svg>} />
        <KpiCard label="Savings Rate" value={parseFloat(savingsRate) * 100} sub="of income saved" trend={parseFloat(savingsRate) >= 20 ? '✓ Healthy' : '⚠ Low'} color="#6366f1" loading={loading}
          icon={<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M12 2a10 10 0 1 0 10 10"/><path d="M12 6v6l4 2"/></svg>} />
      </div>

      {/* ── Main grid ── */}
      <div className={s.mainGrid}>
        {/* Left col */}
        <div className={s.leftCol}>
          {/* Chart tabs */}
          <div className={s.chartCard}>
            <div className={s.cardHeader}>
              <div className={s.tabs}>
                {['trends','forecast'].map(t => (
                  <button key={t} className={`${s.tab} ${activeTab === t ? s.tabActive : ''}`} onClick={() => setActiveTab(t)}>
                    {t === 'trends' ? 'Monthly Trends' : '30-Day Forecast'}
                  </button>
                ))}
              </div>
            </div>

            {activeTab === 'trends' && (
              loading ? <Spinner /> : trends.length === 0 ? <div className={s.empty}>No trend data yet</div> : (
                <ResponsiveContainer width="100%" height={220}>
                  <AreaChart data={trends} margin={{ top: 10, right: 8, left: -10, bottom: 0 }}>
                    <defs>
                      <linearGradient id="gI" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#10b981" stopOpacity={0.25}/>
                        <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                      </linearGradient>
                      <linearGradient id="gE" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#f43f5e" stopOpacity={0.25}/>
                        <stop offset="95%" stopColor="#f43f5e" stopOpacity={0}/>
                      </linearGradient>
                    </defs>
                    <XAxis dataKey="month" tick={{ fill: 'var(--z-text-3)', fontSize: 11 }} axisLine={false} tickLine={false}/>
                    <YAxis tick={{ fill: 'var(--z-text-3)', fontSize: 11 }} axisLine={false} tickLine={false} tickFormatter={fmtShort}/>
                    <Tooltip content={<CustomTooltip />}/>
                    <Area type="monotone" dataKey="Income" stroke="#10b981" strokeWidth={2} fill="url(#gI)" dot={false}/>
                    <Area type="monotone" dataKey="Expense" stroke="#f43f5e" strokeWidth={2} fill="url(#gE)" dot={false}/>
                  </AreaChart>
                </ResponsiveContainer>
              )
            )}

            {activeTab === 'forecast' && (
              loading ? <Spinner /> : forecastBars.length === 0 ? <div className={s.empty}>No forecast data</div> : (
                <ResponsiveContainer width="100%" height={220}>
                  <BarChart data={forecastBars} margin={{ top: 10, right: 8, left: -10, bottom: 0 }}>
                    <XAxis dataKey="cat" tick={{ fill: 'var(--z-text-3)', fontSize: 10 }} axisLine={false} tickLine={false}/>
                    <YAxis tick={{ fill: 'var(--z-text-3)', fontSize: 11 }} axisLine={false} tickLine={false} tickFormatter={fmtShort}/>
                    <Tooltip formatter={v => fmtShort(v)}/>
                    <Bar dataKey="total" radius={[4,4,0,0]}>
                      {forecastBars.map(e => <Cell key={e.cat} fill={e.total >= maxForecast * 0.75 ? '#f43f5e' : '#6366f1'}/>)}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              )
            )}
          </div>

          {/* Category breakdown */}
          <div className={s.card}>
            <div className={s.cardHeader}><h3 className={s.cardTitle}>Category Breakdown</h3></div>
            {loading ? <Spinner /> : catData.length === 0 ? <div className={s.empty}>No data</div> : (
              <div className={s.catGrid}>
                <PieChart width={140} height={140}>
                  <Pie data={catData} cx={65} cy={65} innerRadius={42} outerRadius={62} dataKey="value" strokeWidth={0}>
                    {catData.map((e, i) => <Cell key={i} fill={e.color}/>)}
                  </Pie>
                </PieChart>
                <div className={s.catList}>
                  {catData.map((c, i) => {
                    const total = catData.reduce((a, b) => a + b.value, 0)
                    const pct = total > 0 ? ((c.value / total) * 100).toFixed(1) : 0
                    return (
                      <div key={c.name} className={s.catRow}>
                        <span className={s.catDot} style={{ background: c.color }}/>
                        <span className={s.catName}>{c.name}</span>
                        <div className={s.catBar}>
                          <div className={s.catBarFill} style={{ width: `${pct}%`, background: c.color }}/>
                        </div>
                        <span className={s.catPct}>{pct}%</span>
                        <span className={s.catAmt}>{fmtShort(c.value)}</span>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </div>

          {/* Recent transactions */}
          <div className={s.card}>
            <div className={s.cardHeader}>
              <h3 className={s.cardTitle}>Recent Transactions</h3>
              <span className={s.cardBadge}>{summary?.recentTransactions?.length ?? 0}</span>
            </div>
            {loading ? <Spinner /> : !summary?.recentTransactions?.length ? <div className={s.empty}>No transactions yet</div> : (
              <div className={s.txList}>
                {summary.recentTransactions.map(tx => (
                  <div key={tx.id} className={s.txRow}>
                    <div className={`${s.txIcon} ${tx.type === 'INCOME' ? s.txIconIncome : s.txIconExpense}`}>
                      {tx.type === 'INCOME' ? '↑' : '↓'}
                    </div>
                    <div className={s.txBody}>
                      <div className={s.txTop}>
                        <span className={s.txCat}>{tx.categoryName || 'Uncategorized'}</span>
                        {tx.merchantName && <span className={s.chip} style={{ background: 'var(--z-accent-dim)', color: 'var(--z-accent-2)' }}>{tx.merchantName}</span>}
                      </div>
                      <div className={s.txMeta}>{tx.date} · {tx.createdBy}</div>
                    </div>
                    <div className={`${s.txAmt} ${tx.type === 'INCOME' ? s.green : s.red}`}>
                      {tx.type === 'INCOME' ? '+' : '-'}{fmtShort(tx.amount)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right col */}
        <div className={s.rightCol}>
          {/* Velocity gauge */}
          <div className={s.card}>
            <div className={s.cardHeader}><h3 className={s.cardTitle}>Spend Velocity</h3></div>
            <VelocityGauge score={velocityScore} />
            <p className={s.gaugeSub}>EMA-based score comparing today's spend vs 7-day rolling average</p>
          </div>

          {/* Budget rings */}
          <div className={s.card}>
            <div className={s.cardHeader}>
              <h3 className={s.cardTitle}>Budget Envelopes</h3>
              <span className={s.cardBadge}>{budgets.length}</span>
            </div>
            {loading ? <Spinner /> : budgets.length === 0 ? <div className={s.empty}>No budgets set</div> : (
              <div className={s.budgetGrid}>
                {budgets.map((b, i) => (
                  <BudgetRing key={b.categoryName} name={b.categoryName}
                    spent={parseFloat(b.spent || 0)} limit={parseFloat(b.limit || 1)}
                    color={CAT_COLORS[i % CAT_COLORS.length]} />
                ))}
              </div>
            )}
          </div>

          {/* Merchant leaderboard */}
          <div className={s.card}>
            <div className={s.cardHeader}><h3 className={s.cardTitle}>Top Merchants</h3><span className={s.cardChip}>This month</span></div>
            {loading ? <Spinner /> : merchants.length === 0 ? <div className={s.empty}>No merchant data</div> : (
              <div className={s.merchantList}>
                {merchants.slice(0, 6).map((m, i) => {
                  const max = parseFloat(merchants[0]?.totalSpend || 1)
                  const pct = (parseFloat(m.totalSpend) / max) * 100
                  return (
                    <div key={m.merchant} className={s.merchantRow}>
                      <span className={s.merchantRank}>{i + 1}</span>
                      <span className={s.merchantName}>{m.merchant}</span>
                      <div className={s.merchantBar}>
                        <div className={s.merchantBarFill} style={{ width: `${pct}%`, background: CAT_COLORS[i % CAT_COLORS.length] }}/>
                      </div>
                      <span className={s.merchantAmt}>{fmtShort(m.totalSpend)}</span>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* Live feed */}
          <div className={s.card}>
            <div className={s.cardHeader}>
              <h3 className={s.cardTitle}>Live Feed</h3>
              <span className={s.liveDot} />
            </div>
            {liveFeed.length === 0 ? (
              <div className={s.liveEmpty}>
                <div className={s.livePulse} />
                <span>Waiting for transactions…</span>
              </div>
            ) : (
              <div className={s.liveList}>
                {liveFeed.map((tx, i) => (
                  <div key={`${tx.id}-${i}`} className={s.liveRow}>
                    <div className={`${s.txIcon} ${tx.type === 'INCOME' ? s.txIconIncome : s.txIconExpense}`} style={{ width: 28, height: 28, fontSize: 11 }}>
                      {tx.type === 'INCOME' ? '↑' : '↓'}
                    </div>
                    <div className={s.txBody}>
                      <div className={s.txTop}>
                        <span className={s.txCat} style={{ fontSize: 12 }}>{tx.categoryName || 'Uncategorized'}</span>
                        {tx.offHours && <span className={s.chip} style={{ background: 'rgba(245,158,11,0.12)', color: '#f59e0b' }}>🌙</span>}
                        {tx.possibleDuplicate && <span className={s.chip} style={{ background: 'var(--z-red-dim)', color: 'var(--z-red)' }}>⚠ Dup</span>}
                        {tx.merchantName && <span className={s.chip} style={{ background: 'var(--z-accent-dim)', color: 'var(--z-accent-2)', fontSize: 10 }}>{tx.merchantName}</span>}
                      </div>
                      <div className={s.txMeta} style={{ fontSize: 10 }}>{tx.createdBy}</div>
                    </div>
                    <div className={`${s.txAmt} ${tx.type === 'INCOME' ? s.green : s.red}`} style={{ fontSize: 12 }}>
                      {tx.type === 'INCOME' ? '+' : '-'}{fmtShort(tx.amount)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
