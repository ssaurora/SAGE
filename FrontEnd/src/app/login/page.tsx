"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";

import { login } from "@/lib/api";
import { setAccessToken } from "@/lib/auth";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("demo");
  const [password, setPassword] = useState("demo123");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const response = await login(username, password);
      setAccessToken(response.access_token);
      router.push("/tasks/new");
    } catch (e) {
      setError(e instanceof Error ? e.message : "登录失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="container">
      <div className="card" style={{ maxWidth: 420, margin: "60px auto" }}>
        <h1>登录</h1>
        <p className="muted">使用 Week1 演示账号登录系统。</p>
        <form onSubmit={handleSubmit}>
          <label className="label" htmlFor="username">
            用户名
          </label>
          <input
            id="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            autoComplete="username"
          />

          <label className="label" htmlFor="password" style={{ marginTop: 12 }}>
            密码
          </label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
          />

          <button style={{ marginTop: 16, width: "100%" }} disabled={loading}>
            {loading ? "登录中..." : "登录"}
          </button>
        </form>
        {error ? <p className="error">{error}</p> : null}
      </div>
    </main>
  );
}

