// Lists Elog builds from the repo's public releases feed.
// (No GitHub API key needed, and none of the API's per-IP rate limit that broke the page on mobile data.)
const REPO = "Rabbbibhai/Chess";

function decode(s) {
  return s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, "&");
}

function toText(html) {
  return decode(html)
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(p|li|h\d|div)>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

module.exports = async (req, res) => {
  try {
    const r = await fetch("https://github.com/" + REPO + "/releases.atom", { headers: { "User-Agent": "elog-admin" } });
    if (!r.ok) throw new Error("GitHub returned " + r.status);
    const xml = await r.text();
    const out = [];
    for (const m of xml.matchAll(/<entry>([\s\S]*?)<\/entry>/g)) {
      const e = m[1];
      const link = (e.match(/<link[^>]*href="([^"]+)"/) || [])[1] || "";
      const tag = decodeURIComponent((link.split("/tag/")[1] || ""));
      const mm = /^elog-build-(\d+)$/.exec(tag);
      if (!mm) continue;
      const date = (e.match(/<updated>([^<]+)<\/updated>/) || [])[1] || "";
      const content = (e.match(/<content[^>]*>([\s\S]*?)<\/content>/) || [])[1] || "";
      out.push({
        n: Number(mm[1]),
        date: date,
        body: toText(content),
        apk: "https://github.com/" + REPO + "/releases/download/" + tag + "/Elog.apk",
        url: link
      });
    }
    out.sort((a, b) => b.n - a.n);
    res.setHeader("Cache-Control", "s-maxage=30, stale-while-revalidate=120");
    res.status(200).json(out);
  } catch (err) {
    res.status(502).json({ error: String((err && err.message) || err) });
  }
};
