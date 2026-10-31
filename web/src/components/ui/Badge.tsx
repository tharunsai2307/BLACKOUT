import React from 'react';

export type BadgeVariant =
  | 'critical'
  | 'high'
  | 'moderate'
  | 'low'
  | 'success'
  | 'warning'
  | 'info'
  | 'neutral'
  | 'CRITICAL'
  | 'HIGH'
  | 'MODERATE'
  | 'LOW'
  | 'VERIFIED'
  | 'UNVERIFIED'
  | 'UNDER_REVIEW'
  | 'FALSE_REPORT'
  | 'AVAILABLE'
  | 'ASSIGNED'
  | 'EN_ROUTE'
  | 'ON_SCENE'
  | 'COMPLETED'
  | 'UNAVAILABLE'
  | 'DRAFT'
  | 'SUBMITTED_FOR_APPROVAL'
  | 'APPROVED'
  | 'PUBLISHED'
  | 'EXPIRED'
  | 'REVOKED'
  | 'IMMEDIATE THREAT'
  | 'SEVERE'
  | 'ADVISORY'
  | string;

interface BadgeProps {
  children?: React.ReactNode;
  variant?: BadgeVariant;
  size?: 'sm' | 'md';
  dot?: boolean;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'neutral',
  size = 'md',
  dot = false,
  className = '',
}) => {
  const norm = (variant || '').toLowerCase();

  let style = 'bg-slate-800/80 text-slate-300 border-slate-700';
  let dotColor = 'bg-slate-400';

  if (norm.includes('critical') || norm.includes('false_report') || norm.includes('revoked') || norm.includes('immediate')) {
    style = 'bg-red-950/70 text-red-400 border-red-800/80';
    dotColor = 'bg-red-500 animate-pulse';
  } else if (norm.includes('high') || norm.includes('under_review') || norm.includes('en_route') || norm.includes('severe') || norm.includes('warning') || norm.includes('submitted')) {
    style = 'bg-amber-950/70 text-amber-400 border-amber-800/80';
    dotColor = 'bg-amber-500';
  } else if (norm.includes('verified') || norm.includes('available') || norm.includes('completed') || norm.includes('published') || norm.includes('approved') || norm.includes('success')) {
    style = 'bg-emerald-950/70 text-emerald-400 border-emerald-800/80';
    dotColor = 'bg-emerald-500';
  } else if (norm.includes('assigned') || norm.includes('moderate') || norm.includes('info') || norm.includes('on_scene')) {
    style = 'bg-cyan-950/70 text-cyan-400 border-cyan-800/80';
    dotColor = 'bg-cyan-500';
  } else if (norm.includes('unverified') || norm.includes('draft') || norm.includes('low') || norm.includes('advisory')) {
    style = 'bg-slate-900 text-slate-400 border-slate-700';
    dotColor = 'bg-slate-500';
  }

  const sizeStyles = {
    sm: 'text-[10px] px-2 py-0.5',
    md: 'text-xs font-semibold px-2.5 py-0.5',
  };

  const displayText = children !== undefined ? children : String(variant).replace(/_/g, ' ');

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded border tracking-wide uppercase font-mono ${style} ${sizeStyles[size]} ${className}`}
    >
      {dot && <span className={`w-1.5 h-1.5 rounded-full ${dotColor}`} />}
      {displayText}
    </span>
  );
};
