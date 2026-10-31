/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        operations: {
          bg: '#080C14',
          surface: '#0F172A',
          card: '#131D33',
          border: '#1E293B',
          muted: '#64748B',
          text: '#F8FAFC',
          red: '#EF4444',
          amber: '#F59E0B',
          teal: '#14B8A6',
          cyan: '#06B6D4',
          green: '#10B981',
          blue: '#3B82F6',
        }
      },
      fontFamily: {
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
