import { describe, it, expect } from 'vitest';
import {
  formatCoordinates,
  formatRelativeTime,
  truncateText,
  formatDateTime,
} from '../utils/formatters';

describe('Formatters Utility Suite', () => {
  describe('formatCoordinates', () => {
    it('returns GPS Fix Unavailable when lat or lon is missing', () => {
      expect(formatCoordinates(null, null)).toBe('GPS Fix Unavailable');
      expect(formatCoordinates(undefined, undefined)).toBe('GPS Fix Unavailable');
      expect(formatCoordinates(28.6139, null)).toBe('GPS Fix Unavailable');
      expect(formatCoordinates(null, 77.2090)).toBe('GPS Fix Unavailable');
    });

    it('formats northern and eastern coordinates correctly', () => {
      const result = formatCoordinates(28.6139, 77.2090);
      expect(result).toBe('28.61390° N, 77.20900° E');
    });

    it('formats southern and western coordinates correctly', () => {
      const result = formatCoordinates(-33.8688, -151.2093);
      expect(result).toBe('33.86880° S, 151.20930° W');
    });

    it('includes accuracy radius when provided', () => {
      const result = formatCoordinates(28.6139, 77.2090, 12.4);
      expect(result).toBe('28.61390° N, 77.20900° E (±12.4m)');
    });
  });

  describe('truncateText', () => {
    it('returns empty string if text is empty', () => {
      expect(truncateText('', 10)).toBe('');
    });

    it('returns original string if length is within maxLength', () => {
      expect(truncateText('Hello', 10)).toBe('Hello');
      expect(truncateText('Exact10Len', 10)).toBe('Exact10Len');
    });

    it('truncates and appends ellipsis if string exceeds maxLength', () => {
      expect(truncateText('Emergency Alert Description Long', 15)).toBe('Emergency Alert...');
    });
  });

  describe('formatDateTime', () => {
    it('returns N/A when isoString is missing', () => {
      expect(formatDateTime(null)).toBe('N/A');
      expect(formatDateTime(undefined)).toBe('N/A');
    });

    it('formats a valid ISO timestamp', () => {
      const iso = '2026-11-01T12:00:00Z';
      const formatted = formatDateTime(iso);
      expect(formatted).toContain('2026');
      expect(formatted).toContain('Nov');
    });
  });

  describe('formatRelativeTime', () => {
    it('returns Unknown for missing input', () => {
      expect(formatRelativeTime(null)).toBe('Unknown');
    });

    it('returns seconds ago for recent timestamps', () => {
      const recent = new Date(Date.now() - 10000).toISOString();
      expect(formatRelativeTime(recent)).toMatch(/\d+s ago/);
    });
  });
});
