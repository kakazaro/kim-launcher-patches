# How Kim Launcher checks the subscription (1.0.2781)

Static analysis only; the app was not run. This documents what the shipped code does so the
**Show subscription check result** patch can report it. The patch is read-only: it does not change
the check, the saved status or any limit.

## The status record

Kept in memory (`K1/sd.z0`) and saved in SharedPreferences `VehicleLocationSync`
(keys `vehicleId`, `plan`, `status`, `expiresAt`, `fetchedAt`). It survives restarts.

| Field | Meaning |
|---|---|
| `plan` | `trial`, `paid`, or anything else (`free`) |
| `status` | `active`, or anything else (`disabled`, ...) |
| `expiresAtMs` | subscription end, from the server (`expiresAt`, or `paidUntil` if later) |
| `fetchedAtMs` | when the server was **last successfully** asked |
| `lastError` | text of the last failed attempt, or null |

## The check

`GET <Firebase RTDB>/vehicles/<vehicleId>/entitlement.json?auth=<token>`, where the token is an
anonymous Firebase sign-in token (so a sign-in also needs the internet).

```
refresh task (K1/t6) ── token available? ── no ──> nothing happens (silent)
                                  │ yes
                                  v
              fetch (K1/sd.f0, force=false)
   saved status under 15 min old, same vehicle? ── yes ──> return it, no request
                                  │ no
                                  v
                       GET entitlement.json
        answered ──> parse, fetchedAt = now, lastError = null, save
        failed   ──> keep plan/status/expiresAt/fetchedAt, only set lastError
```

Callers: Dart reaches it through the `refreshEntitlement` channel call (the timer period is inside
`libapp.so`, not visible here). Native callers also exist (`K1/z3`, `K1/a1`, `K1/n1`, `K1/D0`,
`K1/Ba`, `K1/sd`); I did not trace what triggers each. A forced call skips the 15-minute shortcut.

## Is it active? (`K1/md.d`)

All of these must hold:

1. `plan` is `trial` or `paid`
2. `status` is `active`
3. `expiresAt` is in the future
4. `fetchedAt` is unset **or** less than **7 days** ago (`0x240c8400` ms)

`entitlementGrace` (shown to Dart) just means "active, but not verified in the last 15 minutes".

## What this means for a car without signal

- A failed check **never erases** your saved status. It only records the error.
- The 7 days count from the last **successful** check, not the last attempt.
- So the car is fine as long as it gets one successful check at least every 7 days
  (a phone hotspot for a few minutes is enough), and your subscription itself hasn't ended.
- After 7 days with no successful check the app treats the status as expired ("Entitlement cache
  expired") and locks the features until a check succeeds. Nothing is lost; the next successful
  check restores it.
- With no sign-in token the refresh does nothing at all, with no error shown. That is the usual
  offline case, and why this patch also reports it.
- If the server answers but has no entitlement for the vehicle (`null`), the app stores
  `free/disabled`: that is "not subscribed", not an offline problem.
- Whether the Dart UI applies extra limits of its own on top of this is *unknown* (not
  readable from the snapshot).

This 7-day limit is the developer's design, and this project does not change it. If it doesn't fit
how you use the car, ask the developer for a longer offline period.

## The toast (patch "Show subscription check result")

Shown when the fetch finishes and when the refresh is skipped for lack of a token. It is not shown
when the 15-minute shortcut answers without a request. An identical repeat of the last message is
suppressed, but while offline the countdown text changes, so expect about one toast per hour.

| Toast | Meaning |
|---|---|
| `OK, paid/active, until 2027-02-14 15:00` | server answered, subscription active |
| `OK, free/disabled. NOT active` | server answered; no plan for this vehicle |
| `FAILED (UnknownHostException...). Saved status: paid/active, until ... Stays active offline for 5d 3h more` | no network; saved status still good for that long |
| `FAILED (...). ... Older than 7 days: locked until a check succeeds` | offline too long |
| `FAILED (...). ... NOT active` | saved status is itself not active or has expired |
| `FAILED (no sign-in token (offline?), server not contacted)...` | refresh skipped; same remaining-time text |

Hooks: end of `K1/sd.f0` (reports the result it is about to return) and the null-token branch of
`K1/t6.run`. The text logic is in `extensions/kim/.../EntitlementToast.java`; check it off-device
with the commands in `tools/check/EntitlementToastCheck.java`.

Not verified on a device: that the head unit displays the toast.
