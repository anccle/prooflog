"use client";

import React, { useEffect, useState, Suspense } from "react";
import { useSearchParams } from "next/navigation";

interface DocumentResult {
  id: string;
  title: string;
  finalContent: string;
  contentHash: string;
  humanScore: number;
  totalKeyStrokes: number;
  backspaceCount: number;
  pasteCount: number;
  pastedCharCount: number;
  focusOutCount: number;
  durationSeconds: number;
  verifiedAt: string;
}

function VerifyContent() {
  const searchParams = useSearchParams();
  const id = searchParams.get("id");
  const [doc, setDoc] = useState<DocumentResult | null>(null);

  useEffect(() => {
    if (!id) return;
    fetch(`http://localhost:8080/api/v1/documents/${id}`)
      .then((res) => res.json())
      .then((data) => setDoc(data))
      .catch((err) => console.error(err));
  }, [id]);

  if (!doc) {
    return <div className="min-h-screen bg-slate-950 text-slate-300 p-10">검증 데이터 불러오는 중...</div>;
  }

  const isTrusted = doc.humanScore >= 80;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-8 flex flex-col items-center">
      <div className="w-full max-w-3xl bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-xl">
        <div className="flex justify-between items-start border-b border-slate-800 pb-6 mb-6">
          <div>
            <span className={`px-3 py-1 rounded-full text-xs font-bold ${isTrusted ? "bg-emerald-500/20 text-emerald-400" : "bg-rose-500/20 text-rose-400"}`}>
              {isTrusted ? "VERIFIED HUMAN AUTHORSHIP" : "HIGH AI / PASTE SUSPICION"}
            </span>
            <h1 className="text-2xl font-bold mt-3">{doc.title}</h1>
            <p className="text-xs text-slate-400 mt-1">발급 일시: {new Date(doc.verifiedAt).toLocaleString()}</p>
          </div>
          <div className="text-right">
            <div className="text-xs text-slate-400">인간 작성 신뢰도</div>
            <div className={`text-4xl font-extrabold ${isTrusted ? "text-emerald-400" : "text-rose-400"}`}>
              {doc.humanScore}점
            </div>
          </div>
        </div>

        <div className="grid grid-cols-4 gap-4 mb-6 text-center">
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
            <div className="text-xs text-slate-400">직접 키 입력</div>
            <div className="text-xl font-bold text-white mt-1">{doc.totalKeyStrokes}타</div>
          </div>
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
            <div className="text-xs text-slate-400">퇴고(수정) 횟수</div>
            <div className="text-xl font-bold text-sky-400 mt-1">{doc.backspaceCount}회</div>
          </div>
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
            <div className="text-xs text-slate-400">붙여넣기</div>
            <div className="text-xl font-bold text-rose-400 mt-1">{doc.pasteCount}회 ({doc.pastedCharCount}자)</div>
          </div>
          <div className="bg-slate-950 p-4 rounded-xl border border-slate-800">
            <div className="text-xs text-slate-400">총 작업 시간</div>
            <div className="text-xl font-bold text-amber-400 mt-1">{doc.durationSeconds}초</div>
          </div>
        </div>

        <div className="mb-6">
          <h2 className="text-sm font-semibold text-slate-400 mb-2">검증된 최종 본문</h2>
          <div className="bg-slate-950 p-5 rounded-xl border border-slate-800 whitespace-pre-wrap text-slate-200 leading-relaxed">
            {doc.finalContent}
          </div>
        </div>

        <div className="bg-slate-950 p-4 rounded-xl border border-slate-800 text-xs">
          <div className="text-slate-400 mb-1">위변조 방지 무결성 해시 (SHA-256)</div>
          <div className="font-mono text-emerald-400 break-all">{doc.contentHash}</div>
        </div>
      </div>
    </div>
  );
}

export default function VerifyPage() {
  return (
    <Suspense fallback={<div className="min-h-screen bg-slate-950 text-slate-300 p-10">로딩 중...</div>}>
      <VerifyContent />
    </Suspense>
  );
}