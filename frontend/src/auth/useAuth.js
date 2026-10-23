import { useContext } from "react";
import { AuthContext } from "./authContext";

/**
 * Reads the authentication state.
 *
 * @returns {{
 *   session: {token: string, email: string, role: string} | null,
 *   sessionExpired: boolean,
 *   signingIn: boolean,
 *   signIn: (input: {email: string, password: string, role?: string, rememberMe?: boolean}) => Promise<object>,
 *   signOut: () => void,
 * }}
 */
export function useAuth() {
  const value = useContext(AuthContext);

  if (!value) {
    throw new Error("useAuth must be used inside an AuthProvider");
  }

  return value;
}
