import React, { useState, useEffect } from 'react';
import { warningService } from '../services/warningService';
import { signingService, ROOT_KEY_ID, ROOT_PUBLIC_KEY_BASE64, verifySignatureInBrowser, WarningRevision } from '../services/signingService';
import { PublicWarning, WarningSeverity, WarningStatus } from '../types/database';
import { useAuth } from '../hooks/useAuth';
import { Card } from '../components/ui/Card';
import { Badge } from '../components/ui/Badge';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { formatDateTime, formatRelativeTime } from '../utils/formatters';
import {
  Plus,
  RefreshCw,
  Send,
  CheckCircle,
  Ban,
  ShieldCheck,
  ShieldAlert,
  History,
  MapPin,
  Key,
  AlertTriangle,
} from 'lucide-react';

export const PublicWarnings: React.FC = () => {
  const { user, profile, canPublishWarnings, isSuperAdmin } = useAuth();
  const [warnings, setWarnings] = useState<PublicWarning[]>([]);
  const [statusFilter, setStatusFilter] = useState<WarningStatus | ''>('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Verification status cache: warningId -> boolean
  const [verificationMap, setVerificationMap] = useState<Record<string, boolean>>({});

  // Create / Edit Warning Modal
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('FLOOD');
  const [severity, setSeverity] = useState<WarningSeverity>('SEVERE');
  const [instructions, setInstructions] = useState('');
  const [affectedArea, setAffectedArea] = useState('');
  const [latitude, setLatitude] = useState<number | ''>(13.0827);
  const [longitude, setLongitude] = useState<number | ''>(80.2707);
  const [radiusKm, setRadiusKm] = useState<number | ''>(5.0);
  const [durationHours, setDurationHours] = useState(24);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Preview & Sign Modal
  const [previewWarning, setPreviewWarning] = useState<PublicWarning | null>(null);
  const [isSigning, setIsSigning] = useState(false);

  // Revision History Modal
  const [revisionHistoryWarning, setRevisionHistoryWarning] = useState<PublicWarning | null>(null);
  const [revisions, setRevisions] = useState<WarningRevision[]>([]);
  const [isLoadingRevisions, setIsLoadingRevisions] = useState(false);

  const loadWarnings = async () => {
    try {
      setIsLoading(true);
      setError(null);
      const res = await warningService.getWarnings(statusFilter || undefined);
      if (res.error) throw new Error(res.error);
      const list = res.data;
      setWarnings(list);

      // Verify signatures of published warnings in browser
      const verMap: Record<string, boolean> = {};
      for (const w of list) {
        if (w.status === 'PUBLISHED' && (w as any).signature && (w as any).canonical_payload) {
          const valid = await verifySignatureInBrowser(
            ROOT_PUBLIC_KEY_BASE64,
            (w as any).canonical_payload,
            (w as any).signature
          );
          verMap[w.id] = valid;
        }
      }
      setVerificationMap(verMap);
    } catch (err: any) {
      console.error('Failed to load warnings:', err);
      setError(err.message || 'Error querying public warnings.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadWarnings();
  }, [statusFilter]);

  const handleCreateWarning = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !title.trim() || !description.trim()) return;

    try {
      setIsSubmitting(true);
      const actor = {
        id: user.id,
        email: user.email || 'operator@blackout.ops',
        role: profile?.role || 'CONTROL_ROOM_OPERATOR',
      };

      const expiresAt = new Date(Date.now() + durationHours * 3600 * 1000).toISOString();

      const res = await warningService.createWarning({
        title: title.trim(),
        description: description.trim(),
        category,
        severity,
        affectedArea: affectedArea.trim() || 'General District',
        latitude: typeof latitude === 'number' ? latitude : null,
        longitude: typeof longitude === 'number' ? longitude : null,
        radiusKm: typeof radiusKm === 'number' ? radiusKm : null,
        instructions: instructions.trim() || undefined,
        expiresAt,
        actor,
      });

      if (res.error) throw new Error(res.error);

      setIsCreateOpen(false);
      resetForm();
      loadWarnings();
    } catch (err: any) {
      alert(`Failed to create warning: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const resetForm = () => {
    setTitle('');
    setDescription('');
    setCategory('FLOOD');
    setSeverity('SEVERE');
    setInstructions('');
    setAffectedArea('');
    setLatitude(13.0827);
    setLongitude(80.2707);
    setRadiusKm(5.0);
    setDurationHours(24);
  };

  const handleSignAndPublish = async () => {
    if (!previewWarning || !user) return;
    try {
      setIsSigning(true);
      const actor = {
        id: user.id,
        email: user.email || 'publisher@blackout.ops',
        role: profile?.role || 'PUBLIC_WARNING_PUBLISHER',
      };

      const res = await signingService.signAndPublishWarning(previewWarning.id, actor);
      if (res.error) throw new Error(res.error);

      setPreviewWarning(null);
      loadWarnings();
    } catch (err: any) {
      alert(`Signing and publishing failed: ${err.message}`);
    } finally {
      setIsSigning(false);
    }
  };

  const handleOpenRevisions = async (warning: PublicWarning) => {
    setRevisionHistoryWarning(warning);
    setIsLoadingRevisions(true);
    const res = await signingService.getWarningRevisions(warning.id);
    setRevisions(res.data);
    setIsLoadingRevisions(false);
  };

  const handleWorkflowAction = async (warning: PublicWarning, action: 'submit' | 'approve' | 'sign_publish' | 'revoke') => {
    if (!user) return;
    const actor = {
      id: user.id,
      email: user.email || 'operator@blackout.ops',
      role: profile?.role || 'CONTROL_ROOM_OPERATOR',
    };

    try {
      if (action === 'submit') {
        const res = await warningService.submitForApproval(warning.id, actor);
        if (res.error) throw new Error(res.error);
        loadWarnings();
      } else if (action === 'approve') {
        const res = await warningService.approveWarning(warning.id, actor);
        if (res.error) throw new Error(res.error);
        loadWarnings();
      } else if (action === 'sign_publish') {
        // Open preview & confirmation dialog before cryptographic signing
        setPreviewWarning(warning);
      } else if (action === 'revoke') {
        const reason = window.prompt('Specify justification for revoking this broadcast:');
        if (!reason || !reason.trim()) return;
        const res = await signingService.revokeWarning(warning.id, reason.trim(), actor);
        if (res.error) throw new Error(res.error);
        loadWarnings();
      }
    } catch (err: any) {
      alert(`Action failed: ${err.message}`);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-operations-border/60">
        <div>
          <h1 className="text-xl font-bold font-mono tracking-tight text-white flex items-center space-x-2">
            <span>PUBLIC EMERGENCY WARNINGS</span>
            <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30 flex items-center gap-1">
              <ShieldCheck className="w-3 h-3 text-operations-cyan" />
              DIGITALLY SIGNED ED25519
            </span>
          </h1>
          <p className="text-xs text-operations-muted font-mono mt-0.5">
            Authorised emergency broadcast distribution, digital signature workflow, and offline multi-hop relay gateway
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as WarningStatus)}
            className="bg-operations-surface border border-operations-border rounded px-2.5 py-1.5 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
          >
            <option value="">All Workflow States</option>
            <option value="DRAFT">Drafts</option>
            <option value="SUBMITTED_FOR_APPROVAL">Pending Approval</option>
            <option value="APPROVED">Approved (Ready to Sign)</option>
            <option value="PUBLISHED">Active Signed Broadcasts</option>
            <option value="REVOKED">Revoked</option>
          </select>

          <Button variant="ghost" size="sm" onClick={loadWarnings} disabled={isLoading}>
            <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
          </Button>

          <Button variant="primary" size="sm" onClick={() => setIsCreateOpen(true)}>
            <Plus className="w-3.5 h-3.5 mr-1.5" />
            Create Warning
          </Button>
        </div>
      </div>

      {error && (
        <div className="p-4 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs font-mono flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Warnings List */}
      <div className="space-y-4">
        {isLoading ? (
          <div className="py-12 text-center text-xs font-mono text-operations-muted animate-pulse">
            Querying Broadcast Records and Key Registry...
          </div>
        ) : warnings.length === 0 ? (
          <div className="p-8 text-center text-xs font-mono text-operations-muted bg-operations-surface border border-operations-border rounded">
            No public warnings match current query.
          </div>
        ) : (
          warnings.map((warning) => {
            const isVerified = verificationMap[warning.id] === true;
            const revNumber = (warning as any).revision || 1;
            const keyId = (warning as any).signing_key_id || ROOT_KEY_ID;

            return (
              <Card key={warning.id} className="space-y-3 border-l-4 border-l-operations-cyan">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={warning.severity} />
                    <Badge variant={warning.status} />
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-slate-300 border border-operations-border">
                      Code: {warning.warning_code}
                    </span>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-operations-card text-operations-cyan border border-operations-cyan/30">
                      Revision {revNumber}
                    </span>
                    {warning.status === 'PUBLISHED' && (
                      <span
                        className={`text-[10px] font-mono px-2 py-0.5 rounded flex items-center gap-1 border ${
                          isVerified
                            ? 'bg-emerald-950/60 text-emerald-400 border-emerald-500/40'
                            : 'bg-amber-950/60 text-amber-400 border-amber-500/40'
                        }`}
                      >
                        {isVerified ? (
                          <>
                            <ShieldCheck className="w-3 h-3 text-emerald-400" />
                            AUTHENTIC SIGNATURE (Ed25519)
                          </>
                        ) : (
                          <>
                            <ShieldAlert className="w-3 h-3 text-amber-400" />
                            SIGNATURE VERIFYING...
                          </>
                        )}
                      </span>
                    )}
                  </div>
                  <div className="text-[10px] font-mono text-operations-muted">
                    Created {formatRelativeTime(warning.created_at)}
                  </div>
                </div>

                <div>
                  <h3 className="text-sm font-bold font-mono text-white">{warning.title}</h3>
                  <p className="text-xs font-mono text-slate-300 mt-1 whitespace-pre-wrap">
                    {warning.description}
                  </p>
                </div>

                {warning.instructions && (
                  <div className="p-2.5 rounded bg-operations-bg border border-operations-border text-xs font-mono space-y-0.5">
                    <span className="text-[10px] font-bold text-operations-cyan uppercase">
                      Civilian Safety Directives:
                    </span>
                    <p className="text-white">{warning.instructions}</p>
                  </div>
                )}

                {/* Geographic & Signing Metadata Bar */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-1 text-[10px] font-mono text-slate-400">
                  <div className="flex items-center gap-1">
                    <MapPin className="w-3.5 h-3.5 text-operations-cyan shrink-0" />
                    <span>Target Area: <strong className="text-white">{warning.affected_area}</strong></span>
                    {warning.radius_km && <span>({warning.radius_km} km radius)</span>}
                  </div>
                  <div className="flex items-center gap-1">
                    <Key className="w-3.5 h-3.5 text-operations-muted shrink-0" />
                    <span>Key ID: <strong className="text-white">{keyId}</strong></span>
                  </div>
                  <div className="flex items-center gap-1">
                    <span>Authority: <strong className="text-white">{warning.issuing_authority}</strong></span>
                  </div>
                </div>

                {/* Workflow Actions */}
                <div className="pt-3 border-t border-operations-border flex flex-wrap items-center justify-between gap-2">
                  <div className="text-[10px] font-mono text-operations-muted flex items-center gap-3">
                    {warning.published_at && (
                      <span>Published: {formatDateTime(warning.published_at)}</span>
                    )}
                    <span>Expires: {formatDateTime(warning.expires_at)}</span>
                  </div>

                  <div className="flex items-center space-x-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleOpenRevisions(warning)}
                    >
                      <History className="w-3.5 h-3.5 mr-1" />
                      Revisions
                    </Button>

                    {warning.status === 'DRAFT' && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => handleWorkflowAction(warning, 'submit')}
                      >
                        <Send className="w-3.5 h-3.5 mr-1 text-operations-cyan" />
                        Submit for Approval
                      </Button>
                    )}

                    {warning.status === 'SUBMITTED_FOR_APPROVAL' && (canPublishWarnings() || isSuperAdmin()) && (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => handleWorkflowAction(warning, 'approve')}
                      >
                        <CheckCircle className="w-3.5 h-3.5 mr-1" />
                        Approve Warning
                      </Button>
                    )}

                    {warning.status === 'APPROVED' && (canPublishWarnings() || isSuperAdmin()) && (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => handleWorkflowAction(warning, 'sign_publish')}
                      >
                        <ShieldCheck className="w-3.5 h-3.5 mr-1 text-emerald-400" />
                        Sign & Broadcast
                      </Button>
                    )}

                    {warning.status === 'PUBLISHED' && (canPublishWarnings() || isSuperAdmin()) && (
                      <Button
                        variant="danger"
                        size="sm"
                        onClick={() => handleWorkflowAction(warning, 'revoke')}
                      >
                        <Ban className="w-3.5 h-3.5 mr-1" />
                        Revoke Broadcast
                      </Button>
                    )}
                  </div>
                </div>
              </Card>
            );
          })
        )}
      </div>

      {/* Preview & Sign Confirmation Modal */}
      {previewWarning && (
        <Modal
          isOpen={true}
          onClose={() => setPreviewWarning(null)}
          title="Cryptographic Signing & Broadcast Confirmation"
          maxWidth="lg"
        >
          <div className="space-y-4">
            <div className="p-3 bg-operations-bg border border-operations-cyan/40 rounded space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-mono font-bold text-white uppercase">{previewWarning.title}</span>
                <Badge variant={previewWarning.severity} />
              </div>
              <p className="text-xs font-mono text-slate-300">{previewWarning.description}</p>
              <div className="text-[10px] font-mono text-operations-muted pt-2 border-t border-operations-border flex flex-col gap-1">
                <div>Issuing Authority: <span className="text-white">{previewWarning.issuing_authority}</span></div>
                <div>Geographic Target: <span className="text-white">{previewWarning.affected_area}</span></div>
                <div>Signing Key ID: <span className="text-operations-cyan">{ROOT_KEY_ID}</span> (RFC 8032 Ed25519)</div>
              </div>
            </div>

            <div className="p-3 bg-amber-950/20 border border-amber-500/30 rounded text-xs font-mono text-amber-300 flex items-start gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
              <span>
                Once digitally signed, any modification to title, description, severity, timestamps or target area will cryptographically invalidate this warning across all receiving Android mesh devices.
              </span>
            </div>

            <div className="flex justify-end space-x-2 pt-2 border-t border-operations-border">
              <Button variant="ghost" onClick={() => setPreviewWarning(null)} disabled={isSigning}>
                Cancel
              </Button>
              <Button variant="primary" loading={isSigning} onClick={handleSignAndPublish}>
                <ShieldCheck className="w-3.5 h-3.5 mr-1.5" />
                Affix Digital Signature & Broadcast
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Revision History Modal */}
      {revisionHistoryWarning && (
        <Modal
          isOpen={true}
          onClose={() => setRevisionHistoryWarning(null)}
          title={`Revision History — ${revisionHistoryWarning.warning_code}`}
          maxWidth="lg"
        >
          <div className="space-y-4 max-h-[60vh] overflow-y-auto">
            {isLoadingRevisions ? (
              <div className="py-8 text-center text-xs font-mono text-operations-muted animate-pulse">
                Loading revision ledger...
              </div>
            ) : revisions.length === 0 ? (
              <div className="p-4 text-center text-xs font-mono text-operations-muted">
                No past revisions found for this broadcast.
              </div>
            ) : (
              revisions.map((rev) => (
                <div key={rev.id} className="p-3 bg-operations-bg border border-operations-border rounded space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-mono font-bold text-operations-cyan">
                      Revision {rev.revision} • {rev.status}
                    </span>
                    <span className="text-[10px] font-mono text-operations-muted">
                      {formatDateTime(rev.created_at)}
                    </span>
                  </div>
                  <h4 className="text-xs font-mono font-semibold text-white">{rev.title}</h4>
                  <p className="text-[11px] font-mono text-slate-300">{rev.description}</p>
                  <div className="text-[10px] font-mono text-slate-400 break-all bg-operations-card p-2 rounded border border-operations-border">
                    <div className="text-operations-muted mb-0.5 font-bold">Ed25519 Signature:</div>
                    <code>{rev.signature}</code>
                  </div>
                </div>
              ))
            )}
          </div>
        </Modal>
      )}

      {/* Create Warning Modal */}
      <Modal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        title="Draft Public Emergency Warning"
        maxWidth="lg"
      >
        <form onSubmit={handleCreateWarning} className="space-y-4">
          <div>
            <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
              Warning Headline / Title *
            </label>
            <input
              type="text"
              required
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="e.g. FLASH FLOOD EMERGENCY EVACUATION ORDER"
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan font-bold"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Category *
              </label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              >
                <option value="FLOOD">Flood</option>
                <option value="CYCLONE">Cyclone</option>
                <option value="HEAVY_RAINFALL">Heavy Rainfall</option>
                <option value="LANDSLIDE">Landslide</option>
                <option value="EARTHQUAKE">Earthquake</option>
                <option value="FIRE">Fire</option>
                <option value="EVACUATION">Evacuation</option>
                <option value="INFRASTRUCTURE_EMERGENCY">Infrastructure Emergency</option>
                <option value="GENERAL_PUBLIC_SAFETY">General Public Safety</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Severity Level *
              </label>
              <select
                value={severity}
                onChange={(e) => setSeverity(e.target.value as WarningSeverity)}
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              >
                <option value="IMMEDIATE THREAT">CRITICAL (Immediate Threat to Life)</option>
                <option value="SEVERE">SEVERE (Significant Hazard to Property & Life)</option>
                <option value="MODERATE">MODERATE (Potential Hazard)</option>
                <option value="ADVISORY">ADVISORY (Informational Civil Notice)</option>
              </select>
            </div>
          </div>

          <div>
            <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
              Detailed Description & Context *
            </label>
            <textarea
              required
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Detail specific hazard, river basin levels, expected impact timeframe..."
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
            />
          </div>

          <div>
            <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
              Protective Actions & Instructions
            </label>
            <textarea
              rows={2}
              value={instructions}
              onChange={(e) => setInstructions(e.target.value)}
              placeholder="e.g. Move immediately to higher ground. Do not drive through flooded roads."
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Affected Geographic Sector / Zone *
              </label>
              <input
                type="text"
                required
                value={affectedArea}
                onChange={(e) => setAffectedArea(e.target.value)}
                placeholder="e.g. Sector 4, Riverbank Basin"
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              />
            </div>

            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Broadcast Duration (Hours) *
              </label>
              <input
                type="number"
                min={1}
                max={168}
                value={durationHours}
                onChange={(e) => setDurationHours(parseInt(e.target.value) || 24)}
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Latitude (Optional)
              </label>
              <input
                type="number"
                step="0.000001"
                value={latitude}
                onChange={(e) => setLatitude(e.target.value === '' ? '' : parseFloat(e.target.value))}
                placeholder="13.082680"
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              />
            </div>
            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Longitude (Optional)
              </label>
              <input
                type="number"
                step="0.000001"
                value={longitude}
                onChange={(e) => setLongitude(e.target.value === '' ? '' : parseFloat(e.target.value))}
                placeholder="80.270718"
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              />
            </div>
            <div>
              <label className="block text-xs font-mono uppercase text-operations-muted mb-1 font-semibold">
                Radius (km)
              </label>
              <input
                type="number"
                step="0.1"
                value={radiusKm}
                onChange={(e) => setRadiusKm(e.target.value === '' ? '' : parseFloat(e.target.value))}
                placeholder="5.0"
                className="w-full bg-operations-bg border border-operations-border rounded p-2 text-xs font-mono text-white focus:outline-none focus:border-operations-cyan"
              />
            </div>
          </div>

          <div className="flex justify-end space-x-2 pt-2 border-t border-operations-border">
            <Button variant="ghost" type="button" onClick={() => setIsCreateOpen(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" loading={isSubmitting} variant="primary">
              Save Warning Draft
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
