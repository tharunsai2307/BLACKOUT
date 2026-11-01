// BLACKOUT Emergency Operations Portal
// Dispatch Emergency SMS Modal (DLT-Compliant & Consent-Gated)

import React, { useState, useEffect } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { EmergencyAlert } from '../../types/database';
import { smsService, SmsScenario, RecipientConsentRecord } from '../../services/smsService';
import { MessageSquare, AlertTriangle, ShieldCheck, CheckCircle2, RefreshCw } from 'lucide-react';

interface DispatchSmsModalProps {
  alert: EmergencyAlert;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const DispatchSmsModal: React.FC<DispatchSmsModalProps> = ({
  alert,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [scenario, setScenario] = useState<SmsScenario>('EMERGENCY_CONTACT_ALERT');
  const [recipientPhone, setRecipientPhone] = useState('');
  const [templateId, setTemplateId] = useState('EMERGENCY_CONTACT_SOS');
  const [consentRecord, setConsentRecord] = useState<RecipientConsentRecord | null>(null);
  const [isVerifyingConsent, setIsVerifyingConsent] = useState(false);
  const [grantConsentManually, setGrantConsentManually] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successResult, setSuccessResult] = useState<any | null>(null);

  // Template parameters generated from incident telemetry
  const coordsFormatted = alert.latitude && alert.longitude
    ? `${alert.latitude.toFixed(4)}, ${alert.longitude.toFixed(4)}`
    : 'UNKNOWN GPS';

  const previewText = scenario === 'EMERGENCY_CONTACT_ALERT'
    ? `BLACKOUT ALERT: Your emergency contact reported an SOS (${alert.category}) at GPS ${coordsFormatted}. Stand by for official rescue updates. Ref: ${alert.message_id.slice(0, 8)}`
    : scenario === 'OPERATOR_INCIDENT_DISPATCH'
    ? `BLACKOUT DISPATCH: Response team assigned to Incident #${alert.message_id.slice(0, 8)} (${alert.category}) at ${coordsFormatted}. Priority: ${alert.priority}.`
    : `CIVIL DEFENSE WARNING: Immediate hazard in area. Stand by. Ref: ${alert.message_id.slice(0, 8)}`;

  // Lookup consent whenever recipient phone changes
  useEffect(() => {
    const cleanPhone = recipientPhone.trim();
    if (/^\+[1-9]\d{6,14}$/.test(cleanPhone)) {
      setIsVerifyingConsent(true);
      smsService.getRecipientConsent(cleanPhone)
        .then((res) => {
          setConsentRecord(res);
          setGrantConsentManually(false);
        })
        .finally(() => setIsVerifyingConsent(false));
    } else {
      setConsentRecord(null);
    }
  }, [recipientPhone]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccessResult(null);

    const cleanPhone = recipientPhone.trim();
    if (!/^\+[1-9]\d{6,14}$/.test(cleanPhone)) {
      setError('Please provide a valid E.164 phone number (e.g. +919876543210).');
      return;
    }

    try {
      setIsSubmitting(true);

      // If operator manually granted consent
      if (grantConsentManually && (!consentRecord || consentRecord.verification_status !== 'VERIFIED')) {
        const consentRes = await smsService.recordRecipientConsent(
          cleanPhone,
          true,
          scenario === 'PUBLIC_WARNING_BROADCAST',
          'OPERATOR_VERIFIED'
        );
        if (!consentRes.success) {
          throw new Error(`Failed to log recipient consent: ${consentRes.error}`);
        }
      }

      const res = await smsService.dispatchEmergencySms({
        scenario,
        recipientPhone: cleanPhone,
        templateId,
        templateParameters: {
          name: 'Civilian Contact',
          category: alert.category,
          coordinates: coordsFormatted,
          refId: alert.message_id.slice(0, 8),
          incidentId: alert.message_id.slice(0, 8),
          priority: alert.priority,
          teamName: alert.assigned_team?.name || 'Search & Rescue Unit',
        },
        incidentId: alert.id,
        idempotencyKey: `sms-${alert.id}-${cleanPhone}-${Date.now()}`,
      });

      if (!res.success) {
        throw new Error(res.error || 'SMS Dispatch Rejected by Gateway');
      }

      setSuccessResult(res);
      setTimeout(() => {
        onSuccess();
        onClose();
      }, 2000);
    } catch (err: any) {
      setError(err.message || 'Failed to dispatch SMS notification.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const hasConsent = Boolean(
    consentRecord && (
      (scenario === 'EMERGENCY_CONTACT_ALERT' && consentRecord.has_emergency_contact_consent) ||
      (scenario === 'PUBLIC_WARNING_BROADCAST' && consentRecord.has_public_warning_consent) ||
      consentRecord.verification_status === 'VERIFIED'
    )
  );

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Dispatch Emergency SMS Notification">
      <form onSubmit={handleSubmit} className="space-y-4 font-mono text-xs">
        {/* Scenario Selection */}
        <div>
          <label className="block text-[11px] text-operations-muted mb-1 uppercase font-bold">
            Notification Scenario
          </label>
          <select
            value={scenario}
            onChange={(e) => {
              const sc = e.target.value as SmsScenario;
              setScenario(sc);
              if (sc === 'EMERGENCY_CONTACT_ALERT') setTemplateId('EMERGENCY_CONTACT_SOS');
              else if (sc === 'OPERATOR_INCIDENT_DISPATCH') setTemplateId('OPERATOR_FIELD_DISPATCH');
              else setTemplateId('PUBLIC_SAFETY_WARNING');
            }}
            className="w-full bg-operations-bg border border-operations-border rounded p-2 text-white focus:outline-none focus:border-operations-cyan"
          >
            <option value="EMERGENCY_CONTACT_ALERT">Emergency Contact SOS Notification</option>
            <option value="OPERATOR_INCIDENT_DISPATCH">Field Response Team Dispatch SMS</option>
            <option value="PUBLIC_WARNING_BROADCAST">Opted-In Public Warning Alert</option>
          </select>
        </div>

        {/* Recipient Phone (E.164 format) */}
        <div>
          <label className="block text-[11px] text-operations-muted mb-1 uppercase font-bold">
            Recipient Mobile Number (E.164 Format)
          </label>
          <div className="relative">
            <input
              type="tel"
              placeholder="+919876543210"
              value={recipientPhone}
              onChange={(e) => setRecipientPhone(e.target.value)}
              required
              className="w-full bg-operations-bg border border-operations-border rounded p-2 text-white placeholder-slate-600 focus:outline-none focus:border-operations-cyan"
            />
            {isVerifyingConsent && (
              <RefreshCw className="w-3.5 h-3.5 animate-spin absolute right-3 top-2.5 text-operations-cyan" />
            )}
          </div>
          <p className="text-[10px] text-operations-muted mt-1">
            Standard E.164 format required with country code (e.g., +91 for India, +1 for US).
          </p>
        </div>

        {/* Consent Status Check & Verification */}
        {recipientPhone.trim().length > 6 && (
          <div className="p-3 rounded bg-operations-surface border border-operations-border space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] uppercase font-bold text-white flex items-center">
                {hasConsent ? (
                  <ShieldCheck className="w-4 h-4 text-operations-green mr-1.5" />
                ) : (
                  <AlertTriangle className="w-4 h-4 text-operations-amber mr-1.5" />
                )}
                Consent Verification Status
              </span>
              <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                hasConsent ? 'bg-operations-green/20 text-operations-green' : 'bg-operations-amber/20 text-operations-amber'
              }`}>
                {hasConsent ? 'VERIFIED' : 'CONSENT UNVERIFIED'}
              </span>
            </div>

            {!hasConsent && (
              <div className="space-y-2 pt-1 border-t border-operations-border/60">
                <p className="text-[10px] text-operations-muted">
                  Recipient has not registered opt-in consent for emergency alerts. Under TRAI/DLT and telecom regulations, recipient consent must be established.
                </p>
                <label className="flex items-start space-x-2 cursor-pointer text-[11px] text-white">
                  <input
                    type="checkbox"
                    checked={grantConsentManually}
                    onChange={(e) => setGrantConsentManually(e.target.checked)}
                    className="mt-0.5 rounded border-operations-border bg-operations-bg text-operations-cyan focus:ring-0"
                  />
                  <span>
                    Confirm bona fide emergency contact verified by control room operator (Logs operator ID to audit trail).
                  </span>
                </label>
              </div>
            )}
          </div>
        )}

        {/* Rendered Template Preview */}
        <div>
          <label className="block text-[11px] text-operations-muted mb-1 uppercase font-bold">
            DLT-Approved Template Preview ({previewText.length} chars)
          </label>
          <div className="p-3 rounded bg-operations-bg border border-operations-border text-[11px] text-slate-200 leading-relaxed font-sans">
            {previewText}
          </div>
          <span className="text-[10px] text-operations-cyan mt-1 block">
            Approved DLT Header: <strong>BLKOUT</strong> | Single SMS Segment (&lt;160 chars)
          </span>
        </div>

        {/* Error / Success Feedback */}
        {error && (
          <div className="p-3 rounded bg-operations-red/10 border border-operations-red/30 text-operations-red text-xs">
            {error}
          </div>
        )}

        {successResult && (
          <div className="p-3 rounded bg-operations-green/10 border border-operations-green/30 text-operations-green text-xs flex items-center space-x-2">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
            <span>
              SMS Submitted Successfully! Status: <strong>{successResult.status}</strong> (Ref: {successResult.providerReference || 'MOCK'})
            </span>
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-end space-x-2 pt-3 border-t border-operations-border">
          <Button variant="ghost" size="sm" type="button" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            type="submit"
            disabled={isSubmitting || (!hasConsent && !grantConsentManually)}
          >
            {isSubmitting ? (
              <>
                <RefreshCw className="w-3.5 h-3.5 animate-spin mr-1.5" />
                Dispatching...
              </>
            ) : (
              <>
                <MessageSquare className="w-3.5 h-3.5 mr-1.5" />
                Authorize & Send SMS
              </>
            )}
          </Button>
        </div>
      </form>
    </Modal>
  );
};

