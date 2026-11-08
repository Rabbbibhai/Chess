import './globals.css';

export const metadata = { title: 'gooningchessbuddies', description: 'Chess with friends: 1v1, 3 and 4 players' };
export const viewport = { width: 'device-width', initialScale: 1 };

export default function RootLayout({ children }) {
  return (
    <html lang='en'>
      <body>{children}</body>
    </html>
  );
}
