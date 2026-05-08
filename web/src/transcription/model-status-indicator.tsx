import React from 'react';
import { Loader2, CheckCircle2, AlertCircle, Download } from 'lucide-react';
import type { ModelStatus } from './types';

/** Props for the ModelStatusIndicator component. */
export interface ModelStatusIndicatorProps {
  /** Current model loading status. */
  status: ModelStatus;
  /** Download progress from 0 to 100, meaningful during 'downloading' status. */
  downloadPercent: number;
  /** Human-readable error message when status is 'error'. */
  errorMessage?: string;
  /** Callback invoked when the user clicks the retry button in error state. */
  onRetry?: () => void;
}

/**
 * Displays the current model loading status with appropriate visual indicators.
 * Uses aria-live="polite" for screen reader announcements on state changes.
 *
 * - Downloading: progress bar with percentage
 * - Initializing: spinner animation
 * - Ready: green checkmark with confirmation text
 * - Error: red alert with message and retry button
 */
export function ModelStatusIndicator({
  status,
  downloadPercent,
  errorMessage,
  onRetry,
}: ModelStatusIndicatorProps): React.JSX.Element | null {
  if (status === 'idle') {
    return null;
  }

  return (
    <div className="w-full max-w-md mx-auto p-4">
      <div aria-live="polite" aria-atomic="true">
        {status === 'downloading' && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2 text-blue-700">
              <Download className="w-5 h-5" aria-hidden="true" />
              <span className="text-sm font-medium">
                Downloading model… {Math.round(downloadPercent)}%
              </span>
            </div>
            <div
              className="w-full h-3 bg-gray-200 rounded-full overflow-hidden"
              role="progressbar"
              aria-valuenow={Math.round(downloadPercent)}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Model download progress"
            >
              <div
                className="h-full bg-blue-600 rounded-full transition-all duration-300"
                style={{ width: `${Math.min(100, Math.max(0, downloadPercent))}%` }}
              />
            </div>
          </div>
        )}

        {status === 'initializing' && (
          <div className="flex items-center gap-2 text-amber-700">
            <Loader2 className="w-5 h-5 animate-spin" aria-hidden="true" />
            <span className="text-sm font-medium">Initializing model…</span>
          </div>
        )}

        {status === 'ready' && (
          <div className="flex items-center gap-2 text-green-700">
            <CheckCircle2 className="w-5 h-5" aria-hidden="true" />
            <span className="text-sm font-medium">Model ready</span>
          </div>
        )}

        {status === 'error' && (
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-2 text-red-700">
              <AlertCircle className="w-5 h-5" aria-hidden="true" />
              <span className="text-sm font-medium">
                {errorMessage || 'Failed to load model'}
              </span>
            </div>
            {onRetry && (
              <button
                type="button"
                onClick={onRetry}
                className="min-w-[48px] min-h-[48px] px-4 py-2 bg-red-600 hover:bg-red-700 text-white text-sm font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2"
                aria-label="Retry model download"
              >
                Retry
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
