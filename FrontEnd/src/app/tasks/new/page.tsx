"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";

import { createTask } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";

export default function TaskCreatePage() {
  const router = useRouter();
  const [userQuery, setUserQuery] = useState("请帮我做一个 water yield 分析");
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
    <main className="container">
      <div className="card">
        <h1>创建任务</h1>
        <p className="muted">输入自然语言后提交，系统将触发 Week1 的 Pass1 主链路。</p>
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

