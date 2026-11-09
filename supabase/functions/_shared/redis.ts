// BLACKOUT Decentralized Emergency Communications Platform
// Shared Upstash Redis Client for Supabase Edge Functions
// Features: Pure HTTP REST execution, atomic rate-limiting, short-lived idempotency locks,
//           aggregate stats caching, ephemeral gateway presence, and safe circuit-breaking.

export interface RateLimitResult {
  allowed: boolean;
  limit: number;
  remaining: number;
  reset: number;
  retryAfterSeconds: number;
  fallbackUsed?: boolean;
}

export interface IdempotencyResult {
  isNew: boolean;
  fallbackUsed?: boolean;
}

export class UpstashRedisClient {
  private url: string;
  private token: string;
  private timeoutMs: number;
  private isConfigured: boolean;

  constructor(options?: { url?: string; token?: string; timeoutMs?: number }) {
    this.url = options?.url || Deno.env.get("UPSTASH_REDIS_REST_URL") || "";
    this.token = options?.token || Deno.env.get("UPSTASH_REDIS_REST_TOKEN") || "";
    this.timeoutMs = options?.timeoutMs || 2000; // 2-second timeout to avoid blocking SOS calls
    this.isConfigured = Boolean(this.url && this.token);
  }

  /**
   * Checks if Redis credentials are set in environment.
   */
  public isReady(): boolean {
    return this.isConfigured;
  }

