import "./globals.css";
export const metadata = { title: "Chess recap", description: "Get your chess.com move quality in Discord after every game." };
export default function RootLayout({ children }) {
  return (<html lang="en"><body>{children}</body></html>);
}
