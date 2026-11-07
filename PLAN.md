# Android DNS Blocker: Plan

Personal-use Android app that blocks YouTube and other blacklisted sites via a local DNS-only VPN.

## Approach

- `VpnService` with a fake DNS IP (e.g. `10.0.0.2`). Only that `/32` is routed into the TUN, so other traffic bypasses the VPN.
- Parse DNS queries from the TUN, suffix-match against the blocklist, answer `NXDOMAIN`/`0.0.0.0` for blocked names, forward the rest upstream over DoH.
- No SOCKS/tun2socks needed for DNS-only blocking.
- Kotlin-only for v1. Move the engine to Go (gomobile) later if wanted.

## Steps

### 1. Project setup
- [ ] Create Android project (Kotlin, min SDK 26).
- [ ] Declare `BIND_VPN_SERVICE` service and foreground-service permissions in the manifest.

### 2. VPN plumbing
- [ ] Implement `VpnService` subclass.
- [ ] Request VPN consent via `VpnService.prepare()`.
- [ ] Build the TUN: address, `addDnsServer(10.0.0.2)`, `addRoute(10.0.0.2, 32)`.
- [ ] Run as a foreground service with a notification.
- [ ] Read packets from the TUN and log the DNS queries seen (sanity check).

### 3. DNS handling
- [ ] Parse IPv4/UDP packets, extract the DNS question.
- [ ] Handle TCP DNS too (optional for v1).
- [ ] Build response packets (swap src/dst, fix IP/UDP checksums) and write back to the TUN.

### 4. Blocking
- [ ] Suffix-matching blocklist (`*.youtube.com` etc.).
- [ ] Respond `NXDOMAIN` or `0.0.0.0`/`::` for blocked names.
- [ ] Seed list: `youtube.com`, `youtu.be`, `googlevideo.com`, `ytimg.com`, `youtubei.googleapis.com`, `youtube-nocookie.com`. Do not block all of `googleapis.com`.

### 5. Upstream forwarding
- [ ] Forward allowed queries to a DoH resolver (Cloudflare/Quad9).
- [ ] Call `VpnService.protect()` on upstream sockets to avoid routing loops.
- [ ] Use short TTLs on answers to limit stale cache.

### 6. Close bypasses
- [ ] Block known DoH hostnames (`dns.google`, `cloudflare-dns.com`, ...) so browsers can't use their own encrypted DNS.
- [ ] Consider blocking DoT (port 853) if full traffic routing is added later.

### 7. App UI
- [ ] Toggle to start/stop the VPN.
- [ ] Blocklist editor (add/remove domains), persisted locally.
- [ ] Enable Always-on VPN in Android settings so it survives reboot.

### 8. Personal extras
- [ ] Query log screen (find which domains an app needs).
- [ ] Schedules (e.g. block YouTube during work hours).
- [ ] Temporary "pause 10 min" override.

### 9. Test and iterate
- [ ] Test with the YouTube app and browsers; note what leaks.
- [ ] Watch for hardcoded IPs, cached DNS, and browser DoH.

## Later (v2, only if leaks matter)

- Route all traffic through the TUN with a userspace TCP/IP stack (gVisor netstack / tun2socks).
- Add IP-level blocking of resolved addresses and SNI inspection (TLS/QUIC).
- Move the engine to Go via gomobile if sharing code across platforms.
