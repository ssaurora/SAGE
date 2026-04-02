"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { createTask } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

export default function TaskCreatePage() {
  const router = useRouter();
  const [userQuery, setUserQuery] = useState("run a real case invest annual water yield analysis for gura");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!getAccessToken()) {
      router.replace("/login");
    }
  }, [router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const response = await createTask(userQuery);
      router.push(`/tasks/${response.task_id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : "创建任务失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="container page-shell">
      <div className="card hero-card">
        <span className="hero-eyebrow">New Task</span>
        <h1>创建任务</h1>
        <p className="muted">输入自然语言目标，系统会进入受治理的认知、规划、验证和执行主链。</p>
        <form onSubmit={handleSubmit}>
          <label className="label" htmlFor="userQuery">
            用户输入
          </label>
          <textarea
            id="userQuery"
            rows={6}
            value={userQuery}
            onChange={(event) => setUserQuery(event.target.value)}
          />
          <button style={{ marginTop: 16 }} disabled={loading}>
            {loading ? "提交中..." : "提交任务"}
          </button>
        </form>
        {error ? <p className="error">{error}</p> : null}
      </div>
    </main>
  );
}
