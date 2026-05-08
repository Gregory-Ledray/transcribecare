/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { motion, AnimatePresence } from 'motion/react';
import { 
  Settings, 
  Home, 
  History as HistoryIcon, 
  CircleStop, 
  MicVocal,
  Search,
  FileText,
  Play,
  Pause,
  MessageCircle,
  RefreshCw,
  Link2Off,
  Link2,
  MessageSquare,
  Share2,
  AlertCircle,
  RotateCcw
} from 'lucide-react';
import React, { useState, useEffect, useRef, ReactNode, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { saveSession, saveAudio, loadSessions, loadAudio } from './db';
import { useLocalTranscription } from './transcription/useLocalTranscription';
import type { ModelLoadProgress } from './transcription/types';
// --- Types ---
type Tab = 'home' | 'history' | 'settings';

export interface TranscriptSegment {
  id: string;
  text: string;
  type: 'past' | 'recent' | 'current';
}

interface RecordingSession {
  id: string;
  title: string;
  date: string;
  time: string;
  duration: string;
  audioUrl?: string;
  segments: TranscriptSegment[];
  statusLabel: string; // "TODAY", "YESTERDAY", etc.
}

// --- Components ---

function Header({ onSettingsClick }: { onSettingsClick: () => void }) {
  return (
    <header className="fixed top-0 w-full bg-surface z-50 border-b border-outline-variant flex justify-between items-center px-container-margin h-touch-target-min">
      <Link to="/" className="text-[28px] font-bold text-primary">TranscribeCare</Link>
    </header>
  );
}

function RecordingStatus() {
  return (
    <div className="fixed top-[56px] left-0 w-full z-40 bg-secondary-container px-container-margin py-2 flex items-center justify-center gap-2">
      <motion.div 
        animate={{ opacity: [1, 0.4, 1] }}
        transition={{ repeat: Infinity, duration: 2, ease: "easeInOut" }}
        className="w-3 h-3 rounded-full bg-on-secondary" 
      />
      <span className="text-[16px] font-bold text-on-secondary tracking-wide uppercase">Recording Active</span>
    </div>
  );
}

function TranscriptView({ segments, isLargeText, isRecording }: { segments: TranscriptSegment[], isLargeText: boolean, isRecording: boolean }) {
  const endOfListRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endOfListRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [segments]);

  return (
    <main className={`flex-1 ${isRecording ? 'mt-[96px]' : 'mt-[56px]'} mb-[160px] px-container-margin py-element-gap overflow-y-auto flex flex-col items-center transition-all duration-300`}>
      <div className="max-w-3xl w-full flex flex-col gap-8 min-h-full">
        {segments.length === 0 ? (
          <div className="flex-1 flex flex-col items-center justify-center text-on-surface-variant opacity-40 py-20">
            <MicVocal size={64} className="mb-4" />
            <p className="text-xl font-medium">Ready to transcribe</p>
            <p className="text-sm">Tap "Start Recording" to begin</p>
          </div>
        ) : (
          segments.map((segment, index) => {
            const isPast = segment.type === 'past';
            const nextSegment = segments[index + 1];
            const showDivider = isPast && nextSegment && nextSegment.type !== 'past';

            return (
              <div key={segment.id} className="w-full">
                <p className={`
                  ${isPast 
                    ? 'text-lg text-on-surface-variant opacity-50' 
                    : (isLargeText ? 'transcription-current' : 'text-2xl font-bold')
                  } 
                  ${!isPast ? 'text-primary' : ''} 
                  transition-all duration-300
                `}>
                  {segment.text}
                </p>
                {showDivider && (
                  <div className="pt-4 mb-8 border-b-2 border-outline-variant" />
                )}
              </div>
            );
          })
        )}
        <div ref={endOfListRef} />
      </div>
    </main>
  );
}

