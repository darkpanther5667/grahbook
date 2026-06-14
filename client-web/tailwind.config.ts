import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          indigo: "#4F46E5",
          "indigo-deep": "#1E1B4B",
          saffron: "#FF6B00",
          "saffron-deep": "#D45900",
          green: "#10B981",
          "green-dim": "#059669",
          red: "#EF4444",
          "red-dim": "#DC2626",
          amber: "#F59E0B",
          "amber-dim": "#D97706",
        },
        ink: {
          900: "#070B14",
          800: "#0E1322",
          700: "#161F35",
          600: "#222E4D",
          500: "#32426C",
          400: "#4C5E8A",
          300: "#7285B5",
          200: "#98A9D4",
          100: "#CAD4F0",
          50: "#F1F4FA",
        },
        primary: {
          DEFAULT: "#4F46E5",
          50: "#E0E7FF",
          100: "#C7D2FE",
          200: "#A5B4FC",
          300: "#818CF8",
          400: "#6366F1",
          500: "#4F46E5",
          600: "#4338CA",
          700: "#3730A3",
          800: "#312E81",
          900: "#1E1B4B",
        },
        saffron: {
          DEFAULT: "#FF6B00",
          50: "#FFF0E6",
          100: "#FFE0CC",
          200: "#FFAC66",
          300: "#FF8C33",
          400: "#FF6B00",
          500: "#D45900",
          600: "#A84700",
        },
        surface: "#ffffff",
        surfaceDark: "#161F35",
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
      },
      fontFamily: {
        sans: ["Poppins", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "monospace"],
      },
      keyframes: {
        "fade-in": {
          "0%": { opacity: "0", transform: "translateY(10px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "scale-in": {
          "0%": { opacity: "0", transform: "scale(0.95)" },
          "100%": { opacity: "1", transform: "scale(1)" },
        },
        shimmer: {
          "0%": { backgroundPosition: "-200% 0" },
          "100%": { backgroundPosition: "200% 0" },
        },
      },
      animation: {
        "fade-in": "fade-in 0.5s ease-out",
        "scale-in": "scale-in 0.3s ease-out",
        shimmer: "shimmer 1.5s infinite",
      },
    },
  },
  plugins: [],
};
export default config;
