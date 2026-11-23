import React, { useState, useEffect } from 'react';
import { 
  Shield, Trophy, Calendar, MapPin, Building, 
  QrCode, Bell, Plus, CheckCircle2, XCircle, AlertCircle, RefreshCw, Send, Check
} from 'lucide-react';
import { eventApi } from '../../api/eventApi';
import { orderApi } from '../../api/orderApi';
import { notificationApi } from '../../api/notificationApi';
import { authApi } from '../../api/authApi';
import { Navbar } from '../../components/common/Navbar';

export const AdminDashboard = () => {
  const [activeTab, setActiveTab] = useState('matches'); // 'matches' | 'turnstile' | 'infrastructure' | 'broadcast'
  
  // Data State
  const [matches, setMatches] = useState([]);
  const [stadiums, setStadiums] = useState([]);
  const [franchises, setFranchises] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Turnstile Scanner Simulator State
  const [qrInput, setQrInput] = useState('');
  const [gateName, setGateName] = useState('GATE 3 - EAST TURNSTILE');
  const [scanning, setScanning] = useState(false);
  const [turnstileResult, setTurnstileResult] = useState(null);

  // Broadcast Announcement State
  const [broadcastTitle, setBroadcastTitle] = useState('');
  const [broadcastMsg, setBroadcastMsg] = useState('');
  const [broadcasting, setBroadcasting] = useState(false);
  const [broadcastSuccess, setBroadcastSuccess] = useState('');

  // New Match Modal
  const [showMatchModal, setShowMatchModal] = useState(false);
  const [homeFranchiseId, setHomeFranchiseId] = useState('');
  const [awayFranchiseId, setAwayFranchiseId] = useState('');
  const [matchStadiumId, setMatchStadiumId] = useState('');
  const [matchDateTime, setMatchDateTime] = useState('');
  const [creatingMatch, setCreatingMatch] = useState(false);

  // New Stadium Modal
  const [showStadiumModal, setShowStadiumModal] = useState(false);
  const [stadiumName, setStadiumName] = useState('');
  const [stadiumCity, setStadiumCity] = useState('');
  const [stadiumCapacity, setStadiumCapacity] = useState(45000);
  const [creatingStadium, setCreatingStadium] = useState(false);

  // New Franchise Modal
  const [showFranchiseModal, setShowFranchiseModal] = useState(false);
  const [franchiseName, setFranchiseName] = useState('');
  const [teamCode, setTeamCode] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [franchisePassword, setFranchisePassword] = useState('Csk@2026!');
  const [franchiseStadiumId, setFranchiseStadiumId] = useState('');
  const [franchiseCity, setFranchiseCity] = useState('Chennai');
  const [franchisePhone, setFranchisePhone] = useState('+91 98765 43210');
  const [franchiseLogoUrl, setFranchiseLogoUrl] = useState('');
  const [franchiseDesc, setFranchiseDesc] = useState('Official IPL Franchise');
  const [creatingFranchise, setCreatingFranchise] = useState(false);

  // Edit Franchise Modal
  const [editingFranchise, setEditingFranchise] = useState(null);
  const [editPhone, setEditPhone] = useState('');
  const [editLogoUrl, setEditLogoUrl] = useState('');
  const [editCity, setEditCity] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [editShortName, setEditShortName] = useState('');
  const [editStadiumId, setEditStadiumId] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);

  useEffect(() => {
    loadAllData();
  }, []);

  const loadAllData = async () => {
    try {
      setLoading(true);
      setError('');
      const [matchesRes, stadiumsRes, franchisesRes] = await Promise.all([
        eventApi.getMatches(),
        eventApi.getStadiums().catch(() => ({ data: [] })),
        eventApi.getFranchises().catch(() => ({ data: [] })),
      ]);

      setMatches(matchesRes?.data || matchesRes || []);
      setStadiums(stadiumsRes?.data || stadiumsRes || []);
      setFranchises(franchisesRes?.data || franchisesRes || []);
    } catch (err) {
      setError('Failed to load BCCI administration data: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleApproveMatch = async (matchId) => {
    if (!matchId) {
      alert('Match ID is missing. Please reload the fixtures list.');
      return;
    }
    try {
      await eventApi.approveMatch(matchId);
      setMatches((prev) =>
        prev.map((m) => ((m.matchId === matchId || m.id === matchId) ? { ...m, status: 'APPROVED' } : m))
      );
      loadAllData();
    } catch (err) {
      alert('Match approval failed: ' + err.message);
    }
  };

  const handleScanTurnstile = async (e) => {
    e?.preventDefault();
    if (!qrInput) return;

    try {
      setScanning(true);
      setTurnstileResult(null);
      const res = await orderApi.validateTurnstile(qrInput.trim(), gateName);
      const data = res?.data || res;
      setTurnstileResult(data);
    } catch (err) {
      setTurnstileResult({
        result: 'DENIED',
        message: err.message || 'Cryptographic signature invalid or turnstile network timeout.',
      });
    } finally {
      setScanning(false);
    }
  };

  const handleSendBroadcast = async (e) => {
    e.preventDefault();
    if (!broadcastTitle || !broadcastMsg) return;

    try {
      setBroadcasting(true);
      setBroadcastSuccess('');
      await notificationApi.broadcast({
        title: broadcastTitle,
        message: broadcastMsg,
      });
      setBroadcastSuccess('Announcement published to Kafka topic and dispatched to all fans!');
      setBroadcastTitle('');
      setBroadcastMsg('');
    } catch (err) {
      alert('Broadcast dispatch failed: ' + err.message);
    } finally {
      setBroadcasting(false);
    }
  };

  const handleHomeFranchiseSelect = (selectedHomeId) => {
    setHomeFranchiseId(selectedHomeId);
    const selectedHome = franchises.find((f) => String(f.id) === String(selectedHomeId));
    if (selectedHome?.homeStadiumId) {
      setMatchStadiumId(String(selectedHome.homeStadiumId));
    } else if (stadiums.length > 0) {
      setMatchStadiumId(String(stadiums[0].id));
    }
  };

  const openNewMatchModal = () => {
    if (franchises.length >= 2) {
      const homeF = franchises[0];
      setHomeFranchiseId(String(homeF.id));
      setAwayFranchiseId(String(franchises[1].id));
      if (homeF.homeStadiumId) {
        setMatchStadiumId(String(homeF.homeStadiumId));
      } else if (stadiums.length > 0) {
        setMatchStadiumId(String(stadiums[0].id));
      }
    } else if (franchises.length === 1) {
      const homeF = franchises[0];
      setHomeFranchiseId(String(homeF.id));
      setAwayFranchiseId('');
      if (homeF.homeStadiumId) {
        setMatchStadiumId(String(homeF.homeStadiumId));
      } else if (stadiums.length > 0) {
        setMatchStadiumId(String(stadiums[0].id));
      }
    } else {
      setHomeFranchiseId('');
      setAwayFranchiseId('');
      setMatchStadiumId(stadiums[0]?.id ? String(stadiums[0].id) : '');
    }
    setShowMatchModal(true);
  };

  const handleCreateMatch = async (e) => {
    e.preventDefault();
    if (!homeFranchiseId || !awayFranchiseId) {
      alert('Please select both home and away franchises.');
      return;
    }
    if (homeFranchiseId === awayFranchiseId) {
      alert('Home and Away teams must be different franchises.');
      return;
    }
    if (!matchStadiumId) {
      alert('Please select a stadium venue for the match.');
      return;
    }
    try {
      setCreatingMatch(true);
      await eventApi.createMatch({
        homeFranchiseId: Number(homeFranchiseId),
        awayFranchiseId: Number(awayFranchiseId),
        stadiumId: Number(matchStadiumId),
        matchDateTime: matchDateTime || new Date(Date.now() + 86400000 * 7).toISOString(),
      });
      setShowMatchModal(false);
      loadAllData();
    } catch (err) {
      alert('Failed to schedule match: ' + err.message);
    } finally {
      setCreatingMatch(false);
    }
  };

  const handleCreateStadium = async (e) => {
    e.preventDefault();
    try {
      setCreatingStadium(true);
      await eventApi.createStadium({
        name: stadiumName,
        city: stadiumCity,
        capacity: Number(stadiumCapacity),
      });
      setShowStadiumModal(false);
      setStadiumName('');
      setStadiumCity('');
      loadAllData();
    } catch (err) {
      alert('Failed to create stadium: ' + err.message);
    } finally {
      setCreatingStadium(false);
    }
  };

  const handleCreateFranchise = async (e) => {
    e.preventDefault();
    if (stadiums.length === 0) {
      alert('Please add at least one Stadium before registering a Franchise.');
      return;
    }
    try {
      setCreatingFranchise(true);
      // 1. Create auth credentials
      const authRes = await authApi.registerFranchise(teamCode.trim().toLowerCase(), contactEmail.trim(), franchisePassword);
      const user = authRes?.data || authRes;
      const userId = user?.id;
      if (!userId) {
        throw new Error('Authentication service did not return a valid user ID.');
      }

      // 2. Create franchise profile
      await eventApi.createFranchise({
        userId,
        franchiseName: franchiseName.trim(),
        teamCode: teamCode.trim().toUpperCase(),
        shortName: teamCode.trim().toUpperCase(),
        city: franchiseCity.trim() || 'India',
        description: franchiseDesc.trim(),
        logoUrl: franchiseLogoUrl.trim(),
        contactPhone: franchisePhone.trim(),
        contactEmail: contactEmail.trim(),
        homeStadiumId: Number(franchiseStadiumId || stadiums[0]?.id),
      });

      setShowFranchiseModal(false);
      setFranchiseName('');
      setTeamCode('');
      setContactEmail('');
      setFranchisePhone('');
      setFranchiseLogoUrl('');
      setFranchiseDesc('');
      loadAllData();
    } catch (err) {
      alert('Failed to register franchise: ' + err.message);
    } finally {
      setCreatingFranchise(false);
    }
  };

  const openEditFranchiseModal = (f) => {
    setEditingFranchise(f);
    setEditPhone(f.contactPhone || '');
    setEditLogoUrl(f.logoUrl || '');
    setEditCity(f.city || '');
    setEditDesc(f.description || '');
    setEditShortName(f.shortName || f.teamCode || '');
    setEditStadiumId(f.homeStadiumId ? String(f.homeStadiumId) : (stadiums[0]?.id ? String(stadiums[0].id) : '1'));
  };

  const handleSaveFranchiseEdit = async (e) => {
    e.preventDefault();
    if (!editingFranchise) return;
    try {
      setSavingEdit(true);
      await eventApi.updateFranchise(editingFranchise.id, {
        shortName: editShortName,
        city: editCity,
        description: editDesc,
        logoUrl: editLogoUrl,
        contactPhone: editPhone,
        homeStadiumId: editStadiumId ? Number(editStadiumId) : undefined,
      });
      setEditingFranchise(null);
      loadAllData();
    } catch (err) {
      alert('Failed to update franchise profile: ' + err.message);
    } finally {
      setSavingEdit(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
          <div>
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs font-bold uppercase tracking-wider mb-2">
              <Shield className="w-3.5 h-3.5" /> BCCI Governance & Control
            </div>
            <h1 className="text-2xl sm:text-3xl font-black text-white">
              Official Administration Console
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-1">
              Tournament schedule governance, gate turnstile validation, and emergency broadcasts.
            </p>
          </div>

          <button
            onClick={loadAllData}
            className="self-start sm:self-auto flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-bold text-slate-300 transition"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Refresh State
          </button>
        </div>

        {/* Tab Controls */}
        <div className="flex border-b border-slate-800 gap-2 mb-8 overflow-x-auto pb-2">
          <button
            onClick={() => setActiveTab('matches')}
            className={`px-4 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-2 shrink-0 ${
              activeTab === 'matches'
                ? 'bg-amber-500 text-slate-950 shadow-lg shadow-amber-500/20'
                : 'text-slate-400 hover:text-white bg-slate-900/50'
            }`}
          >
            <Trophy className="w-4 h-4" /> Match Governance ({matches.length})
          </button>

          <button
            onClick={() => setActiveTab('turnstile')}
            className={`px-4 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-2 shrink-0 ${
              activeTab === 'turnstile'
                ? 'bg-amber-500 text-slate-950 shadow-lg shadow-amber-500/20'
                : 'text-slate-400 hover:text-white bg-slate-900/50'
            }`}
          >
            <QrCode className="w-4 h-4" /> Turnstile Gate Scanner Simulator
          </button>

          <button
            onClick={() => setActiveTab('infrastructure')}
            className={`px-4 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-2 shrink-0 ${
              activeTab === 'infrastructure'
                ? 'bg-amber-500 text-slate-950 shadow-lg shadow-amber-500/20'
                : 'text-slate-400 hover:text-white bg-slate-900/50'
            }`}
          >
            <Building className="w-4 h-4" /> Stadiums & Franchises
          </button>

          <button
            onClick={() => setActiveTab('broadcast')}
            className={`px-4 py-2.5 rounded-xl text-xs font-bold transition flex items-center gap-2 shrink-0 ${
              activeTab === 'broadcast'
                ? 'bg-amber-500 text-slate-950 shadow-lg shadow-amber-500/20'
                : 'text-slate-400 hover:text-white bg-slate-900/50'
            }`}
          >
            <Bell className="w-4 h-4" /> Fan Broadcast Dispatcher
          </button>
        </div>

        {/* TAB 1: MATCH GOVERNANCE */}
        {activeTab === 'matches' && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-white">IPL Tournament Fixtures</h2>
              <button
                onClick={openNewMatchModal}
                className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 transition cursor-pointer"
              >
                <Plus className="w-4 h-4" /> Schedule New Match
              </button>
            </div>

            <div className="rounded-2xl bg-slate-900/70 border border-slate-800 overflow-hidden shadow-xl">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-950/60 text-slate-400 uppercase tracking-wider font-semibold border-b border-slate-800">
                    <tr>
                      <th className="py-3 px-5">Match ID</th>
                      <th className="py-3 px-5">Encounter</th>
                      <th className="py-3 px-5">Venue</th>
                      <th className="py-3 px-5">Date & Time</th>
                      <th className="py-3 px-5">Status</th>
                      <th className="py-3 px-5 text-right">Approval Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 text-slate-300">
                    {matches.map((m, idx) => {
                      const currentMatchId = m.matchId || m.id || m.match_id || (idx + 1);
                      const isApproved = m.status === 'APPROVED' || m.status === 'TICKETS_OPEN';
                      const homeFranchise = franchises.find(f => String(f.id) === String(m.homeFranchiseId));
                      const awayFranchise = franchises.find(f => String(f.id) === String(m.awayFranchiseId));
                      const assignedStadium = stadiums.find(s => String(s.id) === String(m.stadiumId));

                      const home = m.homeTeamName || homeFranchise?.franchiseName || homeFranchise?.teamCode || (m.homeFranchiseId ? `Franchise #${m.homeFranchiseId}` : 'Home Team');
                      const away = m.awayTeamName || awayFranchise?.franchiseName || awayFranchise?.teamCode || (m.awayFranchiseId ? `Franchise #${m.awayFranchiseId}` : 'Away Team');
                      const venue = (m.stadiumName && m.stadiumName !== 'null') ? m.stadiumName : (assignedStadium?.name || (m.stadiumId && m.stadiumId !== 'null' ? `Stadium #${m.stadiumId}` : 'IPL Arena Stadium'));

                      return (
                        <tr key={`admin-match-${currentMatchId}-${idx}`} className="hover:bg-slate-850/50 transition">
                          <td className="py-4 px-5 font-mono font-bold text-amber-400">#{currentMatchId}</td>
                          <td className="py-4 px-5 font-bold text-white">
                            {home} vs {away}
                          </td>
                          <td className="py-4 px-5 text-slate-400">
                            {venue}
                          </td>
                          <td className="py-4 px-5 text-slate-400">
                            {m.matchDateTime ? new Date(m.matchDateTime).toLocaleString() : 'TBD'}
                          </td>
                          <td className="py-4 px-5">
                            <span className={`px-2.5 py-0.5 rounded-full text-[10px] font-bold border ${
                              isApproved
                                ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                                : 'bg-amber-500/10 text-amber-400 border-amber-500/20'
                            }`}>
                              {m.status || 'SCHEDULED'}
                            </span>
                          </td>
                          <td className="py-4 px-5 text-right">
                            {isApproved ? (
                              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-400">
                                <Check className="w-3.5 h-3.5" /> Approved & On Sale
                              </span>
                            ) : m.status === 'WAITING_FOR_APPROVAL' ? (
                              <button
                                onClick={() => handleApproveMatch(currentMatchId)}
                                className="px-3 py-1.5 rounded-lg text-xs font-bold bg-emerald-500 hover:bg-emerald-400 text-slate-950 transition cursor-pointer shadow-md shadow-emerald-500/20"
                              >
                                Approve for Sale
                              </button>
                            ) : (
                              <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-xl text-[10px] font-semibold bg-slate-900 border border-slate-800 text-slate-500">
                                Awaiting Franchise Pricing
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: TURNSTILE GATE SCANNER SIMULATOR */}
        {activeTab === 'turnstile' && (
          <div className="max-w-2xl mx-auto space-y-6">
            <div className="p-6 rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl">
              <div className="flex items-center gap-3 mb-4">
                <div className="p-3 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-400">
                  <QrCode className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-lg font-black text-white">Physical Turnstile Scanner Simulator</h3>
                  <p className="text-xs text-slate-400">
                    Emulates physical optical turnstiles validating HMAC cryptographic tokens at stadium gates.
                  </p>
                </div>
              </div>

              <form onSubmit={handleScanTurnstile} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                    Turnstile Gate Designation
                  </label>
                  <select
                    value={gateName}
                    onChange={(e) => setGateName(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs focus:ring-2 focus:ring-amber-500"
                  >
                    <option value="GATE 1 - NORTH PAVILION">GATE 1 - NORTH PAVILION</option>
                    <option value="GATE 2 - VIP BOX ENTRANCE">GATE 2 - VIP BOX ENTRANCE</option>
                    <option value="GATE 3 - EAST TURNSTILE">GATE 3 - EAST TURNSTILE</option>
                    <option value="GATE 4 - GENERAL TURNSTILE B">GATE 4 - GENERAL TURNSTILE B</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                    Scanned QR Code Payload (or Test Value)
                  </label>
                  <textarea
                    rows={3}
                    value={qrInput}
                    onChange={(e) => setQrInput(e.target.value)}
                    required
                    placeholder="Paste ticket QR string or HMAC token payload..."
                    className="w-full p-3 rounded-xl bg-slate-950 border border-slate-800 text-white font-mono text-xs focus:ring-2 focus:ring-amber-500"
                  />
                </div>

                <button
                  type="submit"
                  disabled={scanning}
                  className="w-full py-3 rounded-xl text-xs font-bold bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 flex items-center justify-center gap-2 hover:from-amber-300 hover:to-orange-400 transition"
                >
                  {scanning ? (
                    <div className="w-4 h-4 border-2 border-slate-950 border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <>
                      <QrCode className="w-4 h-4" />
                      <span>Validate QR at Turnstile Gate</span>
                    </>
                  )}
                </button>
              </form>

              {/* Turnstile Output Display */}
              {turnstileResult && (
                <div className={`mt-6 p-5 rounded-2xl border text-center transition-all ${
                  turnstileResult.result === 'ALLOWED'
                    ? 'bg-emerald-950/40 border-emerald-500/50 text-emerald-200'
                    : 'bg-rose-950/40 border-rose-500/50 text-rose-200'
                }`}>
                  <div className="flex justify-center mb-2">
                    {turnstileResult.result === 'ALLOWED' ? (
                      <CheckCircle2 className="w-12 h-12 text-emerald-400 animate-bounce" />
                    ) : (
                      <XCircle className="w-12 h-12 text-rose-400" />
                    )}
                  </div>

                  <div className="text-xl font-black uppercase tracking-wider mb-1">
                    TURNSTILE {turnstileResult.result}: {turnstileResult.result === 'ALLOWED' ? 'ENTRY PERMITTED' : 'ACCESS DENIED'}
                  </div>

                  <p className="text-xs opacity-90 max-w-sm mx-auto mb-3">
                    {turnstileResult.message || 'Validation evaluation complete.'}
                  </p>

                  {turnstileResult.result === 'ALLOWED' && (
                    <div className="inline-grid grid-cols-2 gap-4 text-xs font-mono pt-3 border-t border-emerald-500/30 text-left">
                      <div>Ticket: <span className="text-white font-bold">{turnstileResult.ticketNumber || 'TKT-101'}</span></div>
                      <div>Seat: <span className="text-white font-bold">{turnstileResult.seatNumber || 'A-12'}</span></div>
                      <div>Stand: <span className="text-white font-bold">{turnstileResult.stand || 'East'}</span></div>
                      <div>Gate: <span className="text-white font-bold">{turnstileResult.gate || gateName}</span></div>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* TAB 3: INFRASTRUCTURE */}
        {activeTab === 'infrastructure' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            {/* Stadiums Column */}
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <MapPin className="w-4 h-4 text-rose-400" /> Stadium Venues
                </h3>
                <button
                  onClick={() => setShowStadiumModal(true)}
                  className="px-2.5 py-1 rounded-lg text-[11px] font-bold bg-slate-800 text-amber-400 hover:text-white"
                >
                  + Add Stadium
                </button>
              </div>

              <div className="space-y-2">
                {stadiums.map((s) => (
                  <div key={s.id} className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 flex justify-between items-center">
                    <div>
                      <div className="text-xs font-bold text-white">{s.name}</div>
                      <div className="text-[11px] text-slate-400">{s.city} &bull; Capacity: {s.capacity}</div>
                    </div>
                    <span className="text-[10px] font-mono text-slate-500">ID #{s.id}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Franchises Column */}
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <Shield className="w-4 h-4 text-amber-400" /> Registered Franchises
                </h3>
                <button
                  onClick={() => setShowFranchiseModal(true)}
                  className="px-2.5 py-1 rounded-lg text-[11px] font-bold bg-slate-800 text-amber-400 hover:text-white transition"
                >
                  + Add Franchise
                </button>
              </div>

              <div className="space-y-2">
                {franchises.map((f, idx) => (
                  <div key={f.id || idx} className="p-3.5 rounded-xl bg-slate-900 border border-slate-800 flex justify-between items-center">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-white">{f.franchiseName || f.teamCode}</span>
                        {f.city && <span className="text-[10px] px-1.5 py-0.2 rounded bg-slate-800 text-slate-300">{f.city}</span>}
                      </div>
                      <div className="text-[11px] text-slate-400 mt-0.5">
                        Code: <span className="text-amber-400 font-bold">{f.teamCode}</span> &bull; Phone: {f.contactPhone || 'N/A'}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() => openEditFranchiseModal(f)}
                        className="px-2 py-1 rounded-lg text-[10px] font-bold bg-slate-800 hover:bg-slate-700 text-amber-400 border border-slate-700 transition"
                      >
                        Edit Profile
                      </button>
                      <span className="text-[10px] font-mono text-slate-500">ID #{f.id}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: BROADCAST */}
        {activeTab === 'broadcast' && (
          <div className="max-w-xl mx-auto">
            <div className="p-6 rounded-3xl bg-slate-900 border border-slate-800 shadow-2xl">
              <div className="flex items-center gap-3 mb-4">
                <div className="p-3 rounded-2xl bg-blue-500/10 border border-blue-500/20 text-blue-400">
                  <Bell className="w-6 h-6" />
                </div>
                <div>
                  <h3 className="text-lg font-black text-white">Broadcast Public Alert</h3>
                  <p className="text-xs text-slate-400">
                    Dispatches high-priority event announcements to all fan inboxes via Kafka.
                  </p>
                </div>
              </div>

              {broadcastSuccess && (
                <div className="mb-4 p-3.5 rounded-xl bg-emerald-500/10 border border-emerald-500/20 text-emerald-300 text-xs">
                  {broadcastSuccess}
                </div>
              )}

              <form onSubmit={handleSendBroadcast} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                    Announcement Subject
                  </label>
                  <input
                    type="text"
                    value={broadcastTitle}
                    onChange={(e) => setBroadcastTitle(e.target.value)}
                    required
                    placeholder="e.g. Flash Sale Live: CSK vs RCB Finals Tickets!"
                    className="w-full px-3.5 py-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs focus:ring-2 focus:ring-amber-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
                    Message Body
                  </label>
                  <textarea
                    rows={4}
                    value={broadcastMsg}
                    onChange={(e) => setBroadcastMsg(e.target.value)}
                    required
                    placeholder="Enter urgent broadcast text..."
                    className="w-full p-3 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs focus:ring-2 focus:ring-amber-500"
                  />
                </div>

                <button
                  type="submit"
                  disabled={broadcasting}
                  className="w-full py-3 rounded-xl text-xs font-bold bg-gradient-to-r from-blue-500 to-indigo-600 text-white flex items-center justify-center gap-2 hover:from-blue-400 hover:to-indigo-500 transition shadow-lg shadow-blue-500/20 cursor-pointer"
                >
                  {broadcasting ? (
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  ) : (
                    <>
                      <Send className="w-4 h-4" />
                      <span>Broadcast via Kafka</span>
                    </>
                  )}
                </button>
              </form>
            </div>
          </div>
        )}

        {/* MODAL: SCHEDULE MATCH */}
        {showMatchModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-md w-full p-6 shadow-2xl">
              <h3 className="text-base font-black text-white mb-4">Schedule New Fixture</h3>
              {franchises.length < 2 || stadiums.length === 0 ? (
                <div className="space-y-4">
                  <div className="p-4 rounded-2xl bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs">
                    <p className="font-bold mb-1">Infrastructure Setup Required</p>
                    <p className="text-slate-400">
                      You need at least <strong>2 registered franchises</strong> and <strong>1 stadium venue</strong> before scheduling matches.
                    </p>
                    <p className="mt-2 text-slate-400">
                      Switch to the <strong>Stadiums & Franchises</strong> tab above to create your stadiums and franchises.
                    </p>
                  </div>
                  <div className="flex justify-end">
                    <button
                      type="button"
                      onClick={() => setShowMatchModal(false)}
                      className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-300 hover:text-white"
                    >
                      Close
                    </button>
                  </div>
                </div>
              ) : (
                <form onSubmit={handleCreateMatch} className="space-y-4">
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Home Franchise</label>
                    <select
                      value={homeFranchiseId}
                      onChange={(e) => handleHomeFranchiseSelect(e.target.value)}
                      required
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    >
                      {franchises.map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.franchiseName || f.teamCode} ({f.teamCode})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Away Franchise</label>
                    <select
                      value={awayFranchiseId}
                      onChange={(e) => setAwayFranchiseId(e.target.value)}
                      required
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    >
                      {franchises.map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.franchiseName || f.teamCode} ({f.teamCode})
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Stadium Venue</label>
                    <select
                      value={matchStadiumId}
                      onChange={(e) => setMatchStadiumId(e.target.value)}
                      required
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    >
                      {stadiums.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name} ({s.city}) &bull; Cap: {s.capacity}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Match Date & Time</label>
                    <input
                      type="datetime-local"
                      value={matchDateTime}
                      onChange={(e) => setMatchDateTime(e.target.value)}
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    />
                  </div>
                  <div className="flex justify-end gap-2 pt-2">
                    <button
                      type="button"
                      onClick={() => setShowMatchModal(false)}
                      className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={creatingMatch}
                      className="px-4 py-1.5 rounded-lg text-xs font-bold bg-amber-500 text-slate-950"
                    >
                      {creatingMatch ? 'Scheduling...' : 'Save Fixture'}
                    </button>
                  </div>
                </form>
              )}
            </div>
          </div>
        )}

        {/* MODAL: ADD STADIUM */}
        {showStadiumModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-md w-full p-6 shadow-2xl">
              <h3 className="text-base font-black text-white mb-4">Register New Stadium</h3>
              <form onSubmit={handleCreateStadium} className="space-y-4">
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Stadium Name</label>
                  <input
                    type="text"
                    value={stadiumName}
                    onChange={(e) => setStadiumName(e.target.value)}
                    required
                    placeholder="e.g. Narendra Modi Stadium"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">City</label>
                  <input
                    type="text"
                    value={stadiumCity}
                    onChange={(e) => setStadiumCity(e.target.value)}
                    required
                    placeholder="e.g. Ahmedabad"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Seating Capacity</label>
                  <input
                    type="number"
                    min="1000"
                    value={stadiumCapacity}
                    onChange={(e) => setStadiumCapacity(e.target.value)}
                    required
                    placeholder="100000"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div className="flex justify-end gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowStadiumModal(false)}
                    className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={creatingStadium}
                    className="px-4 py-1.5 rounded-lg text-xs font-bold bg-amber-500 text-slate-950"
                  >
                    {creatingStadium ? 'Creating...' : 'Register Stadium'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* MODAL: ADD FRANCHISE */}
        {showFranchiseModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-md w-full p-6 shadow-2xl">
              <h3 className="text-base font-black text-white mb-4">Register New Franchise</h3>
              <form onSubmit={handleCreateFranchise} className="space-y-3.5">
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Franchise Name</label>
                  <input
                    type="text"
                    value={franchiseName}
                    onChange={(e) => setFranchiseName(e.target.value)}
                    required
                    placeholder="Chennai Super Kings"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Team Code</label>
                    <input
                      type="text"
                      value={teamCode}
                      onChange={(e) => setTeamCode(e.target.value)}
                      required
                      placeholder="CSK"
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs uppercase"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Home Stadium</label>
                    <select
                      value={franchiseStadiumId}
                      onChange={(e) => setFranchiseStadiumId(e.target.value)}
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    >
                      {stadiums.map((s) => (
                        <option key={s.id} value={s.id}>{s.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">City</label>
                    <input
                      type="text"
                      value={franchiseCity}
                      onChange={(e) => setFranchiseCity(e.target.value)}
                      placeholder="Chennai"
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Phone Number</label>
                    <input
                      type="text"
                      value={franchisePhone}
                      onChange={(e) => setFranchisePhone(e.target.value)}
                      placeholder="+91 98765 43210"
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Rep Email (Login ID)</label>
                    <input
                      type="email"
                      value={contactEmail}
                      onChange={(e) => setContactEmail(e.target.value)}
                      required
                      placeholder="admin@csk.com"
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-bold text-slate-400 mb-1">Initial Password</label>
                    <input
                      type="text"
                      value={franchisePassword}
                      onChange={(e) => setFranchisePassword(e.target.value)}
                      required
                      placeholder="Csk@2026!"
                      className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs font-mono"
                    />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Logo URL (Optional)</label>
                  <input
                    type="url"
                    value={franchiseLogoUrl}
                    onChange={(e) => setFranchiseLogoUrl(e.target.value)}
                    placeholder="https://example.com/logo.png"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Bio / Description</label>
                  <input
                    type="text"
                    value={franchiseDesc}
                    onChange={(e) => setFranchiseDesc(e.target.value)}
                    placeholder="Official IPL Franchise Team"
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div className="flex justify-end gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowFranchiseModal(false)}
                    className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={creatingFranchise}
                    className="px-4 py-1.5 rounded-lg text-xs font-bold bg-amber-500 text-slate-950"
                  >
                    {creatingFranchise ? 'Registering...' : 'Register Franchise'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* MODAL: EDIT FRANCHISE PROFILE */}
        {editingFranchise && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-md w-full p-6 shadow-2xl">
              <h3 className="text-base font-black text-white mb-1">Edit Franchise Profile</h3>
              <p className="text-xs text-slate-400 mb-4">{editingFranchise.franchiseName} ({editingFranchise.teamCode})</p>
              <form onSubmit={handleSaveFranchiseEdit} className="space-y-3.5">
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
                    className="w-full p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-white text-xs"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-400 mb-1">Logo URL</label>
                  <input
                    type="url"
                    value={editLogoUrl}
                    onChange={(e) => setEditLogoUrl(e.target.value)}
                    placeholder="https://images.example.com/csk-logo.png"
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
                    onClick={() => setEditingFranchise(null)}
                    className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={savingEdit}
                    className="px-4 py-1.5 rounded-lg text-xs font-bold bg-amber-500 text-slate-950"
                  >
                    {savingEdit ? 'Saving Changes...' : 'Update Profile'}
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

