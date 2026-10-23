import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./useAuth";
import { canonicalRole } from "../roles";
import { navigationForRole } from "../nav";
import { safeReturnTo } from "./returnTo";
import BrandLoader from "../components/BrandLoader";

/**
 * Route guard for the role protected workspaces.
 *
 * The guard is a convenience for the interface, never the security
 * boundary: every endpoint of the backend checks the role of the token on
 * its own and answers 403 for the wrong role.
 *
 * @param {{role: "ADMIN" | "EMPLOYEE", children: React.ReactNode}} props
 */
export default function RequireRole({ role, children }) {
  const { session, status } = useAuth();
  const location = useLocation();

  if (status === "restoring") return <BrandLoader label="Restoring your workspace" />;

  if (!session) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  }

  if (canonicalRole(session.role) !== role) {
    // The account is valid but belongs to the other workspace.
    return <Navigate to={safeReturnTo(location.pathname + location.search, session.role, navigationForRole(session.role).map((item) => item.id))} replace />;
  }

  return children;
}
