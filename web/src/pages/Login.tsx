import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Radio, Shield, Lock, Mail, AlertTriangle, KeyRound } from 'lucide-react';
import { Button } from '../components/ui/Button';

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const [email, setEmail] = useState('admin@blackout.ops');
  const [password, setPassword] = useState('Blackout2026!');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const from = (location.state as any)?.from?.pathname || '/dashboard';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMessage('Please provide both authorized email address and password.');
      return;
    }

    try {
      setIsLoading(true);
      setErrorMessage(null);
      const res = await login(email, password);
      if (!res.success) {
        throw new Error(res.error || 'Authentication failed');
      }
      navigate(from, { replace: true });
    } catch (err: any) {
      console.error('Login failed:', err);
      setErrorMessage(err.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleFillCredentials = (fillEmail: string, fillPass: string) => {
    setEmail(fillEmail);
    setPassword(fillPass);
    setErrorMessage(null);
  };

  return (
    <div className="min-h-screen bg-operations-bg flex flex-col justify-center items-center px-4 select-none relative">
      {/* Background Grid Pattern */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#1E293B_1px,transparent_1px),linear-gradient(to_bottom,#1E293B_1px,transparent_1px)] bg-[size:4rem_4rem] [mask-image:radial-gradient(ellipse_60%_50%_at_50%_50%,#000_70%,transparent_100%)] opacity-20 pointer-events-none" />

      <div className="w-full max-w-md bg-operations-surface border border-operations-border rounded-lg shadow-2xl p-6 sm:p-8 relative z-10">
        {/* Header Branding */}
        <div className="flex flex-col items-center text-center mb-6">
          <div className="w-14 h-14 rounded-full bg-operations-card border border-operations-red/40 flex items-center justify-center text-operations-red mb-3 shadow-lg shadow-operations-red/20">
            <Radio className="w-7 h-7 text-operations-red animate-pulse" />
          </div>
          <h1 className="text-2xl font-black font-mono tracking-widest text-white">
            BLACKOUT
          </h1>
          <p className="text-xs font-mono text-operations-cyan tracking-wider uppercase mt-1">
            Government Operations Command Portal
          </p>
          <p className="text-[11px] text-operations-muted mt-1">
            Restricted System • Authorized Emergency Management Personnel Only
          </p>
        </div>

        {/* Error Alert */}
        {errorMessage && (
          <div className="mb-4 p-3 rounded bg-operations-red/10 border border-operations-red/30 flex items-start space-x-2 text-operations-red text-xs font-mono">
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
              Authorized Email
            </label>
            <div className="relative">
              <Mail className="w-4 h-4 text-operations-muted absolute left-3 top-3" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@blackout.ops"
                className="w-full bg-operations-bg border border-operations-border rounded p-2.5 pl-10 text-xs font-mono text-white placeholder-slate-600 focus:outline-none focus:border-operations-cyan transition-colors"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
              Security Passcode
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-operations-muted absolute left-3 top-3" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••••••"
                className="w-full bg-operations-bg border border-operations-border rounded p-2.5 pl-10 text-xs font-mono text-white placeholder-slate-600 focus:outline-none focus:border-operations-cyan transition-colors"
              />
            </div>
          </div>

          <Button
            type="submit"
            isLoading={isLoading}
            variant="primary"
            className="w-full py-3 mt-4"
          >
            Authenticate Command Session
          </Button>
        </form>

        {/* Quick-Access Demo Credentials Card */}
        <div className="mt-6 p-3 rounded bg-operations-bg border border-operations-border space-y-2 font-mono text-[11px]">
          <div className="flex items-center space-x-1.5 text-operations-cyan font-bold">
            <KeyRound className="w-3.5 h-3.5" />
            <span>COMMAND CENTER CREDENTIALS:</span>
          </div>

          <div className="grid grid-cols-2 gap-2 pt-1">
            <button
              type="button"
              onClick={() => handleFillCredentials('admin@blackout.ops', 'Blackout2026!')}
              className="p-2 rounded bg-operations-card border border-operations-border hover:border-operations-cyan/60 text-left transition-all"
            >
              <div className="text-[10px] text-operations-cyan font-bold">SUPER_ADMIN</div>
              <div className="text-[10px] text-white truncate">admin@blackout.ops</div>
              <div className="text-[9px] text-operations-muted">Blackout2026!</div>
            </button>

            <button
              type="button"
              onClick={() => handleFillCredentials('operator@blackout.ops', 'Operator2026!')}
              className="p-2 rounded bg-operations-card border border-operations-border hover:border-operations-cyan/60 text-left transition-all"
            >
              <div className="text-[10px] text-operations-amber font-bold">OPERATOR</div>
              <div className="text-[10px] text-white truncate">operator@blackout.ops</div>
              <div className="text-[9px] text-operations-muted">Operator2026!</div>
            </button>
          </div>
        </div>

        {/* Security Notice */}
        <div className="mt-6 pt-3 border-t border-operations-border/60 text-center">
          <div className="flex items-center justify-center space-x-1.5 text-[11px] font-mono text-operations-muted">
            <Shield className="w-3.5 h-3.5 text-operations-green" />
            <span>Encrypted Session • Immutable Audit Trail Enforced</span>
          </div>
        </div>
      </div>
    </div>
  );
};
