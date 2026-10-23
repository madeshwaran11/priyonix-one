export default function BrandLoader({ label = "Opening your workspace", detail = "Getting everything ready for you…", logoSrc = "/brand/priyonix-mark.jpg", fullPage = true }) {
  return <div className={`brand-loader${fullPage ? " brand-loader-full" : ""}`} role="status" aria-live="polite" aria-busy="true">
    <div className="brand-loader-mark" aria-hidden="true"><img src={logoSrc} alt="" width="64" height="64" /><span className="brand-loader-ring" /></div>
    <div className="brand-loader-copy"><p className="brand-loader-title">{label}</p>{detail && <p className="brand-loader-detail">{detail}</p>}</div>
  </div>;
}
