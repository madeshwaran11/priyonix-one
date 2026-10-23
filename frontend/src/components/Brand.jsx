export default function Brand({ compact = false }) {
  return (
    <div className={"brand" + (compact ? " brand-compact" : "")}>
      <img className="brand-image" src="/brand/priyonix-mark.jpg" alt="" width="54" height="54" />
      <span className="brand-wordmark">PriyoniX<strong>One</strong></span>
    </div>
  );
}


