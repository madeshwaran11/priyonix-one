import { useCallback, useRef, useState } from "react";

/**
 * Tracks an explicit reload that should keep the current, successfully loaded
 * view on screen. The request itself stays in the page so existing API and
 * cancellation behavior remain unchanged.
 */
export function useRetainedRefresh() {
  const retainedRequest = useRef(false);
  const [refreshing, setRefreshing] = useState(false);

  const beginRefresh = useCallback(() => {
    if (retainedRequest.current) return false;
    retainedRequest.current = true;
    setRefreshing(true);
    return true;
  }, []);

  const settleRefresh = useCallback(() => {
    const retained = retainedRequest.current;
    retainedRequest.current = false;
    setRefreshing(false);
    return retained;
  }, []);

  const cancelRefresh = useCallback(() => {
    retainedRequest.current = false;
    setRefreshing(false);
  }, []);

  return { refreshing, beginRefresh, settleRefresh, cancelRefresh };
}
