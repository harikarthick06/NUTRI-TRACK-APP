import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, buildUrl, setAccessToken, setSessionExpiredHandler } from "./api";

const json = (status: number, body: unknown) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

afterEach(() => {
  vi.unstubAllGlobals();
  setAccessToken(null);
  setSessionExpiredHandler(null);
});

describe("buildUrl", () => {
  it("drops empty query values", () => {
    expect(buildUrl("/api/foods", { q: "idli", page: 0, category: "", x: undefined })).toBe("/api/foods?q=idli&page=0");
  });
});

describe("api", () => {
  it("sends the access token", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    setAccessToken("tok-1");
    await api("/api/profile");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer tok-1");
  });

  it("refreshes once on 401 and retries with the new token", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(401, { message: "expired" }))
      .mockResolvedValueOnce(json(200, { accessToken: "tok-2", user: {} }))
      .mockResolvedValueOnce(json(200, { name: "Hari" }));
    vi.stubGlobal("fetch", fetchMock);
    setAccessToken("tok-1");
    await expect(api("/api/profile")).resolves.toEqual({ name: "Hari" });
    expect(fetchMock.mock.calls[1][0]).toBe("/api/auth/refresh");
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe("Bearer tok-2");
  });

  it("shares one refresh between concurrent 401s", async () => {
    let refreshCalls = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init: RequestInit) => {
        if (url === "/api/auth/refresh") {
          refreshCalls++;
          return json(200, { accessToken: "fresh", user: {} });
        }
        const auth = (init.headers as Record<string, string>).Authorization;
        return auth === "Bearer fresh" ? json(200, { url }) : json(401, {});
      }),
    );
    setAccessToken("stale");
    await Promise.all([api("/api/a"), api("/api/b"), api("/api/c")]);
    expect(refreshCalls).toBe(1);
  });

  it("calls the session-expired handler when refresh fails", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValueOnce(json(401, {})).mockResolvedValueOnce(json(401, {})));
    const expired = vi.fn();
    setSessionExpiredHandler(expired);
    await expect(api("/api/profile")).rejects.toBeInstanceOf(ApiError);
    expect(expired).toHaveBeenCalledOnce();
  });

  it("maps field errors from the server", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(json(400, { message: "Invalid", code: "VALIDATION", fieldErrors: [{ field: "amountMl", message: "Too large" }] })),
    );
    const err = (await api("/api/water-logs", { method: "POST", body: {} }).catch((e) => e)) as ApiError;
    expect(err.status).toBe(400);
    expect(err.code).toBe("VALIDATION");
    expect(err.fieldErrors).toEqual({ amountMl: "Too large" });
  });

  it("reports network failures as status 0", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    const err = (await api("/api/profile").catch((e) => e)) as ApiError;
    expect(err.status).toBe(0);
    expect(err.message).toMatch(/can't reach/i);
  });
});