function Controls({ isRecording, onToggle }: { isRecording: boolean, onToggle: () => void }) {
  return (
    <div className="fixed bottom-[88px] left-0 w-full px-container-margin pb-4 flex justify-center pointer-events-none">
      <motion.button 
        whileTap={{ scale: 0.95 }}
        onClick={onToggle}
        className={`
          pointer-events-auto h-touch-target-min min-w-[240px] px-8 rounded-full flex items-center justify-center gap-3 shadow-lg transition-colors
          ${isRecording ? 'bg-error text-on-error' : 'bg-primary text-on-primary'}
        `}
      >
        {isRecording ? (
          <>
            <CircleStop size={28} strokeWidth={2.5} fill="currentColor" />
            <span className="text-[22px] font-semibold">Stop Recording</span>
          </>
        ) : (
          <>
            <MicVocal size={28} strokeWidth={2.5} fill="currentColor" />
            <span className="text-[22px] font-semibold">Start Recording</span>
          </>
        )}
      </motion.button>
    </div>
  );
}

function AudioPlayer({ session, isActive, onToggle }: { session: RecordingSession, isActive: boolean, onToggle: () => void }) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [progress, setProgress] = useState(0);
  const [currentTime, setCurrentTime] = useState("00:00");
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

  useEffect(() => {
    if (!audioRef.current) return;
    if (isActive) {
      audioRef.current.play();
    } else {
      audioRef.current.pause();
    }
  }, [isActive]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playbackSpeed;
    }
  }, [playbackSpeed]);

  const handleTimeUpdate = () => {
    if (!audioRef.current) return;
    const current = audioRef.current.currentTime;
    const duration = audioRef.current.duration || 1;
    setProgress((current / duration) * 100);

    const mins = Math.floor(current / 60);
    const secs = Math.floor(current % 60);
    setCurrentTime(`${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`);
  };

  const handleEnded = () => {
    onToggle();
    setProgress(0);
    setCurrentTime("00:00");
  };

  const progressBarRef = useRef<HTMLDivElement>(null);

  const handleSeek = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!audioRef.current || !progressBarRef.current) return;
    const rect = progressBarRef.current.getBoundingClientRect();
    const clickX = e.clientX - rect.left;
    const ratio = Math.max(0, Math.min(1, clickX / rect.width));
    const duration = audioRef.current.duration;
    if (isFinite(duration)) {
      audioRef.current.currentTime = ratio * duration;
      setProgress(ratio * 100);
    }
  };

  return (
    <div className={`p-4 rounded-xl flex flex-col gap-4 ${isActive ? 'bg-surface-container' : 'bg-surface-container-low opacity-80'}`}>
      <audio 
        ref={audioRef} 
        src={session.audioUrl} 
        onTimeUpdate={handleTimeUpdate}
        onEnded={handleEnded}
        hidden 
      />
      <div className="flex items-center gap-4">
        <button 
          onClick={onToggle}
          disabled={!session.audioUrl}
          className="w-12 h-12 rounded-full bg-primary text-on-primary flex items-center justify-center shrink-0 cursor-pointer disabled:opacity-50"
        >
          {isActive ? <Pause size={24} fill="currentColor" /> : <Play size={24} className="ml-1" fill="currentColor" />}
        </button>
        <div className="flex-1 space-y-1">
          <div
            ref={progressBarRef}
            onClick={handleSeek}
            role="slider"
            aria-label="Audio playback position"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(progress)}
            tabIndex={0}
            className="h-3 w-full bg-outline-variant rounded-full overflow-hidden relative cursor-pointer group"
          >
            <div className="absolute inset-0 bg-outline-variant opacity-20" />
            <motion.div 
              style={{ width: `${progress}%` }}
              className="h-full bg-secondary duration-75 group-hover:bg-secondary-container"
            />
          </div>
          <div className="flex justify-between text-[10px] font-bold text-on-surface-variant font-mono">
            <span>{currentTime}</span>
            <span>{session.duration}</span>
          </div>
        </div>
      </div>
      
      <div className="flex items-center justify-between pt-2 border-t border-outline-variant/30">
        <span className="text-xs font-bold text-on-surface-variant">Playback Speed</span>
        <div className="flex bg-surface-container-high rounded-lg p-1">
          {[1, 1.25, 1.5, 2].map((speed) => (
            <button 
              key={speed}
              onClick={() => setPlaybackSpeed(speed)}
              className={`px-3 py-1 rounded-md text-xs font-bold transition-colors cursor-pointer ${playbackSpeed === speed ? 'bg-primary text-on-primary shadow-sm' : 'hover:bg-surface-container-highest text-on-surface-variant'}`}
            >
              {speed}x
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function HistoryView({ sessions, isRecording, onViewTranscript }: { sessions: RecordingSession[], isRecording: boolean, onViewTranscript: (s: RecordingSession) => void }) {
  const [searchQuery, setSearchQuery] = useState("");
  const [activePlaybackId, setActivePlaybackId] = useState<string | null>(null);

  const handleShare = async (session: RecordingSession) => {
    const transcriptText = session.segments.map(s => s.text).join('\n');
    const shareText = `Recording on ${session.date}\n\nTranscript:\n${transcriptText}`;
    
    const shareData: any = {
      title: session.title,
      text: shareText,
    };

    // If we have an audio URL, check if it's a blob
    if (session.audioUrl) {
      if (session.audioUrl.startsWith('blob:')) {
        // Try to convert blob to a File for sharing
        try {
          const response = await fetch(session.audioUrl);
          const blob = await response.blob();
          const file = new File([blob], `recording-${session.id}.webm`, { type: 'audio/webm' });
          
          if (navigator.canShare && navigator.canShare({ files: [file] })) {
            shareData.files = [file];
          }
        } catch (e) {
          console.error('Could not process audio blob for sharing:', e);
        }
      } else {
        // It's a regular URL, safe to include
        shareData.url = session.audioUrl;
      }
    }

    // If no absolute URL was set yet, and sharing isn't just text, use the current page URL
    if (!shareData.url && (!shareData.files || shareData.files.length === 0)) {
       // Only include URL if it's likely a real shareable web address
       if (window.location.protocol.startsWith('http')) {
         shareData.url = window.location.href;
       }
    }

    try {
      if (navigator.share && navigator.canShare && navigator.canShare(shareData)) {
        await navigator.share(shareData);
      } else if (navigator.share && !shareData.files) {
        // Fallback for browsers that support sharing but not files
        const simpleShare = { title: shareData.title, text: shareData.text };
        await navigator.share(simpleShare);
      } else {
        // Fallback to clipboard
        const clipboardText = `${shareData.text}${shareData.url ? `\n\nLink: ${shareData.url}` : ''}`;
        await navigator.clipboard.writeText(clipboardText);
        alert('Sharing not fully supported or cancelled. Transcript copied to clipboard.');
      }
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        console.error('Error sharing:', err);
      }
    }
  };

  const filteredSessions = useMemo(() => {
    return sessions.filter(s => 
      s.title.toLowerCase().includes(searchQuery.toLowerCase()) || 
      s.segments.some(seg => seg.text.toLowerCase().includes(searchQuery.toLowerCase()))
    );
  }, [sessions, searchQuery]);

  return (
    <main className={`flex-1 ${isRecording ? 'mt-[96px]' : 'mt-[56px]'} mb-[80px] p-container-margin overflow-y-auto`}>
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Search */}
        <div className="space-y-2">
          <h3 className="text-sm font-bold text-on-surface">Search Conversations</h3>
          <div className="relative">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant" size={20} />
            <input 
              type="text" 
              placeholder="Find keywords or medical terms..."
              className="w-full h-[56px] pl-12 pr-4 bg-white border border-outline-variant rounded-xl focus:outline-none focus:border-primary transition-colors text-lg"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
        </div>

        {/* Recent Sessions */}
        <div className="space-y-4">
          <h3 className="text-lg font-bold text-on-surface">Recent Sessions</h3>
          <div className="flex flex-col gap-4">
            {filteredSessions.map((session) => (
              <div key={session.id} className="bg-white rounded-xl border border-outline-variant overflow-hidden shadow-sm">
                <div className="p-5 flex justify-between items-start">
                  <div className="space-y-1">
                    <span className="text-xs font-bold text-secondary uppercase tracking-wider">{session.statusLabel}</span>
                    <h4 className="text-[22px] font-bold text-on-surface leading-tight">{session.title}</h4>
                    <p className="text-sm text-on-surface-variant">{session.date} • {session.time}</p>
                  </div>
                  <div className="flex gap-2">
                    <button 
                      onClick={() => handleShare(session)}
                      className="p-3 rounded-lg border border-outline-variant hover:bg-surface-container transition-colors cursor-pointer text-on-surface"
                      title="Share with family"
                    >
                      <Share2 size={24} />
                    </button>
                    <button 
                      onClick={() => onViewTranscript(session)}
                      className="p-3 rounded-lg border border-outline-variant hover:bg-surface-container transition-colors cursor-pointer"
                    >
                      <FileText size={24} className="text-on-surface" />
                    </button>
                  </div>
                </div>

                {/* Player UI */}
                <div className="px-5 pb-5">
                  <AudioPlayer 
                    session={session} 
                    isActive={activePlaybackId === session.id} 
                    onToggle={() => setActivePlaybackId(activePlaybackId === session.id ? null : session.id)} 
                  />
                </div>
              </div>
            ))}
            {filteredSessions.length === 0 && (
              <div className="py-12 text-center text-on-surface-variant opacity-60">
                <p>No recording sessions found.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}

function Navigation({ activeTab, onTabChange }: { activeTab: Tab, onTabChange: (t: Tab) => void }) {
  return (
    <nav className="fixed bottom-0 left-0 w-full z-50 bg-surface border-t border-outline-variant flex justify-around items-center px-4 py-2 h-[80px]">
      <TabButton 
        id="home" 
        label="Home" 
        icon={<Home />} 
        isActive={activeTab === 'home'} 
        onClick={() => onTabChange('home')} 
      />
      <TabButton 
        id="history" 
        label="History" 
        icon={<HistoryIcon />} 
        isActive={activeTab === 'history'} 
        onClick={() => onTabChange('history')} 
      />
      <TabButton 
        id="settings" 
        label="Settings" 
        icon={<Settings />} 
        isActive={activeTab === 'settings'} 
        onClick={() => onTabChange('settings')} 
      />
    </nav>
  );
}

function TabButton({ id, label, icon, isActive, onClick }: { id: string, label: string, icon: ReactNode, isActive: boolean, onClick: () => void }) {
  return (
    <button 
      onClick={onClick}
      className={`
        flex flex-col items-center justify-center gap-1 min-w-[72px] h-[56px] transition-all cursor-pointer
        ${isActive ? 'text-primary' : 'text-on-surface-variant'}
      `}
    >
      <div className={`
        px-4 py-1 rounded-xl transition-colors
        ${isActive ? 'bg-primary-container/20' : 'hover:bg-surface-container'}
      `}>
        {icon}
      </div>
      <span className="text-[14px] font-bold">{label}</span>
    </button>
  );
}

function TranscriptDetailView({ session, onBack, isLargeText, isRecording }: { session: RecordingSession, onBack: () => void, isLargeText: boolean, isRecording: boolean }) {
  return (
    <main className={`flex-1 ${isRecording ? 'mt-[96px]' : 'mt-[56px]'} mb-[80px] p-container-margin overflow-y-auto animate-in slide-in-from-right duration-300`}>
      <div className="max-w-3xl mx-auto space-y-8">
        <div className="flex items-center gap-4">
          <button 
            onClick={onBack}
            className="p-3 rounded-full border border-outline-variant hover:bg-surface-container transition-colors cursor-pointer text-on-surface"
          >
            <Home size={24} className="rotate-270" /> {/* Using Home icon or just a simple back icon if we had one */}
          </button>
          <div className="space-y-1">
            <h2 className="text-[28px] font-bold text-primary leading-tight">{session.title}</h2>
            <p className="text-on-surface-variant">{session.date} • {session.time}</p>
          </div>
        </div>

        <div className="bg-white p-8 rounded-xl border border-outline-variant shadow-sm space-y-6">
          {session.segments.length === 0 ? (
            <p className="text-on-surface-variant italic">No transcription available for this session.</p>
          ) : (
            session.segments.map((segment) => (
              <p key={segment.id} className={`${isLargeText ? 'transcription-history' : 'text-lg'} text-on-surface leading-relaxed`}>
                {segment.text}
              </p>
            ))
          )}
        </div>
      </div>
    </main>
  );
}

/** Overlay displayed during model download/initialization or on error. */
function ModelLoadingOverlay({ 
  status, 
  loadProgress, 
  error, 
  onRetry 
}: { 
  status: 'idle' | 'loading' | 'ready' | 'recording' | 'error';
  loadProgress: ModelLoadProgress;
  error: string | null;
  onRetry: () => void;
}) {
  // Only show when loading or error
  if (status !== 'loading' && status !== 'error') {
    return null;
  }

  const isError = status === 'error';
  const percent = Math.round(loadProgress.percent);

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className="bg-surface rounded-2xl shadow-xl p-8 max-w-md w-[90%] flex flex-col items-center gap-6">
        {isError ? (
          <>
            {/* Error state — announced immediately via role="alert" */}
            <div role="alert" className="flex flex-col items-center gap-4 text-center">
              <AlertCircle size={48} className="text-error" aria-hidden="true" />
              <h2 className="text-xl font-bold text-on-surface">Loading Failed</h2>
              <p className="text-base text-on-surface-variant leading-relaxed">
                {error || 'An unexpected error occurred while loading the transcription model.'}
              </p>
            </div>
            <button
              onClick={onRetry}
              className="min-w-[48px] min-h-[48px] px-6 py-3 rounded-xl bg-primary text-on-primary font-bold text-lg flex items-center justify-center gap-2 hover:bg-primary/90 focus:outline-none focus:ring-4 focus:ring-primary/40 transition-colors cursor-pointer"
              aria-label="Retry loading the transcription model"
            >
              <RotateCcw size={20} aria-hidden="true" />
              Retry
            </button>
          </>
        ) : (
          <>
            {/* Loading state — progress announced via aria-live */}
            <div aria-live="polite" aria-atomic="true" className="flex flex-col items-center gap-4 w-full text-center">
              <h2 className="text-xl font-bold text-on-surface">Loading Transcription Model</h2>
              <p className="text-base text-on-surface-variant">
                {loadProgress.phase === 'downloading' && `Downloading model... ${percent}%`}
                {loadProgress.phase === 'validating' && 'Validating model integrity...'}
                {loadProgress.phase === 'initializing' && 'Initializing inference engine...'}
                {loadProgress.phase === 'ready' && 'Model ready!'}
              </p>
              {/* Progress bar */}
              <div className="w-full">
                <div
                  role="progressbar"
                  aria-valuenow={percent}
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-label={`Model loading progress: ${percent}%`}
                  className="w-full h-3 bg-outline-variant rounded-full overflow-hidden"
                >
                  <div
                    className="h-full bg-primary rounded-full transition-all duration-300"
                    style={{ width: `${percent}%` }}
                  />
                </div>
                <p className="mt-2 text-sm font-bold text-on-surface">{percent}%</p>
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

// --- Main App ---

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('home');
  const [viewingSession, setViewingSession] = useState<RecordingSession | null>(null);
  const [isRecording, setIsRecording] = useState(false);
  const [largeTextMode, setLargeTextMode] = useState(true);
  const [sessions, setSessions] = useState<RecordingSession[]>([]);
  const [isWhatsAppConnected, setIsWhatsAppConnected] = useState(false);
  const [whatsAppGroup, setWhatsAppGroup] = useState("Smith Family Care");
  const [isTextConnected, setIsTextConnected] = useState(false);
  const [textGroup, setTextGroup] = useState("Smith Family Chat");

  // Local transcription hook (replaces Web Speech API)
  const transcription = useLocalTranscription();
  
  // Media Recorder refs
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const startTimeRef = useRef<number>(0);
  const segmentsRef = useRef<TranscriptSegment[]>([]);
  const interimRef = useRef<string>("");
  const whatsappSettingsRef = useRef({ connected: false, group: "Smith Family Care" });
  const textSettingsRef = useRef({ connected: false, group: "Smith Family Chat" });

  // Keep refs in sync with transcription hook state
  useEffect(() => {
    segmentsRef.current = transcription.segments;
  }, [transcription.segments]);

  useEffect(() => {
    interimRef.current = transcription.interimText;
  }, [transcription.interimText]);

  useEffect(() => {
    whatsappSettingsRef.current = { connected: isWhatsAppConnected, group: whatsAppGroup };
  }, [isWhatsAppConnected, whatsAppGroup]);

  useEffect(() => {
    textSettingsRef.current = { connected: isTextConnected, group: textGroup };
  }, [isTextConnected, textGroup]);

  // Load sessions from IndexedDB on mount
  useEffect(() => {
    loadSessions().then(async (storedSessions) => {
      // Restore audio URLs from IndexedDB blobs
      const sessionsWithAudio = await Promise.all(
        storedSessions.map(async (session) => {
          const audioBlob = await loadAudio(session.id);
          return {
            ...session,
            audioUrl: audioBlob ? URL.createObjectURL(audioBlob) : undefined,
          };
        })
      );
      setSessions(sessionsWithAudio);
    }).catch((e) => {
      console.error('Failed to load sessions from IndexedDB:', e);
    });
  }, []);

  // Initialize local transcription engine on mount
  useEffect(() => {
    transcription.initialize();
  }, []);

  const handleConnectWhatsApp = () => {
    const groupName = prompt("Enter the name of the WhatsApp group you'd like to sync with:", whatsAppGroup);
    if (groupName && groupName.trim()) {
      setWhatsAppGroup(groupName.trim());
      setIsWhatsAppConnected(true);
    }
  };

  const handleConnectText = () => {
    const groupName = prompt("Enter the name of the Text Message group you'd like to sync with:", textGroup);
    if (groupName && groupName.trim()) {
      setTextGroup(groupName.trim());
      setIsTextConnected(true);
    }
  };

  // Handle start/stop
  const handleToggleRecording = async () => {
    if (isRecording) {
      // Stop transcription
      transcription.stop();
      
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        mediaRecorderRef.current.stop();
      }
      
      setIsRecording(false);
    } else {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const mediaRecorder = new MediaRecorder(stream);
        audioChunksRef.current = [];
        
        mediaRecorder.ondataavailable = (e) => {
          if (e.data.size > 0) {
            audioChunksRef.current.push(e.data);
          }
        };

        mediaRecorder.onstop = () => {
          const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/webm' });
          const audioUrl = URL.createObjectURL(audioBlob);
          
          const durationMs = Date.now() - startTimeRef.current;
          const mins = Math.floor(durationMs / 60000);
          const secs = Math.floor((durationMs % 60000) / 1000);
          const formattedDuration = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;

          // Pull segments from ref to avoid stale closure
          const currentSessionSegments = segmentsRef.current.filter(s => s.type !== 'past');
          const finalSegmentsToSave = [...currentSessionSegments];
          
          // Add any remaining interim text if it's not already at the end of the segments
          const trimmedInterim = interimRef.current.trim();
          const lastSavedSegment = finalSegmentsToSave.length > 0 ? finalSegmentsToSave[finalSegmentsToSave.length - 1] : null;

          if (trimmedInterim && (!lastSavedSegment || lastSavedSegment.text !== trimmedInterim)) {
            finalSegmentsToSave.push({
              id: `final-interim-${Date.now()}`,
              text: trimmedInterim,
              type: 'current'
            });
          }

          if (finalSegmentsToSave.length > 0) {
            const now = new Date();
            const sessionId = Date.now().toString();
            const newSession: RecordingSession = {
              id: sessionId,
              title: `Recording on ${now.toLocaleDateString()}`,
              date: now.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }),
              time: now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }),
              duration: formattedDuration,
              audioUrl: audioUrl,
              segments: finalSegmentsToSave,
              statusLabel: 'TODAY',
            };
            
            setSessions(prev => [newSession, ...prev]);

            // Persist session metadata and audio to IndexedDB
            const { audioUrl: _url, ...sessionMetadata } = newSession;
            saveSession(sessionMetadata).catch(e => console.error('Failed to save session:', e));
            saveAudio(sessionId, audioBlob).catch(e => console.error('Failed to save audio:', e));

            // Mock WhatsApp Integration sending
            const waSettings = whatsappSettingsRef.current;
            if (waSettings.connected) {
              const fullText = finalSegmentsToSave.map(s => s.text).join(" ");
              setTimeout(() => {
                alert(`[WhatsApp Integration]\n\nSuccessfully synced to '${waSettings.group}':\n\n"${fullText}"`);
              }, 500);
            }

            // Mock Text Message Integration sending
            const tSettings = textSettingsRef.current;
            if (tSettings.connected) {
              const fullText = finalSegmentsToSave.map(s => s.text).join(" ");
              setTimeout(() => {
                alert(`[Text Message Integration]\n\nSuccessfully sent to '${tSettings.group}':\n\n"${fullText}"`);
              }, 1000);
            }
          }
          
          stream.getTracks().forEach(track => track.stop());
        };

        mediaRecorderRef.current = mediaRecorder;
        startTimeRef.current = Date.now();
        mediaRecorder.start();

        // Start local transcription
        await transcription.start();
        setIsRecording(true);
      } catch (e) {
        console.error("Error starting recording:", e);
        alert("Could not access microphone. Please check permissions.");
      }
    }
  };

  const displaySegments = [...transcription.segments];
  if (transcription.interimText) {
    displaySegments.push({
      id: 'interim',
      text: transcription.interimText,
      type: 'current'
    });
  }

  return (
    <div className="min-h-screen flex flex-col bg-background">
      <Header onSettingsClick={() => setActiveTab('settings')} />
      <ModelLoadingOverlay
        status={transcription.status}
        loadProgress={transcription.loadProgress}
        error={transcription.error}
        onRetry={transcription.retry}
      />
      {isRecording && <RecordingStatus />}
      
      {activeTab === 'home' && (
        <>
          <TranscriptView segments={displaySegments} isLargeText={largeTextMode} isRecording={isRecording} />
          <Controls 
            isRecording={isRecording} 
            onToggle={handleToggleRecording} 
          />
        </>
      )}

      {activeTab === 'history' && !viewingSession && (
        <HistoryView sessions={sessions} isRecording={isRecording} onViewTranscript={setViewingSession} />
      )}

      {activeTab === 'history' && viewingSession && (
        <TranscriptDetailView 
          session={viewingSession} 
          onBack={() => setViewingSession(null)} 
          isLargeText={largeTextMode}
          isRecording={isRecording}
        />
      )}

      {activeTab === 'settings' && (
        <main className={`flex-1 ${isRecording ? 'mt-[96px]' : 'mt-[56px]'} mb-[80px] p-container-margin animate-in fade-in duration-300`}>
          <div className="max-w-3xl mx-auto space-y-12">
            <div>
              <h2 className="text-[28px] font-bold text-primary mb-4">Accessibility</h2>
              <div className="flex flex-col gap-2">
                <button 
                  onClick={() => setLargeTextMode(!largeTextMode)}
                  className="bg-white p-4 rounded-xl border border-outline-variant flex justify-between items-center h-[72px] w-full text-left cursor-pointer hover:bg-surface-container transition-colors shadow-sm"
                >
                  <span className="font-bold text-lg">Large Text Mode</span>
                  <div className={`w-12 h-6 rounded-full relative transition-colors ${largeTextMode ? 'bg-primary' : 'bg-outline-variant'}`}>
                    <motion.div 
                      animate={{ x: largeTextMode ? 24 : 0 }}
                      transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                      className="absolute left-1 top-1 w-4 h-4 bg-white rounded-full shadow-sm" 
                    />
                  </div>
                </button>
              </div>
            </div>
          </div>
        </main>
      )}

      <Navigation activeTab={activeTab} onTabChange={(tab) => {
        setActiveTab(tab);
        setViewingSession(null);
      }} />
    </div>
  );
}

