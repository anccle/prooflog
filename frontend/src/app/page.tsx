"use client";

import React, { useState, useEffect, useRef } from "react";
import { useRouter } from "next/navigation";

interface EventItem {
  timestamp: number;
  type: "KEY_DOWN" | "BACKSPACE" | "PASTE" | "FOCUS_OUT";
  lengthDelta: number;
  keyCategory: string;
}

export default function ProofLogEditor() {
  const router = useRouter();
  const [documentId, setDocumentId] = useState<string | null>(null);
  const [title, setTitle] = useState("2027학년도 자기소개서 초안");
  const [content, setContent] = useState("");
  const [pasteCount, setPasteCount] = useState(0);
  const [focusOutCount, setFocusOutCount] = useState(0);
  const [sentBatchCount, setSentBatchCount] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const startTimeRef = useRef<number>(Date.now());
  const eventBufferRef = useRef<EventItem[]>([]);
  const sequenceRef = useRef<number>(1);

  // 1. 페이지 진입 시 백엔드에서 새 문서 UUID 세션 발급
  useEffect(() => {
    const initDocument = async () => {
      try {
        const res = await fetch("http://localhost:8080/api/v1/documents", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ title }),
        });
        if (res.ok) {
          const data = await res.json();
          setDocumentId(data.id);
        }
      } catch (err) {
        console.error("문서 세션 생성 실패:", err);
      }
    };
    initDocument();
  }, []);

  // 2. 브라우저 탭 이탈 감지
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden) {
        setFocusOutCount((prev) => prev + 1);
        eventBufferRef.current.push({
          timestamp: Date.now() - startTimeRef.current,
          type: "FOCUS_OUT",
          lengthDelta: 0,
          keyCategory: "WINDOW",
        });
      }
    };
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, []);

  // 3. 버퍼 전송 함수 (5초 주기 및 제출 직전 호출)
  const flushLogs = async (docId: string) => {
    if (eventBufferRef.current.length === 0) return;

    const eventsToSend = [...eventBufferRef.current];
    eventBufferRef.current = [];
    const currentSeq = sequenceRef.current++;

    const res = await fetch("http://localhost:8080/api/v1/logs/batch", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        documentId: docId,
        sequenceNum: currentSeq,
        events: eventsToSend,
      }),
    });

    if (res.ok) {
      setSentBatchCount((prev) => prev + 1);
    }
  };

  useEffect(() => {
    if (!documentId) return;
    const interval = setInterval(() => flushLogs(documentId), 5000);
    return () => clearInterval(interval);
  }, [documentId]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    const now = Date.now() - startTimeRef.current;
    if (e.key === "Backspace" || e.key === "Delete") {
      eventBufferRef.current.push({
        timestamp: now,
        type: "BACKSPACE",
        lengthDelta: -1,
        keyCategory: "DELETE",
      });
    } else if (e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
      eventBufferRef.current.push({
        timestamp: now,
        type: "KEY_DOWN",
        lengthDelta: 1,
        keyCategory: e.key === " " ? "SPACE" : "CHAR",
      });
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const pastedText = e.clipboardData.getData("text");
    setPasteCount((prev) => prev + 1);
    eventBufferRef.current.push({
      timestamp: Date.now() - startTimeRef.current,
      type: "PASTE",
      lengthDelta: pastedText.length,
      keyCategory: "CLIPBOARD",
    });
  };

  // 4. 최종 제출 및 검증서 발급
  const handleSubmit = async () => {
    if (!documentId || !content.trim()) {
      alert("내용을 작성한 뒤 제출해 주세요.");
      return;
    }
    setIsSubmitting(true);
    try {
      // 남은 버퍼 로그 마지막으로 전송
      await flushLogs(documentId);

      // 검증 점수 산출 API 호출
      const res = await fetch(`http://localhost:8080/api/v1/documents/${documentId}/submit`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ finalContent: content }),
      });

      if (res.ok) {
        router.push(`/verify?id=${documentId}`);    
      }
    } catch (err) {
      console.error("제출 실패:", err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 p-8 flex flex-col items-center">
      <div className="w-full max-w-4xl">
        <header className="flex justify-between items-center mb-6 border-b border-slate-800 pb-4">
          <div>
            <h1 className="text-2xl font-bold text-emerald-400">ProofLog Editor</h1>
            <p className="text-xs text-slate-400 mt-1">
              세션 ID: <span className="font-mono text-slate-300">{documentId ?? "연결 중..."}</span>
            </p>
          </div>
          <button
            onClick={handleSubmit}
            disabled={isSubmitting || !documentId}
            className="bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold px-5 py-2.5 rounded-lg transition disabled:opacity-50"
          >
            {isSubmitting ? "무결성 검증 중..." : "작성 완료 및 검증서 발급"}
          </button>
        </header>

        <div className="flex gap-3 mb-4 text-sm">
          <div className="bg-slate-900 px-3 py-2 rounded border border-slate-800">
            글자 수: <span className="font-bold text-white">{content.length}</span>
          </div>
          <div className="bg-slate-900 px-3 py-2 rounded border border-slate-800">
            붙여넣기: <span className="font-bold text-rose-400">{pasteCount}회</span>
          </div>
          <div className="bg-slate-900 px-3 py-2 rounded border border-slate-800">
            창 이탈: <span className="font-bold text-amber-400">{focusOutCount}회</span>
          </div>
          <div className="bg-slate-900 px-3 py-2 rounded border border-slate-800">
            서버 동기화: <span className="font-bold text-emerald-400">{sentBatchCount}회</span>
          </div>
        </div>

        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="w-full bg-slate-900 border border-slate-800 rounded-lg px-4 py-3 mb-4 text-lg font-semibold focus:outline-none focus:border-emerald-500"
          placeholder="문서 제목을 입력하세요"
        />

        <textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          placeholder="여기에 내용을 직접 작성해 보세요. 완료 후 우측 상단 버튼을 누르면 인간 작성 신뢰도 점수와 위변조 방지 해시 검증서가 발급됩니다."
          className="w-full h-96 bg-slate-900 border border-slate-700 rounded-lg p-5 text-slate-100 focus:outline-none focus:border-emerald-500 leading-relaxed resize-none"
        />
      </div>
    </div>
  );
}