# record_toggle.ps1 -- start/stop an Xbox Game Bar recording of the STS2 window
# WITHOUT pressing a hotkey on the client.
#
# Why this exists: the desktop is driven over Moonlight, and the client OS
# swallows Win-key combos (Win+Alt+R) before they ever reach this host, so the
# built-in capture shortcut is unreachable from the remote side. This
# synthesizes the same keystroke locally, where nothing intercepts it.
#
# Game Bar records the FOREGROUND window and nothing else, which is exactly the
# "only the game" framing we want -- so the script focuses the game first and
# refuses to fire if it can't, rather than silently recording whatever happened
# to be on top (a terminal full of logs, say).
#
# Toggle semantics are Game Bar's own: the same keystroke starts and stops.
# Run it once to start, once more to stop. Output lands in
# %USERPROFILE%\Videos\Captures.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File record_toggle.ps1
#   powershell -ExecutionPolicy Bypass -File record_toggle.ps1 -ProcessName SlayTheSpire2

param(
    [string]$ProcessName = "SlayTheSpire2",
    # Skip the focus step and record whatever is already in front. Only useful
    # for testing the keystroke path itself.
    [switch]$AnyWindow
)

Add-Type @'
using System;
using System.Runtime.InteropServices;

public static class Rec {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, IntPtr pid);
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();

    /// <summary>
    /// Take the foreground reliably. A plain SetForegroundWindow from a
    /// background process is refused by Windows' foreground lock -- it
    /// returns success and does nothing, which is how a capture once
    /// targeted the terminal instead of the game. Two standard defeats,
    /// applied together: tap ALT (a synthetic keypress makes this process
    /// look like it owns recent input), and attach our input queue to the
    /// current foreground thread so we count as the same input context.
    /// </summary>
    public static bool ForceForeground(IntPtr hWnd) {
        keybd_event(0x12, 0, 0, UIntPtr.Zero);
        keybd_event(0x12, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
        uint fgThread = GetWindowThreadProcessId(GetForegroundWindow(), IntPtr.Zero);
        uint thisThread = GetCurrentThreadId();
        if (fgThread != thisThread) { AttachThreadInput(thisThread, fgThread, true); }
        ShowWindow(hWnd, 9); // SW_RESTORE
        BringWindowToTop(hWnd);
        SetForegroundWindow(hWnd);
        if (fgThread != thisThread) { AttachThreadInput(thisThread, fgThread, false); }
        System.Threading.Thread.Sleep(400);
        return GetForegroundWindow() == hWnd;
    }

    // keybd_event, not SendInput. SendInput is the modern API but every
    // call from here returned 0 with GetLastError = 87
    // (ERROR_INVALID_PARAMETER) despite a correctly-sized 32-byte INPUT
    // struct -- PowerShell's Add-Type marshalling of the explicit-layout
    // union is the likely culprit and not worth fighting. keybd_event is
    // deprecated in favour of SendInput but is still delivered to the same
    // input queue and works here.
    [DllImport("user32.dll", SetLastError = true)]
    public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, UIntPtr dwExtraInfo);

    const uint KEYEVENTF_KEYUP = 0x0002;

    // Win(0x5B) + Alt(0x12) + R(0x52), released in strict reverse order --
    // Game Bar ignores the combo if the modifiers lift before the letter.
    // The hold is deliberate: with no gap the overlay intermittently misses it.
    public static void SendWinAltR() {
        keybd_event(0x5B, 0, 0, UIntPtr.Zero);
        keybd_event(0x12, 0, 0, UIntPtr.Zero);
        keybd_event(0x52, 0, 0, UIntPtr.Zero);
        System.Threading.Thread.Sleep(60);
        keybd_event(0x52, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
        keybd_event(0x12, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
        keybd_event(0x5B, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
    }
}
'@

if (-not $AnyWindow) {
    $proc = Get-Process -Name $ProcessName -ErrorAction SilentlyContinue |
            Where-Object { $_.MainWindowHandle -ne 0 } | Select-Object -First 1
    if (-not $proc) {
        Write-Error "No '$ProcessName' window found. Launch the game first (or pass -AnyWindow)."
        exit 1
    }
    $ok = $false
    for ($try = 1; $try -le 5 -and -not $ok; $try++) {
        $ok = [Rec]::ForceForeground($proc.MainWindowHandle)
        if (-not $ok) { Start-Sleep -Milliseconds 600 }
    }
    if (-not $ok) {
        Write-Error "Could not bring '$ProcessName' to the foreground; refusing to record another window. Click the game window once, then re-run."
        exit 2
    }
    Write-Output "Focused $ProcessName (pid $($proc.Id))."
}

$cap = "$env:USERPROFILE\Videos\Captures"
$newest = Get-ChildItem $cap -Filter *.mp4 -ErrorAction SilentlyContinue |
          Sort-Object LastWriteTime -Descending | Select-Object -First 1
$sizeBefore = if ($newest) { $newest.Length } else { 0 }

[Rec]::SendWinAltR()
Write-Output "Sent Win+Alt+R. Game Bar capture toggled (same key starts and stops)."
Start-Sleep -Seconds 8

# Whether a capture is running is decided by a FILE LOCK, never by file size.
# Game Bar buffers and writes the bulk on stop: one capture sat at 4.4 MB for
# 14 s mid-recording and finalized at 80 MB, so "not growing" reads as
# "stopped" and is simply wrong. An open write handle is unambiguous.
function Get-CaptureState {
    $f = Get-ChildItem $cap -Filter *.mp4 -ErrorAction SilentlyContinue |
         Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $f) { return $null }
    $open = $false
    try { $s = [IO.File]::Open($f.FullName, 'Open', 'ReadWrite', 'None'); $s.Close() } catch { $open = $true }
    [PSCustomObject]@{ File = $f; Recording = $open }
}

$state = Get-CaptureState
if ($state -and $state.Recording) {
    # Game Bar names the file after the window it captured. If that is not the
    # game, it grabbed the wrong window (the foreground lock can silently
    # defeat the focus step) -- stop at once rather than record the desktop.
    if ($state.File.Name -notlike "Slay the Spire 2*") {
        Write-Warning "Capture targeted the WRONG window: $($state.File.Name) -- stopping it."
        [Rec]::SendWinAltR()
        Start-Sleep -Seconds 6
        exit 4
    }
    Write-Output "RECORDING -> $($state.File.Name)"
} elseif ($state) {
    Write-Output "STOPPED -> $($state.File.Name) ($([math]::Round($state.File.Length/1MB,1)) MB)"
} else {
    Write-Output "No capture files found."
}
Write-Output "Files: $cap"
