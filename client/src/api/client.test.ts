import { describe, it, expect, vi } from 'vitest';
import { apiRequest, ApiError } from './client';

describe('API Client', () => {
  it('handles 204 No Content', async () => {
    global.fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    const result = await apiRequest('/api/test');
    expect(result).toBeUndefined();
  });

  it('handles empty response body', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      new Response('', { status: 200, headers: { 'Content-Type': 'application/json' } })
    );
    const result = await apiRequest('/api/test');
    expect(result).toBeUndefined();
  });

  it('handles text/plain error', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      new Response('Internal Server Error', {
        status: 500,
        headers: { 'Content-Type': 'text/plain' },
      })
    );
    await expect(apiRequest('/api/test')).rejects.toThrow('Internal Server Error');
  });

  it('parses Retry-After as seconds', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      new Response('{}', {
        status: 429,
        headers: { 'Content-Type': 'application/json', 'Retry-After': '30' },
      })
    );

    try {
      await apiRequest('/api/test');
    } catch (error) {
      expect((error as ApiError).retryAfter).toBe(30);
    }
  });

  it('parses Retry-After as HTTP-date', async () => {
    const futureDate = new Date(Date.now() + 30000).toUTCString();
    global.fetch = vi.fn().mockResolvedValue(
      new Response('{}', {
        status: 429,
        headers: { 'Content-Type': 'application/json', 'Retry-After': futureDate },
      })
    );

    try {
      await apiRequest('/api/test');
    } catch (error) {
      expect((error as ApiError).retryAfter).toBeGreaterThan(25);
      expect((error as ApiError).retryAfter).toBeLessThan(35);
    }
  });

  it('includes request ID from server', async () => {
    global.fetch = vi.fn().mockResolvedValue(
      new Response('{}', {
        status: 500,
        headers: { 'Content-Type': 'application/json', 'X-Request-ID': 'abc123' },
      })
    );

    try {
      await apiRequest('/api/test');
    } catch (error) {
      expect((error as ApiError).requestId).toBe('abc123');
    }
  });
});






