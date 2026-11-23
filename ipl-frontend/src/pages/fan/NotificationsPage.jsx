import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { 
  Bell, CheckCheck, Sparkles, Ticket, AlertTriangle, 
  Info, Clock, RefreshCw, CheckCircle2, Sliders, Calendar, Shield
} from 'lucide-react';
import { notificationApi } from '../../api/notificationApi';
import { useNotifications } from '../../context/NotificationContext';
import { useAuth } from '../../context/AuthContext';
import { Navbar } from '../../components/common/Navbar';

export const NotificationsPage = () => {
  const { role } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('ALL'); // 'ALL' | 'UNREAD'
  const { markAsRead, markAllAsRead, refreshUnreadCount } = useNotifications();

  useEffect(() => {
    fetchNotifications();
  }, []);

  const fetchNotifications = async () => {
    try {
      setLoading(true);
      setError('');
      const res = await notificationApi.getMyNotifications();
      const list = res?.data || res || [];
      setNotifications(Array.isArray(list) ? list : []);
    } catch (err) {
      setError('Failed to fetch notifications: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleMarkSingle = async (id) => {
    await markAsRead(id);
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, isRead: true } : n))
    );
  };

  const handleMarkAll = async () => {
    await markAllAsRead();
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    refreshUnreadCount();
  };

  const getNotificationIcon = (type) => {
    switch (type) {
      case 'ORDER_CONFIRMED':
        return <Ticket className="w-5 h-5 text-emerald-400" />;
      case 'MATCH_CONFIGURED':
        return <Sliders className="w-5 h-5 text-amber-400" />;
      case 'MATCH_SCHEDULED':
        return <Calendar className="w-5 h-5 text-blue-400" />;
      case 'MATCH_APPROVED':
      case 'TICKETS_OPENED':
        return <CheckCircle2 className="w-5 h-5 text-emerald-400" />;
      case 'FLASH_SALE_ALERT':
        return <Sparkles className="w-5 h-5 text-amber-400" />;
      case 'ORDER_CANCELLED':
      case 'TICKET_RELEASED':
        return <AlertTriangle className="w-5 h-5 text-rose-400" />;
      case 'ADMIN_ANNOUNCEMENT':
      case 'BROADCAST':
      case 'ANNOUNCEMENT':
        return <Bell className="w-5 h-5 text-blue-400" />;
      default:
        return <Info className="w-5 h-5 text-slate-400" />;
    }
  };

  const filteredList = notifications.filter((n) => {
    const read = n.isRead ?? n.read ?? false;
    if (filter === 'UNREAD') return !read;
    return true;
  });

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <Navbar />

      <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
          <div>
            <h1 className="text-2xl sm:text-3xl font-black text-white flex items-center gap-2.5">
              <Bell className="w-7 h-7 text-amber-400" /> Notifications & Alerts
            </h1>
            <p className="text-xs sm:text-sm text-slate-400 mt-0.5">
              Real-time events published via Apache Kafka to your ticketing inbox.
            </p>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleMarkAll}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-xs font-semibold text-slate-300 transition"
            >
              <CheckCheck className="w-4 h-4 text-emerald-400" /> Mark All Read
            </button>
            <button
              onClick={fetchNotifications}
              className="p-2 rounded-xl bg-slate-900 hover:bg-slate-800 border border-slate-800 text-slate-400 hover:text-white transition"
              title="Refresh notifications"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Filter Tabs */}
        <div className="flex gap-2 mb-6 border-b border-slate-800 pb-3">
          <button
            onClick={() => setFilter('ALL')}
            className={`px-4 py-1.5 rounded-xl text-xs font-bold transition ${
              filter === 'ALL'
                ? 'bg-amber-500 text-slate-950'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            All ({notifications.length})
          </button>
          <button
            onClick={() => setFilter('UNREAD')}
            className={`px-4 py-1.5 rounded-xl text-xs font-bold transition ${
              filter === 'UNREAD'
                ? 'bg-amber-500 text-slate-950'
                : 'text-slate-400 hover:text-white'
            }`}
          >
            Unread ({notifications.filter((n) => !n.isRead).length})
          </button>
        </div>

        {/* List */}
        {loading ? (
          <div className="py-20 flex flex-col items-center justify-center">
            <div className="w-10 h-10 border-3 border-amber-500 border-t-transparent rounded-full animate-spin"></div>
            <p className="mt-3 text-xs text-slate-400">Loading your notifications...</p>
          </div>
        ) : error ? (
          <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs">
            {error}
          </div>
        ) : filteredList.length === 0 ? (
          <div className="py-16 text-center rounded-2xl bg-slate-900/40 border border-slate-800">
            <Bell className="w-12 h-12 text-slate-600 mx-auto mb-3" />
            <p className="text-base font-bold text-slate-300">No notifications to display</p>
            <p className="text-xs text-slate-500 mt-1">
              Any match alerts, order confirmations, or broadcast bulletins will appear here.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {filteredList.map((item) => {
              const read = item.isRead ?? item.read ?? false;
              const dateStr = item.createdAt
                ? new Date(item.createdAt).toLocaleString('en-IN', {
                    dateStyle: 'medium',
                    timeStyle: 'short',
                  })
                : 'Just now';

              return (
                <div
                  key={item.id}
                  className={`p-4 rounded-2xl border transition flex items-start justify-between gap-4 ${
                    read
                      ? 'bg-slate-900/40 border-slate-800/80 text-slate-400'
                      : 'bg-slate-900 border-amber-500/40 text-slate-200 shadow-md shadow-amber-500/5'
                  }`}
                >
                  <div className="flex items-start gap-3.5">
                    <div className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 shrink-0 mt-0.5">
                      {getNotificationIcon(item.type)}
                    </div>
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-xs font-bold text-white">
                          {item.title || item.type?.replace(/_/g, ' ') || 'Alert'}
                        </span>
                        {!item.isRead && (
                          <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse" />
                        )}
                      </div>
                      <p className="text-xs text-slate-300 leading-relaxed">{item.message}</p>

                      {/* Action Links */}
                      {item.type === 'MATCH_CONFIGURED' && role === 'ADMIN' && (
                        <div className="mt-2.5">
                          <Link
                            to="/admin"
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold transition shadow"
                          >
                            <Shield className="w-3.5 h-3.5" /> Review & Approve Match
                          </Link>
                        </div>
                      )}

                      {item.type === 'MATCH_SCHEDULED' && role === 'FRANCHISE' && (
                        <div className="mt-2.5">
                          <Link
                            to="/franchise"
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 text-xs font-bold transition shadow"
                          >
                            <Sliders className="w-3.5 h-3.5" /> Configure Venue Pricing
                          </Link>
                        </div>
                      )}

                      {(item.type === 'MATCH_APPROVED' || item.type === 'TICKETS_OPENED') && role === 'USER' && (
                        <div className="mt-2.5">
                          <Link
                            to="/fan"
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-gradient-to-r from-amber-400 to-orange-500 text-slate-950 text-xs font-bold transition shadow"
                          >
                            <Ticket className="w-3.5 h-3.5" /> Book Match Seats
                          </Link>
                        </div>
                      )}

                      <div className="flex items-center gap-1.5 mt-2.5 text-[10px] text-slate-500">
                        <Clock className="w-3 h-3" />
                        <span>{dateStr}</span>
                      </div>
                    </div>
                  </div>

                  {!item.isRead && (
                    <button
                      onClick={() => handleMarkSingle(item.id)}
                      className="shrink-0 p-2 text-slate-400 hover:text-amber-400 hover:bg-slate-800 rounded-lg text-xs transition"
                      title="Mark as read"
                    >
                      <CheckCircle2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
};

