import React from 'react';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  headerAction?: React.ReactNode;
  title?: string;
  subtitle?: string;
  icon?: React.ReactNode;
  is3D?: boolean;
}

export const Card: React.FC<CardProps> = ({
  children,
  className = '',
  headerAction,
  title,
  subtitle,
  icon,
  is3D = true,
}) => {
  return (
    <div
      className={`bg-operations-surface border border-operations-border rounded-lg shadow-sm overflow-hidden ${
        is3D ? 'card-3d holographic-edge' : ''
      } ${className}`}
    >
      {(title || headerAction) && (
        <div className="px-5 py-4 border-b border-operations-border flex items-center justify-between">
          <div className="flex items-center gap-3">
            {icon && <div className="text-operations-teal">{icon}</div>}
            <div>
              {title && <h3 className="font-semibold text-slate-100 text-sm tracking-wide">{title}</h3>}
              {subtitle && <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>}
            </div>
          </div>
          {headerAction && <div>{headerAction}</div>}
        </div>
      )}
      <div className="p-5">{children}</div>
    </div>
  );
};