  /**
   * Executes a single Redis command using Upstash REST API.
   * e.g. execute(["SET", "key", "value", "EX", 60])
   */
  public async execute<T = any>(command: (string | number)[]): Promise<T | null> {
    if (!this.isConfigured) {
      return null;
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const response = await fetch(this.url, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(command),
        signal: controller.signal,
      });

      clearTimeout(timer);

      if (!response.ok) {
        console.warn(`[Upstash Redis] Command failed with status ${response.status}: ${command[0]}`);
        return null;
      }

      const json = await response.json();
      if (json.error) {
        console.warn(`[Upstash Redis] Command error: ${json.error}`);
        return null;
      }

      return json.result as T;
    } catch (err: any) {
      clearTimeout(timer);
      console.warn(`[Upstash Redis] Connection/timeout error on ${command[0]}: ${err.message || err}`);
      return null;
    }
  }

  /**
   * Executes a batch of Redis commands atomically via Upstash REST pipeline.
   */
  public async pipeline(commands: (string | number)[][]): Promise<any[] | null> {
    if (!this.isConfigured || commands.length === 0) {
      return null;
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const pipelineUrl = this.url.endsWith("/") ? `${this.url}pipeline` : `${this.url}/pipeline`;
      const response = await fetch(pipelineUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.token}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(commands),
        signal: controller.signal,
      });

      clearTimeout(timer);

      if (!response.ok) {
        console.warn(`[Upstash Redis] Pipeline failed with status ${response.status}`);
        return null;
      }

      const json = await response.json();
      return Array.isArray(json) ? json.map((entry) => entry.result) : null;
    } catch (err: any) {
      clearTimeout(timer);
      console.warn(`[Upstash Redis] Pipeline error: ${err.message || err}`);
      return null;
    }
  }

  // ==========================================================================
  // Core Key-Value Operations
  // ==========================================================================

  public async get<T = any>(key: string): Promise<T | null> {
    const raw = await this.execute<string>(["GET", key]);
    if (raw === null || raw === undefined) return null;
    try {
      return JSON.parse(raw) as T;
    } catch {
      return raw as unknown as T;
    }
  }

  public async set(
    key: string,
    value: any,
    options?: { ex?: number; nx?: boolean }
  ): Promise<boolean> {
    const serialized = typeof value === "string" ? value : JSON.stringify(value);
    const cmd: (string | number)[] = ["SET", key, serialized];

    if (options?.ex && options.ex > 0) {
      cmd.push("EX", options.ex);
    }
    if (options?.nx) {
      cmd.push("NX");
    }

    const result = await this.execute<string>(cmd);
    return result === "OK";
  }

  public async del(key: string): Promise<boolean> {
    const result = await this.execute<number>(["DEL", key]);
    return result !== null && result > 0;
  }

  public async incr(key: string): Promise<number | null> {
    return await this.execute<number>(["INCR", key]);
  }

  public async expire(key: string, seconds: number): Promise<boolean> {
    const result = await this.execute<number>(["EXPIRE", key, seconds]);
    return result !== null && result === 1;
  }

  // ==========================================================================
  // 1. Redis-Backed Atomic Rate Limiting
  // ==========================================================================

  /**
   * Evaluates fixed-window rate limiting for an identifier (device, user, or IP).
   * Atomically increments the window counter and sets an expiration if new.
   * If Redis is unreachable, applies a fail-safe policy (allows SOS, logs warning).
   */
  public async checkRateLimit(
    categoryKey: string,
    identifier: string,
    limit: number,
    windowSeconds: number
  ): Promise<RateLimitResult> {
    const redisKey = `ratelimit:${categoryKey}:${identifier}`;

    // Pipeline: INCR then EXPIRE (NX to only set on first hit)
    const pipelineResult = await this.pipeline([
      ["INCR", redisKey],
      ["TTL", redisKey],
    ]);

    if (!pipelineResult) {
      // Redis is offline/unreachable: Fail-safe fallback
      console.warn(`[RateLimit Fallback] Redis unreachable for ${redisKey}. Permitting request.`);
      return {
        allowed: true,
        limit,
        remaining: 1,
        reset: windowSeconds,
        retryAfterSeconds: 0,
        fallbackUsed: true,
      };
    }

    const currentCount = Number(pipelineResult[0]) || 1;
    let ttl = Number(pipelineResult[1]);

    // If key had no TTL or is fresh (ttl === -1), set it
    if (ttl === -1 || currentCount === 1) {
      await this.expire(redisKey, windowSeconds);
      ttl = windowSeconds;
    } else if (ttl < 0) {
      ttl = windowSeconds;
    }

    const allowed = currentCount <= limit;
    const remaining = Math.max(0, limit - currentCount);
    const retryAfter = allowed ? 0 : Math.max(1, ttl);

    return {
      allowed,
      limit,
      remaining,
      reset: ttl,
      retryAfterSeconds: retryAfter,
      fallbackUsed: false,
    };
  }

  // ==========================================================================
  // 2. Redis-Backed Idempotency Locks
  // ==========================================================================

  /**
   * Attempts to acquire an atomic idempotency lock for an alert messageId.
   * Returns isNew = true if this is the first time the messageId is seen within ttlSeconds.
   * Returns isNew = false if another gateway/peer has already uploaded this messageId.
   */
  public async checkAndSetIdempotency(
    messageId: string,
    gatewayDeviceId: string,
    ttlSeconds = 86400 // 24 hours lock
  ): Promise<IdempotencyResult> {
    const key = `idempotency:sos:${messageId}`;
    const acquired = await this.set(key, { gateway: gatewayDeviceId, timestamp: Date.now() }, {
      ex: ttlSeconds,
      nx: true,
    });

    if (!this.isConfigured) {
      // Unconfigured Redis: rely on PostgreSQL message_id unique constraint
      return { isNew: true, fallbackUsed: true };
    }

    return { isNew: acquired, fallbackUsed: false };
  }

  // ==========================================================================
  // 3. Ephemeral Gateway Presence
  // ==========================================================================

  /**
   * Registers or updates gateway heartbeat presence in Redis.
   * Auto-expires after ttlSeconds (default 3 minutes) so stale gateways disappear without DB cleanup.
   */
  public async registerGatewayPresence(
    gatewayDeviceId: string,
    metadata: {
      networkType?: string;
      ip?: string;
      appVersion?: string;
      hopCount?: number;
    },
    ttlSeconds = 180
  ): Promise<boolean> {
    const key = `presence:gateway:${gatewayDeviceId}`;
    const payload = {
      gatewayDeviceId,
      lastSeen: Date.now(),
      ...metadata,
    };
    return await this.set(key, payload, { ex: ttlSeconds });
  }

  /**
   * Retrieves active gateway presence record.
   */
  public async getGatewayPresence(gatewayDeviceId: string): Promise<any | null> {
    const key = `presence:gateway:${gatewayDeviceId}`;
    return await this.get(key);
  }

  // ==========================================================================
  // 4. Non-Sensitive Reference & Aggregation Caching
  // ==========================================================================

  public async cacheGet<T>(cacheKey: string): Promise<T | null> {
    return await this.get<T>(`cache:${cacheKey}`);
  }

  public async cacheSet(cacheKey: string, data: any, ttlSeconds: number): Promise<boolean> {
    return await this.set(`cache:${cacheKey}`, data, { ex: ttlSeconds });
  }

  public async cacheInvalidate(cacheKey: string): Promise<boolean> {
    return await this.del(`cache:${cacheKey}`);
  }
}

// Export singleton instance for edge functions
export const redis = new UpstashRedisClient();

