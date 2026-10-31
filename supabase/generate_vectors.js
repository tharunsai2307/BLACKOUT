const crypto = require('crypto');

function buildCanonicalString(w) {
  const regions = (w.affectedRegions || []).map(r => r.trim()).sort().join(',');
  const latStr = w.latitude != null ? Number(w.latitude).toFixed(6) : 'null';
  const lonStr = w.longitude != null ? Number(w.longitude).toFixed(6) : 'null';
  const radStr = w.radiusMeters != null ? Number(w.radiusMeters).toFixed(2) : 'null';

  return [
    'BLACKOUT_WARNING_V1',
    `warningId=${(w.warningId || '').trim()}`,
    `protocolVersion=${w.protocolVersion || 1}`,
    `revision=${w.revision || 1}`,
    `status=${(w.status || '').trim().toUpperCase()}`,
    `category=${(w.category || '').trim().toUpperCase()}`,
    `severity=${(w.severity || '').trim().toUpperCase()}`,
    `issuingAuthority=${(w.issuingAuthority || '').trim()}`,
    `title=${(w.title || '').trim()}`,
    `description=${(w.description || '').trim()}`,
    `issuedAt=${w.issuedAt}`,
    `effectiveAt=${w.effectiveAt}`,
    `expiresAt=${w.expiresAt}`,
    `language=${(w.language || 'en').trim().toLowerCase()}`,
    `signingKeyId=${(w.signingKeyId || '').trim()}`,
    `signatureAlgorithm=${(w.signatureAlgorithm || 'Ed25519').trim()}`,
    `latitude=${latStr}`,
    `longitude=${lonStr}`,
    `radiusMeters=${radStr}`,
    `affectedRegions=${regions}`
  ].join('\n');
}

const rawPrivBase64 = 'Dzk1c03BCXfD5EAA/HWzf/tjiDaPGS6T05ZuBgS66LA=';
const privDer = Buffer.concat([
  Buffer.from('302e020100300506032b657004220420', 'hex'),
  Buffer.from(rawPrivBase64, 'base64')
]);
const privateKey = crypto.createPrivateKey({ key: privDer, format: 'der', type: 'pkcs8' });

// 1. Future Warning (Active)
const futureWarning = {
  warningId: 'd3b07384-d113-46fb-9b24-9b2bfa6eb542',
  protocolVersion: 1,
  revision: 1,
  status: 'PUBLISHED',
  category: 'FLOOD',
  severity: 'CRITICAL',
  issuingAuthority: 'BLACKOUT Civil Defense & Emergency Response Administration',
  title: 'Severe Flash Flooding Alert',
  description: 'Immediate flash flooding reported in low-lying areas. Move to higher ground immediately.',
  issuedAt: 1793400000000,
  effectiveAt: 1793400000000,
  expiresAt: 1850000000000,
  language: 'en',
  signingKeyId: 'KEY-BLACKOUT-ROOT-2026',
  signatureAlgorithm: 'Ed25519',
  latitude: 13.082680,
  longitude: 80.270718,
  radiusMeters: 5000.0,
  affectedRegions: ['Sector 4', 'Central Basin', 'North Riverbank']
};
const sig1 = crypto.sign(null, Buffer.from(buildCanonicalString(futureWarning), 'utf-8'), privateKey);
console.log('ACTIVE_SIG=' + sig1.toString('base64'));

// 2. Expired Warning (Expired in 2024)
const expiredWarning = {
  ...futureWarning,
  issuedAt: 1700000000000,
  effectiveAt: 1700000000000,
  expiresAt: 1710000000000
};
const sig2 = crypto.sign(null, Buffer.from(buildCanonicalString(expiredWarning), 'utf-8'), privateKey);
console.log('EXPIRED_SIG=' + sig2.toString('base64'));
