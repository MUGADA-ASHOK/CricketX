import React, { useState, useEffect } from 'react';
import { 
  Ticket, QrCode, ShieldCheck, Calendar, MapPin, 
  CheckCircle2, AlertCircle, RefreshCw, ArrowLeft, Download, Eye
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { orderApi } from '../../api/orderApi';
import { Navbar } from '../../components/common/Navbar';

export const MyTickets = () => {
  const [tickets, setTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedTicket, setSelectedTicket] = useState(null);

  useEffect(() => {
    fetchTickets();
  }, []);

  const fetchTickets = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await orderApi.getMyTickets();
      const list = res?.data || res || [];
      setTickets(Array.isArray(list) ? list : []);
    } catch (err) {
      setError('Failed to fetch digital tickets: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case 'ISSUED':
      case 'CONFIRMED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
            <CheckCircle2 className="w-3 h-3" /> ACTIVE / READY FOR ENTRY
          </span>
        );
      case 'USED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-slate-700 text-slate-300 border border-slate-600">
            TURNSTILE SCANNED
          </span>
        );
      case 'CANCELLED':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">
            CANCELLED
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30">
            {status}
          </span>
        );
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <Navbar />

      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-8">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Link to="/fan" className="text-slate-400 hover:text-white transition">
                <ArrowLeft className="w-4 h-4" />
              </Link>
              <h1 className="text-2xl sm:text-3xl font-black text-white flex items-center gap-2.5">
                <Ticket className="w-7 h-7 text-amber-400" /> Digital Ticket Wallet
              </h1>
            </div>
            <p className="text-xs sm:text-sm text-slate-400">
              Your official, HMAC-SHA256 cryptographically signed digital stadium passes.
            </p>
          </div>

          <button
            onClick={fetchTickets}
            className="self-start sm:self-auto flex items-center gap-2 px-4 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-bold text-slate-300 transition"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Refresh Wallet
          </button>
        </div>

        {/* Content */}
        {loading ? (
          <div className="py-20 flex flex-col items-center justify-center">
            <div className="w-12 h-12 border-4 border-amber-500 border-t-transparent rounded-full animate-spin"></div>
            <p className="mt-4 text-sm text-slate-400">Loading your digital stadium passes...</p>
          </div>
        ) : error ? (
          <div className="p-6 rounded-2xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-center">
            <AlertCircle className="w-8 h-8 text-rose-400 mx-auto mb-2" />
            <p className="font-semibold">{error}</p>
          </div>
        ) : tickets.length === 0 ? (
          <div className="py-16 text-center rounded-2xl bg-slate-900/40 border border-slate-800 max-w-lg mx-auto">
            <Ticket className="w-16 h-16 text-slate-600 mx-auto mb-4" />
            <h3 className="text-lg font-bold text-white">No Tickets Found</h3>
            <p className="text-sm text-slate-400 mt-1 mb-6">
              You haven't booked any match tickets yet. Head over to the fixtures page to reserve seats!
            </p>
            <Link
              to="/fan"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl font-bold text-xs bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950"
            >
              Browse Live Fixtures
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {tickets.map((t, idx) => {
              const qrValue = t.qrCodeData || `IPL2026|${t.ticketNumber}|${t.matchId}|${t.seatNumber || 'GEN'}`;

              return (
                <div
                  key={t.id || t.ticketNumber || `ticket-${idx}`}
                  className="rounded-3xl bg-slate-900/90 border border-slate-800 shadow-2xl overflow-hidden flex flex-col justify-between relative group hover:border-slate-700 transition"
                >
                  {/* Top Header Card */}
                  <div className="p-5 bg-gradient-to-br from-slate-900 to-slate-950 border-b border-dashed border-slate-800 relative">
                    {/* Semi-circle notches for ticket realism */}
                    <div className="absolute -bottom-3 -left-3 w-6 h-6 rounded-full bg-slate-950 border-r border-slate-800" />
                    <div className="absolute -bottom-3 -right-3 w-6 h-6 rounded-full bg-slate-950 border-l border-slate-800" />

                    <div className="flex items-center justify-between mb-3">
                      <span className="text-[10px] font-mono font-bold text-amber-400 tracking-wider">
                        {t.ticketNumber}
                      </span>
                      {getStatusBadge(t.status)}
                    </div>

                    <h3 className="text-base font-black text-white">
                      IPL Match #{t.matchId || 1}
                    </h3>
                    <p className="text-xs text-slate-400 mt-0.5">
                      Order Reference: <span className="font-mono text-slate-300">#{t.orderNumber}</span>
                    </p>

                    {/* Seat Details Grid */}
                    <div className="mt-4 grid grid-cols-3 gap-2 p-3 rounded-2xl bg-slate-950/70 border border-slate-800/80 text-center">
                      <div>
                        <div className="text-[10px] text-slate-500 uppercase font-semibold">Category</div>
                        <div className="text-xs font-black text-amber-300 line-clamp-1">{t.categoryName || 'General'}</div>
                      </div>
                      <div>
                        <div className="text-[10px] text-slate-500 uppercase font-semibold">Stand / Sec</div>
                        <div className="text-xs font-black text-white">{t.stand || 'East'} - {t.section || 'A'}</div>
                      </div>
                      <div>
                        <div className="text-[10px] text-slate-500 uppercase font-semibold">Seat No</div>
                        <div className="text-xs font-black text-white">{t.seatNumber || 'A-12'}</div>
                      </div>
                    </div>
                  </div>

                  {/* QR Code Presentation */}
                  <div className="p-6 flex flex-col items-center justify-center bg-slate-950/40">
                    <div className="p-3 bg-white rounded-2xl shadow-xl flex items-center justify-center min-w-[176px] min-h-[176px]">
                      <QRCodeSVG
                        value={qrValue}
                        size={152}
                        level="M"
                        includeMargin={false}
                      />
                    </div>

                    <div className="mt-4 flex flex-col items-center gap-2">
                      <div className="flex items-center gap-1.5 text-[11px] text-emerald-400 font-semibold">
                        <ShieldCheck className="w-4 h-4 text-emerald-400" />
                        <span>HMAC-SHA256 Cryptographic Pass</span>
                      </div>
                      {qrValue && (
                        <button
                          type="button"
                          onClick={() => {
                            navigator.clipboard.writeText(qrValue);
                            alert("QR Pass Token copied! You can now paste it into the Turnstile Scanner.");
                          }}
                          className="px-3 py-1 rounded-lg text-[10px] font-bold bg-slate-800 hover:bg-slate-700 text-amber-400 border border-slate-700 transition cursor-pointer"
                        >
                          📋 Copy Gate QR Token
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Turnstile Gate Footer */}
                  <div className="p-4 bg-slate-950 border-t border-slate-800/80 flex items-center justify-between text-xs">
                    <div>
                      <span className="text-slate-500">Gate Entry:</span>
                      <span className="font-bold text-white ml-1">
                        {t.turnstileGate || 'GATE 4 - TURNSTILE A'}
                      </span>
                    </div>
                    <div className="text-amber-400 font-bold">
                      ₹{t.price}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
};

