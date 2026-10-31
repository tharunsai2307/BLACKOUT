import React from 'react';
import { Loader2 } from 'lucide-react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'outline' | 'ghost' | 'success';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
  isLoading?: boolean;
  icon?: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  isLoading = false,
  icon,
  className = '',
  disabled,
  ...props
}) => {
  const isButtonLoading = loading || isLoading;

  const baseStyles =
    'inline-flex items-center justify-center font-medium rounded transition-colors focus:outline-none focus:ring-1 focus:ring-operations-cyan disabled:opacity-50 disabled:cursor-not-allowed select-none font-mono';

  const variantStyles = {
    primary: 'bg-operations-cyan hover:bg-cyan-600 text-slate-950 font-semibold shadow-sm',
    secondary: 'bg-operations-card hover:bg-slate-800 text-slate-200 border border-operations-border',
    danger: 'bg-operations-red hover:bg-red-700 text-white shadow-sm font-semibold',
    success: 'bg-operations-green hover:bg-emerald-700 text-slate-950 shadow-sm font-semibold',
    outline: 'border border-operations-border hover:bg-operations-surface text-slate-300',
    ghost: 'hover:bg-operations-surface text-slate-400 hover:text-slate-200',
  };

  const sizeStyles = {
    sm: 'text-xs px-2.5 py-1 gap-1.5',
    md: 'text-xs px-3.5 py-2 gap-2',
    lg: 'text-sm px-5 py-2.5 gap-2.5',
  };

  return (
    <button
      className={`${baseStyles} ${variantStyles[variant]} ${sizeStyles[size]} ${className}`}
      disabled={disabled || isButtonLoading}
      {...props}
    >
      {isButtonLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin text-current" /> : icon}
      {children}
    </button>
  );
};