/*
function settingsNotUsed() {
  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h2 className="text-[28px] font-bold text-primary">Family Sharing</h2>
        <p className="text-on-surface-variant leading-relaxed opacity-90">
          Keep your support network informed. Transcriptions are automatically shared with your selected group once a session ends, ensuring everyone stays updated on your care.
        </p>
      </div>

      <div className="bg-white rounded-2xl border-2 border-outline-variant p-6 shadow-sm flex flex-col md:flex-row gap-6 items-start md:items-center justify-between">
        <div className="flex gap-4 items-start">
          <div className="w-[60px] h-[60px] bg-[#1cd068] rounded-xl flex items-center justify-center shrink-0">
            <MessageCircle stroke="white" strokeWidth={2} fill="white" size={32} />
          </div>
          <div>
            <h3 className="text-xl font-bold text-on-surface">WhatsApp Integration</h3>
            {isWhatsAppConnected ? (
              <>
                <div className="flex items-center gap-2 mt-1 mb-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-[#1cd068]"></div>
                  <span className="font-bold text-[15px]">Connected: {whatsAppGroup}</span>
                </div>
                <p className="text-on-surface-variant text-[15px]">
                  Currently syncing all healthcare transcriptions to the '{whatsAppGroup}' group.
                </p>
              </>
            ) : (
              <>
                <div className="flex items-center gap-2 mt-1 mb-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-outline-variant"></div>
                  <span className="font-bold text-[15px] text-on-surface-variant">Not Connected</span>
                </div>
                <p className="text-on-surface-variant text-[15px]">
                  Automatically sync healthcare transcriptions to your family's WhatsApp group.
                </p>
              </>
            )}
          </div>
        </div>
        <div className="flex flex-col gap-3 w-full md:w-auto shrink-0 md:min-w-[140px]">
          {isWhatsAppConnected ? (
            <>
              <button 
                onClick={() => {
                  const newGroup = prompt("Enter new WhatsApp group name:", whatsAppGroup);
                  if (newGroup) setWhatsAppGroup(newGroup);
                }}
                className="bg-[#0f172a] text-white h-11 px-6 rounded-lg font-bold flex items-center justify-center gap-2 hover:bg-[#1e293b] transition-colors cursor-pointer w-full text-sm"
              >
                <RefreshCw size={16} />
                Change Group
              </button>
              <button 
                onClick={() => setIsWhatsAppConnected(false)}
                className="bg-white text-on-surface border border-outline-variant h-11 px-6 rounded-lg font-bold flex items-center justify-center gap-2 hover:bg-surface-container transition-colors cursor-pointer w-full text-sm shadow-sm"
              >
                <Link2Off size={16} />
                Disconnect
              </button>
            </>
          ) : (
            <button 
              onClick={handleConnectWhatsApp}
              className="bg-[#0f172a] text-white h-11 px-6 rounded-lg font-bold flex items-center justify-center gap-2 hover:bg-[#1e293b] transition-colors cursor-pointer w-full text-sm"
            >
              <Link2 size={16} />
              Connect
            </button>
          )}
        </div>
      </div>

        <div className="bg-white rounded-2xl border-2 border-outline-variant p-6 shadow-sm flex flex-col md:flex-row gap-6 items-start md:items-center justify-between">
        <div className="flex gap-4 items-start">
          <div className="w-[60px] h-[60px] bg-[#0f172a] rounded-xl flex items-center justify-center shrink-0">
            <MessageSquare stroke="black" fill="white" size={32} />
          </div>
          <div>
            <h3 className="text-xl font-bold text-on-surface">Text Message Integration</h3>
            {isTextConnected ? (
              <>
                <div className="flex items-center gap-2 mt-1 mb-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-[#1cd068]"></div>
                  <span className="font-bold text-[15px]">Connected: {textGroup}</span>
                </div>
                <p className="text-on-surface-variant text-[15px]">
                  Automatically sync all healthcare transcriptions to your family's native text message group.
                </p>
              </>
            ) : (
              <>
                <div className="flex items-center gap-2 mt-1 mb-2">
                  <div className="w-2.5 h-2.5 rounded-full bg-outline-variant"></div>
                  <span className="font-bold text-[15px] text-on-surface-variant">Not Connected</span>
                </div>
                <p className="text-on-surface-variant text-[15px]">
                  Sync transcriptions automatically to your family's SMS/iMessage group chat.
                </p>
              </>
            )}
          </div>
        </div>
        <div className="flex flex-col gap-3 w-full md:w-auto shrink-0 md:min-w-[140px]">
          {isTextConnected ? (
            <>
              <button 
                onClick={() => {
                  const newGroup = prompt("Enter new Text Message group name:", textGroup);
                  if (newGroup) setTextGroup(newGroup);
                }}
                className="bg-[#0f172a] text-white h-11 px-6 rounded-lg font-bold flex items-center justify-center gap-2 hover:bg-[#1e293b] transition-colors cursor-pointer w-full text-sm"
              >
                <RefreshCw size={16} />
                Change Group
              </button>
              <button 
                onClick={() => setIsTextConnected(false)}
                className="bg-white text-on-surface border border-outline-variant h-11 px-6 rounded-lg font-bold flex items-center justify-center gap-2 hover:bg-surface-container transition-colors cursor-pointer w-full text-sm shadow-sm"
              >
                <Link2Off size={16} />
                Disconnect
              </button>
            </>
          ) : (
            <button 
              onClick={handleConnectText}
              className="bg-[#0f172a] text-white h-11 px-6 rounded-lg font-bold flex items-center justify-center gap-2 hover:bg-[#1e293b] transition-colors cursor-pointer w-full text-sm"
            >
              <Link2 size={16} />
              Connect
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
*/
