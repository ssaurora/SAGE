import Link from "next/link";

export default function Home() {
  return (
    <main className="container">
      <div className="card">
        <h1>SAGE Week1 MVP</h1>
        <p className="muted">请先登录后创建任务。</p>
        <div className="row">
          <Link href="/login">前往登录</Link>
          <Link href="/tasks/new">创建任务</Link>
        </div>
      </div>
    </main>
  );
}

