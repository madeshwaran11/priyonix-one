import { createContext } from "react";

// The authentication context is kept in its own module so the provider
// component and the hook that reads it stay separate files. Mixing both in
// one file would make the component re-export rules of the fast refresh
// plugin complain.

export const AuthContext = createContext(null);
