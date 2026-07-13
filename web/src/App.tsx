import { FormEvent, useEffect, useState } from "react";
import { KeyRound } from "lucide-react";
import { login } from "./lib/api";
import { loadSession, saveSession } from "./lib/auth";
import { currentRoute, navigate, type AppRoute } from "./lib/navigation";
import type { AuthSession } from "./types/api";
import { ChatLayout } from "./components/ChatLayout";

export function App() {
  const [route, setRoute] = useState<AppRoute>(currentRoute());
  const [session, setSession] = useState<AuthSession | null>(() => loadSession());

  useEffect(() => {
    const syncRoute = () => setRoute(currentRoute());
    window.addEventListener("popstate", syncRoute);
    return () => window.removeEventListener("popstate", syncRoute);
  }, []);

  useEffect(() => {
    if (!session && route !== "/login") {
      navigate("/login");
    }
    if (session && route === "/login") {
      navigate("/chat");
    }
  }, [route, session]);

  if (!session) {
    return <LoginScreen onSignedIn={setSession} />;
  }

  return (
    <ChatLayout
      route={route === "/storage" ? "/storage" : "/chat"}
      session={session}
      onSignedOut={() => setSession(null)}
    />
  );
}

interface LoginScreenProps {
  onSignedIn: (session: AuthSession) => void;
}

function LoginScreen({ onSignedIn }: LoginScreenProps) {
  const [pairingCode, setPairingCode] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response = await login(pairingCode.trim());
      const session = saveSession(response);
      onSignedIn(session);
      navigate("/chat");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Could not sign in.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main id="main" className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-mark" aria-hidden>
          <KeyRound size={28} />
        </div>
        <p className="eyebrow">One private connection</p>
        <h1 id="login-title">Between us.</h1>
        <p className="login-copy">Enter the one-time code shown on the phone. After pairing, this browser remembers the connection locally.</p>

        <form className="login-form" onSubmit={submit}>
          <input type="text" name="username" autoComplete="username" value="viewer" readOnly hidden />
          <label htmlFor="pairing-code">6-digit code</label>
          <input
            id="pairing-code"
            name="pairing-code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            spellCheck={false}
            placeholder="000 000"
            maxLength={6}
            value={pairingCode}
            onChange={(event) => setPairingCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
          />
          {error ? <p className="form-error" role="alert">{error}</p> : null}
          <button type="submit" className="primary-button" disabled={submitting}>
            {submitting ? "Pairing…" : "Open private chat"}
          </button>
        </form>
      </section>
    </main>
  );
}
