/**
 * Type declarations for the AudioWorkletGlobalScope.
 * These types are available inside AudioWorklet processor modules but are not
 * part of the standard DOM lib typings in TypeScript.
 */

declare const sampleRate: number;
declare const currentTime: number;
declare const currentFrame: number;

declare class AudioWorkletProcessor {
  readonly port: MessagePort;
  constructor(options?: AudioWorkletNodeOptions);
  process(
    inputs: Float32Array[][],
    outputs: Float32Array[][],
    parameters: Record<string, Float32Array>
  ): boolean;
}

declare function registerProcessor(
  name: string,
  processorCtor: new (options?: AudioWorkletNodeOptions) => AudioWorkletProcessor
): void;
