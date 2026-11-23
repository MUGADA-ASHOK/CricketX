import React, { useState, useEffect } from 'react';
import { 
  Shield, Calendar, MapPin, Sliders, CheckCircle2, 
  AlertCircle, Plus, Trash2, RefreshCw, X, TrendingUp, DollarSign, Lock
} from 'lucide-react';
import { eventApi } from '../../api/eventApi';
import { useAuth } from '../../context/AuthContext';
import { Navbar } from '../../components/common/Navbar';

export const FranchiseDashboard = () => {
  const { user } = useAuth();
  const [matches, setMatches] = useState([]);
  const [stadiums, setStadiums] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Configure Modal State
  const [configMatch, setConfigMatch] = useState(null);
  const [selectedStadiumId, setSelectedStadiumId] = useState('');
  const [categories, setCategories] = useState([
    { categoryName: 'VIP', capacity: 500, price: 4500 },
    { categoryName: 'PAVILION', capacity: 2000, price: 2500 },
    { categoryName: 'GENERAL', capacity: 10000, price: 800 },
  ]);
  const [submittingConfig, setSubmittingConfig] = useState(false);
  const [configError, setConfigError] = useState('');
  const [configSuccess, setConfigSuccess] = useState('');

  // Profile Edit State
  const [franchises, setFranchises] = useState([]);
  const [myFranchise, setMyFranchise] = useState(null);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [editPhone, setEditPhone] = useState('');
  const [editLogoUrl, setEditLogoUrl] = useState('');
  const [editCity, setEditCity] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editShortName, setEditShortName] = useState('');
  const [editStadiumId, setEditStadiumId] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      setError('');
      const [matchesRes, stadiumsRes, franchisesRes] = await Promise.all([
        eventApi.getMatches(),
        eventApi.getStadiums().catch(() => ({ data: [] })),
        eventApi.getFranchises().catch(() => ({ data: [] })),
      ]);

      const matchList = matchesRes?.data || matchesRes || [];
      const stadiumList = stadiumsRes?.data || stadiumsRes || [];
      const franchiseList = franchisesRes?.data || franchisesRes || [];

      setMatches(Array.isArray(matchList) ? matchList : []);
      setStadiums(Array.isArray(stadiumList) ? stadiumList : []);
      setFranchises(Array.isArray(franchiseList) ? franchiseList : []);

      const found = franchiseList.find((f) => String(f.userId) === String(user?.id)) || null;
      setMyFranchise(found || (user?.role === 'ADMIN' ? franchiseList[0] : null));
    } catch (err) {
      setError('Failed to load franchise fixtures: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const openProfileModal = () => {
    if (!myFranchise) return;
    setEditPhone(myFranchise.contactPhone || '');
    setEditLogoUrl(myFranchise.logoUrl || '');
    setEditCity(myFranchise.city || '');
    setEditDesc(myFranchise.description || '');
    setEditShortName(myFranchise.shortName || myFranchise.teamCode || '');
    setEditStadiumId(myFranchise.homeStadiumId ? String(myFranchise.homeStadiumId) : (stadiums[0]?.id ? String(stadiums[0].id) : '1'));
    setShowProfileModal(true);
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    if (!myFranchise) return;
    try {
      setSavingProfile(true);
      await eventApi.updateFranchise(myFranchise.id, {
        shortName: editShortName,
        city: editCity,
        description: editDesc,
        logoUrl: editLogoUrl,
        contactPhone: editPhone,
        homeStadiumId: editStadiumId ? Number(editStadiumId) : undefined,
      });
      setShowProfileModal(false);
      loadData();
    } catch (err) {
      alert('Failed to update team profile: ' + err.message);
    } finally {
      setSavingProfile(false);
    }
  };

  const openConfigModal = (match) => {
    const resolvedId = match.matchId || match.id || match.match_id;
    if (!resolvedId) {
      alert('Unable to identify match reference. Please refresh the fixture list.');
      return;
    }
    const resolvedStadiumId = match.stadiumId || (stadiums[0]?.id ? String(stadiums[0].id) : '1');
    setConfigMatch({ 
      ...match, 
      matchId: resolvedId, 
      id: resolvedId,
      stadiumId: resolvedStadiumId 
    });
    setSelectedStadiumId(String(resolvedStadiumId));
    setCategories([
      { categoryName: 'VIP', capacity: 500, price: 5000 },
      { categoryName: 'PAVILION', capacity: 2000, price: 2500 },
      { categoryName: 'GENERAL', capacity: 8000, price: 800 },
    ]);
    setConfigError('');
    setConfigSuccess('');
  };

  const handleCategoryChange = (index, field, value) => {
    const updated = [...categories];
    updated[index] = {
      ...updated[index],
      [field]: field === 'categoryName' ? value : Number(value) || 0,
    };
    setCategories(updated);
  };

  const addCategory = () => {
    setCategories([...categories, { categoryName: 'STAND_' + (categories.length + 1), capacity: 1000, price: 1200 }]);
  };

  const removeCategory = (index) => {
    if (categories.length <= 1) return;
    setCategories(categories.filter((_, i) => i !== index));
  };

  const totalAllocatedCapacity = categories.reduce((sum, c) => sum + (Number(c.capacity) || 0), 0);
  const activeStadium = stadiums.find((s) => String(s.id) === String(selectedStadiumId));
  const maxStadiumCapacity = activeStadium?.capacity || 40000;
  const isCapacityExceeded = totalAllocatedCapacity > maxStadiumCapacity;

  const handleSubmitConfiguration = async (e) => {
    e.preventDefault();
    if (!configMatch) return;
    if (isCapacityExceeded) {
      setConfigError(`Allocated capacity (${totalAllocatedCapacity}) exceeds stadium limit (${maxStadiumCapacity})`);
      return;
    }

    try {
      setSubmittingConfig(true);
      setConfigError('');

      const payload = {
        stadiumId: Number(selectedStadiumId),
        categories: categories.map((c) => ({
          categoryName: c.categoryName.trim().toUpperCase(),
          capacity: Number(c.capacity),
          price: Number(c.price),
        })),
      };

      const targetMatchId = configMatch.matchId || configMatch.id || configMatch.match_id;
      if (!targetMatchId || targetMatchId === 'undefined') {
        setConfigError('Invalid match ID. Please close and re-open the configuration.');
        return;
      }
      await eventApi.configureMatch(targetMatchId, payload);
      setConfigSuccess('Ticket pricing & category quotas successfully applied!');
      setTimeout(() => {
        setConfigMatch(null);
        loadData();
      }, 1200);
    } catch (err) {
      setConfigError(err.message || 'Failed to configure match quotas.');
    } finally {
      setSubmittingConfig(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs font-bold uppercase tracking-wider mb-2">
              <Shield className="w-3.5 h-3.5" /> Franchise Operations
            </div>
            <h1 className="text-2xl sm:text-3xl font-black text-white">
              Match Ticketing & Pricing Hub
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Configure stand capacities, pricing tiers, and inventory quotas for your team's matches.
            </p>
          </div>

          <div className="flex items-center gap-2 self-start sm:self-auto">
            {myFranchise && (
              <button
                onClick={openProfileModal}
                className="flex items-center gap-2 px-4 py-2 rounded-xl bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 text-xs font-bold text-amber-400 transition cursor-pointer"
              >
                <Shield className="w-3.5 h-3.5" /> Edit Team Profile
              </button>
            )}
            <button
              onClick={loadData}
              className="flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-bold text-slate-300 transition cursor-pointer"
            >
              <RefreshCw className="w-3.5 h-3.5" /> Refresh List
            </button>
          </div>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
          <div className="p-5 rounded-2xl bg-slate-900/80 border border-slate-800">
            <div className="flex items-center justify-between text-slate-400 text-xs font-bold uppercase mb-2">
              <span>Total Fixtures</span>
              <Calendar className="w-4 h-4 text-amber-400" />
            </div>
            <div className="text-2xl font-black text-white">{matches.length}</div>
          </div>
          <div className="p-5 rounded-2xl bg-slate-900/80 border border-slate-800">
            <div className="flex items-center justify-between text-slate-400 text-xs font-bold uppercase mb-2">
              <span>Home Stadiums</span>
              <MapPin className="w-4 h-4 text-rose-400" />
            </div>
            <div className="text-2xl font-black text-white">{stadiums.length}</div>
          </div>
          <div className="p-5 rounded-2xl bg-slate-900/80 border border-slate-800">
            <div className="flex items-center justify-between text-slate-400 text-xs font-bold uppercase mb-2">
              <span>Operating Mode</span>
              <TrendingUp className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="text-base font-bold text-emerald-400">Pessimistic Hold + Redis</div>
          </div>
        </div>

        {/* Fixtures Table */}
        <div className="rounded-2xl bg-slate-900/70 border border-slate-800 overflow-hidden shadow-xl">
          <div className="p-5 border-b border-slate-800">
            <h2 className="text-base font-bold text-white">Scheduled Matches</h2>
          </div>

          {loading ? (
            <div className="py-16 text-center">
              <div className="w-10 h-10 border-3 border-amber-500 border-t-transparent rounded-full animate-spin mx-auto mb-3"></div>
              <p className="text-xs text-slate-400">Loading assigned fixtures...</p>
            </div>
          ) : error ? (
            <div className="p-4 m-4 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs">
              {error}
            </div>
          ) : matches.length === 0 ? (
            <div className="py-12 text-center text-sm text-slate-500">
              No matches found for configuration.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-950/60 text-slate-400 uppercase tracking-wider font-semibold border-b border-slate-800">
                  <tr>
                    <th className="py-3 px-5">Match ID</th>
                    <th className="py-3 px-5">Clash</th>
                    <th className="py-3 px-5">Stadium Venue</th>
                    <th className="py-3 px-5">Status</th>
                    <th className="py-3 px-5 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 text-slate-300">
                  {matches.map((m, idx) => {
                    const currentMatchId = m.matchId || m.id || m.match_id || (idx + 1);
                    const homeFranchise = franchises.find(f => String(f.id) === String(m.homeFranchiseId));
                    const awayFranchise = franchises.find(f => String(f.id) === String(m.awayFranchiseId));
                    const assignedStadium = stadiums.find(s => String(s.id) === String(m.stadiumId));

                    const home = m.homeTeamName || homeFranchise?.franchiseName || homeFranchise?.teamCode || (m.homeFranchiseId ? `Franchise #${m.homeFranchiseId}` : 'Home Team');
                    const away = m.awayTeamName || awayFranchise?.franchiseName || awayFranchise?.teamCode || (m.awayFranchiseId ? `Franchise #${m.awayFranchiseId}` : 'Away Team');
                    const venue = (m.stadiumName && m.stadiumName !== 'null') ? m.stadiumName : (assignedStadium?.name || (m.stadiumId && m.stadiumId !== 'null' ? `Stadium #${m.stadiumId}` : 'IPL Arena Stadium'));
                    const isHome = myFranchise ? (
                      String(m.homeFranchiseId) === String(myFranchise.id) ||
                      (m.homeTeamName && myFranchise.franchiseName && m.homeTeamName.toLowerCase() === myFranchise.franchiseName.toLowerCase()) ||
                      (m.homeTeamCode && myFranchise.teamCode && m.homeTeamCode.toLowerCase() === myFranchise.teamCode.toLowerCase())
                    ) : (user?.role === 'ADMIN');

                    return (
                      <tr key={`franchise-match-${currentMatchId}-${idx}`} className="hover:bg-slate-850/50 transition">
                        <td className="py-4 px-5 font-mono font-bold text-amber-400">#{currentMatchId}</td>
                        <td className="py-4 px-5">
                          <span className="font-bold text-white">{home}</span>
                          <span className="text-slate-500 mx-1.5 font-bold">vs</span>
                          <span className="font-bold text-white">{away}</span>
                        </td>
                        <td className="py-4 px-5 text-slate-400">{venue}</td>
                        <td className="py-4 px-5">
                          <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${
                            m.status === 'APPROVED' || m.status === 'TICKETS_OPEN'
                              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                              : 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                          }`}>
                            {m.status || 'SCHEDULED'}
                          </span>
                        </td>
                        <td className="py-4 px-5 text-right">
                          {isHome ? (
                            <button
                              onClick={() => openConfigModal(m)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl font-bold text-xs bg-slate-800 hover:bg-slate-700 text-amber-400 border border-slate-700 transition cursor-pointer"
                            >
                              <Sliders className="w-3.5 h-3.5" /> Configure Pricing
                            </button>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-xl text-[11px] font-semibold bg-slate-900 border border-slate-800 text-slate-500">
                              Away Clash (Priced by Home)
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Configuration Modal */}
        {configMatch && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-2xl w-full max-h-[92vh] flex flex-col shadow-2xl overflow-hidden">
              <div className="p-5 border-b border-slate-800 flex items-center justify-between">
                <div>
                  <h3 className="text-base font-black text-white">
                    Configure Ticket Quotas - Match #{configMatch.matchId || configMatch.id}
                  </h3>
                  <p className="text-xs text-slate-400">
                    Define seating stands, capacity limits, and pricing for this encounter.
                  </p>
                </div>
                <button
                  onClick={() => setConfigMatch(null)}
                  className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              <form onSubmit={handleSubmitConfiguration} className="p-6 overflow-y-auto flex-1 space-y-5">
                {configError && (
                  <div className="p-3.5 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    <span>{configError}</span>
                  </div>
                )}

                {configSuccess && (
                  <div className="p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 shrink-0" />
                    <span>{configSuccess}</span>
                  </div>
                )}

                {/* Assigned Match Stadium (Locked by BCCI Schedule) */}
                <div className="p-4 rounded-2xl bg-slate-950/80 border border-slate-800">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                      <MapPin className="w-3.5 h-3.5 text-rose-400" /> Assigned Match Stadium
                    </span>
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-400 border border-amber-500/20">
                      <Lock className="w-3 h-3" /> Locked by BCCI Schedule
                    </span>
                  </div>
                  <div className="text-sm font-black text-white flex items-center justify-between">
                    <span>{activeStadium?.name || configMatch?.stadiumName || 'Assigned Match Stadium'}</span>
                    <span className="text-xs font-mono text-slate-400 font-normal">Max Capacity: {maxStadiumCapacity.toLocaleString()} seats</span>
                  </div>
                  <p className="text-[11px] text-slate-500 mt-1.5">
                    Tournament venue was fixed during BCCI fixture scheduling and cannot be altered. Home franchise configures seating tiers, capacity allocations, and ticket prices below.
                  </p>
                </div>

                {/* Categories Table */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-xs font-bold uppercase tracking-wider text-slate-400">
                      Ticket Tiers & Price Allocation
                    </label>
                    <button
                      type="button"
                      onClick={addCategory}
                      className="inline-flex items-center gap-1 text-xs font-bold text-amber-400 hover:text-amber-300"
                    >
                      <Plus className="w-3.5 h-3.5" /> Add Tier
                    </button>
                  </div>

                  <div className="space-y-2">
                    {categories.map((cat, idx) => (
                      <div
                        key={idx}
                        className="grid grid-cols-12 gap-2 p-3 bg-slate-950/60 rounded-xl border border-slate-800 items-center"
                      >
                        <div className="col-span-5">
                          <input
                            type="text"
                            value={cat.categoryName}
                            onChange={(e) => handleCategoryChange(idx, 'categoryName', e.target.value)}
                            placeholder="Category Name"
                            required
                            className="w-full px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-700 text-white text-xs focus:ring-1 focus:ring-amber-500"
                          />
                        </div>
                        <div className="col-span-3">
                          <input
                            type="number"
                            min="1"
                            value={cat.capacity}
                            onChange={(e) => handleCategoryChange(idx, 'capacity', e.target.value)}
                            placeholder="Seats"
                            required
                            className="w-full px-3 py-1.5 rounded-lg bg-slate-900 border border-slate-700 text-white text-xs focus:ring-1 focus:ring-amber-500"
                          />
                        </div>
                        <div className="col-span-3">
                          <div className="relative">
                            <span className="absolute left-2.5 top-1.5 text-slate-500 text-xs">₹</span>
                            <input
                              type="number"
                              min="1"
                              value={cat.price}
                              onChange={(e) => handleCategoryChange(idx, 'price', e.target.value)}
                              placeholder="Price"
                              required
                              className="w-full pl-6 pr-2 py-1.5 rounded-lg bg-slate-900 border border-slate-700 text-white text-xs focus:ring-1 focus:ring-amber-500"
                            />
                          </div>
                        </div>
                        <div className="col-span-1 text-right">
                          <button
                            type="button"
                            onClick={() => removeCategory(idx)}
                            className="p-1.5 text-slate-500 hover:text-rose-400 rounded-lg transition"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Capacity Counter */}
                <div className={`p-3.5 rounded-xl border text-xs flex items-center justify-between ${
                  isCapacityExceeded
                    ? 'bg-rose-500/10 border-rose-500/30 text-rose-300'
                    : 'bg-slate-950 border-slate-800 text-slate-300'
                }`}>
                  <span>Total Allocated Seats:</span>
                  <span className="font-mono font-bold">
                    {totalAllocatedCapacity} / {maxStadiumCapacity}
                  </span>
                </div>

                {/* Actions */}
                <div className="pt-2 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setConfigMatch(null)}
                    className="px-4 py-2 rounded-xl text-xs font-semibold bg-slate-800 text-slate-300 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={submittingConfig || isCapacityExceeded}
                    className="px-5 py-2 rounded-xl text-xs font-bold bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 hover:from-amber-300 hover:to-orange-400 disabled:opacity-50 transition cursor-pointer"
                  >
                    {submittingConfig ? 'Applying Quotas...' : 'Save & Publish Configuration'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* MODAL: EDIT TEAM PROFILE */}
        {showProfileModal && myFranchise && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-md w-full p-6 shadow-2xl">
              <h3 className="text-base font-black text-white mb-1">Edit Team Franchise Profile</h3>
              <p className="text-xs text-slate-400 mb-4">{myFranchise.franchiseName} ({myFranchise.teamCode})</p>
              <form onSubmit={handleSaveProfile} className="space-y-3.5">
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Short Name</label>
                    <input
                      type="text"
                      value={editShortName}
                      onChange={(e) => setEditShortName(e.target.value)}
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">City</label>
                    <input
                      type="text"
                      value={editCity}
                      onChange={(e) => setEditCity(e.target.value)}
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Contact Phone</label>
                  <input
                    type="text"
                    value={editPhone}
                    onChange={(e) => setEditPhone(e.target.value)}
                    placeholder="+91 98765 43210"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Logo URL</label>
                  <input
                    type="url"
                    value={editLogoUrl}
                    onChange={(e) => setEditLogoUrl(e.target.value)}
                    placeholder="https://images.example.com/logo.png"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Home Stadium</label>
                  <select
                    value={editStadiumId}
                    onChange={(e) => setEditStadiumId(e.target.value)}
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  >
                    {stadiums.map((s) => (
                      <option key={s.id} value={s.id}>{s.name} ({s.city})</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Description / Bio</label>
                  <textarea
                    rows={2}
                    value={editDesc}
                    onChange={(e) => setEditDesc(e.target.value)}
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div className="flex justify-end gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowProfileModal(false)}
                    className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={savingProfile}
                    className="px-4 py-1.5 rounded-lg text-xs font-bold bg-amber-500 text-slate-950"
                  >
                    {savingProfile ? 'Saving...' : 'Save Profile'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

