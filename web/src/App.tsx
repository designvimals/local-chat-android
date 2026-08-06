import { FormEvent, useEffect, useState } from "react";
import { KeyRound, RefreshCw, Smartphone } from "lucide-react";
import { createWebPairingCode, login, webPairingStatus } from "./lib/api";
import { loadSession, saveSession } from "./lib/auth";
import { currentRoute, navigate, type AppRoute } from "./lib/navigation";
import { setDocumentTitle, setFaviconStatus } from "./lib/favicon";
import type { AuthSession } from "./types/api";
import { ChatLayout } from "./components/ChatLayout";

export function App() {
  const [route, setRoute] = useState<AppRoute>(currentRoute());
  const [session, setSession] = useState<AuthSession | null>(() => loadSession());

  useEffect(() => {
    if (!session) {
      setFaviconStatus("offline");
      setDocumentTitle("offline");
    }
  }, [session]);

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
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [webPairing, setWebPairing] = useState<{
    requestId: string;
    pairingCode: string;
    expiresAt: string;
  } | null>(null);

  useEffect(() => {
    if (!webPairing) return;
    let cancelled = false;
    const poll = async () => {
      try {
        const status = await webPairingStatus(webPairing.requestId);
        if (cancelled) return;
        if (status.status === "claimed") {
          const session = saveSession(status.session);
          onSignedIn(session);
          navigate("/chat");
          return;
        }
        if (status.status === "expired") {
          setWebPairing(null);
          setError("That code expired. Generate a new one.");
        }
      } catch (caught) {
        if (!cancelled) {
          setError(caught instanceof Error ? caught.message : "Could not check the code.");
        }
      }
    };
    void poll();
    const timer = window.setInterval(poll, 2_000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [webPairing, onSignedIn]);

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

  async function generateCode() {
    setGenerating(true);
    setError(null);
    try {
      const response = await createWebPairingCode();
      setWebPairing(response);
      setPairingCode("");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Could not generate a code.");
    } finally {
      setGenerating(false);
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
        <p className="login-copy">Enter the one-time code shown on the phone. After pairing, this device remembers the connection locally.</p>

        <div className="web-pairing-card">
          <div className="web-pairing-heading">
            <Smartphone size={18} aria-hidden />
            <span>Pairing</span>
          </div>
          {webPairing ? (
            <>
              <div className="web-pairing-code" aria-label={`Pairing code ${webPairing.pairingCode}`}>
                {formatPairingCode(webPairing.pairingCode)}
              </div>
              <p>Enter this code on the phone. The chat will open when pairing is complete.</p>
            </>
          ) : (
            <p>Generate a code here, then enter it on the phone.</p>
          )}
          <button type="button" className="soft-button web-pairing-button" onClick={generateCode} disabled={generating}>
            <RefreshCw size={16} aria-hidden />
            {generating ? "Generating..." : webPairing ? "New code" : "Generate code"}
          </button>
        </div>

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

function formatPairingCode(value: string): string {
  return value.replace(/(\d{3})(\d{3})/, "$1  $2");
}
