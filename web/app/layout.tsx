import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Field Incident — Operator',
  description: 'Triage incidents reported by field technicians.',
};

// Structural only: layouts are reused across navigations, so data lives in pages.
export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full">{children}</body>
    </html>
  );
}
