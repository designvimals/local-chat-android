export type AppRoute = "/login" | "/chat" | "/storage";

export function currentRoute(): AppRoute {
  if (window.location.pathname === "/storage") {
    return "/storage";
  }
  if (window.location.pathname === "/chat") {
    return "/chat";
  }
  return "/login";
}

export function navigate(route: AppRoute): void {
  if (window.location.pathname !== route) {
    window.history.pushState({}, "", route);
    window.dispatchEvent(new PopStateEvent("popstate"));
  }
}
