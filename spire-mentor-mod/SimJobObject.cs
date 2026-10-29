// SimJobObject.cs -- make the advisor process die with the game, always.
//
// The advisor is a CHILD of SlayTheSpire2.exe, and Steam treats the game as
// still running for as long as anything in the game's process tree lives.
// So an orphaned advisor does not merely waste a CPU core and hold port
// 9002 (the failure mode the F7 harness already knows about) -- it leaves
// Steam stuck on "Running" until the player force-quits from the library
// (reported 2026-08-08).
//
// Killing it in a shutdown handler is necessary but not sufficient: Alt+F4,
// Steam's stop button and a hard crash never reach managed code. A Windows
// JOB OBJECT closes that gap in the kernel. The advisor is assigned to a job
// created with JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE, and the only handle to
// that job is held by the game process -- so when the game dies, by any
// means, the handle closes and Windows terminates the job's processes.
//
// Notes:
//   - The handle is intentionally NEVER closed. Closing it early would kill
//     a perfectly healthy advisor; process exit closes it for us.
//   - Nested jobs (Steam may already have put the game in one) are supported
//     on Windows 8+ and the game requires far newer, so assignment succeeds.
//   - Every failure is a logged no-op: worst case we are back to the
//     shutdown handler alone, which is where we were before.

using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

namespace SpireMentor;

internal static class SimJobObject
{
    private static readonly object _lock = new();
    private static IntPtr _job = IntPtr.Zero;
    private static bool _unavailable;

    /// <summary>
    /// Put <paramref name="process"/> in the kill-on-close job. Returns true
    /// if the OS now guarantees the process dies with this one.
    /// </summary>
    public static bool Adopt(Process process)
    {
        if (!OperatingSystem.IsWindows())
        {
            return false;
        }
        try
        {
            lock (_lock)
            {
                if (_unavailable)
                {
                    return false;
                }
                if (_job == IntPtr.Zero)
                {
                    _job = CreateJob();
                    if (_job == IntPtr.Zero)
                    {
                        _unavailable = true;
                        return false;
                    }
                }
                if (!AssignProcessToJobObject(_job, process.Handle))
                {
                    Logger.Log(
                        "[SimJobObject] Could not assign the advisor to the job " +
                        $"(win32 error {Marshal.GetLastWin32Error()}); it will only be stopped " +
                        "by the shutdown handler.");
                    return false;
                }
                return true;
            }
        }
        catch (Exception ex)
        {
            Logger.Log($"[SimJobObject] Unavailable ({ex.GetType().Name}: {ex.Message}); " +
                       "falling back to the shutdown handler.");
            _unavailable = true;
            return false;
        }
    }

    private static IntPtr CreateJob()
    {
        // Unnamed: a named job would be shared with a second running copy of
        // the game, and then quitting one would kill the other's advisor.
        IntPtr job = CreateJobObject(IntPtr.Zero, null);
        if (job == IntPtr.Zero)
        {
            Logger.Log($"[SimJobObject] CreateJobObject failed (win32 error {Marshal.GetLastWin32Error()}).");
            return IntPtr.Zero;
        }

        var info = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
        info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        int size = Marshal.SizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>();
        IntPtr buffer = Marshal.AllocHGlobal(size);
        try
        {
            Marshal.StructureToPtr(info, buffer, fDeleteOld: false);
            if (!SetInformationJobObject(job, JobObjectExtendedLimitInformation, buffer, (uint)size))
            {
                Logger.Log($"[SimJobObject] SetInformationJobObject failed (win32 error {Marshal.GetLastWin32Error()}).");
                CloseHandle(job);
                return IntPtr.Zero;
            }
        }
        finally
        {
            Marshal.FreeHGlobal(buffer);
        }
        Logger.Log("[SimJobObject] Kill-on-close job created; child processes will not outlive the game.");
        return job;
    }

    // ------------------------------------------------------------------
    // Win32
    // ------------------------------------------------------------------

    private const int JobObjectExtendedLimitInformation = 9;
    private const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateJobObject(IntPtr lpJobAttributes, string? lpName);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetInformationJobObject(
        IntPtr hJob, int jobObjectInfoClass, IntPtr lpJobObjectInfo, uint cbJobObjectInfoLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool AssignProcessToJobObject(IntPtr hJob, IntPtr hProcess);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr hObject);

    [StructLayout(LayoutKind.Sequential)]
    private struct JOBOBJECT_BASIC_LIMIT_INFORMATION
    {
        public long PerProcessUserTimeLimit;
        public long PerJobUserTimeLimit;
        public uint LimitFlags;
        public UIntPtr MinimumWorkingSetSize;
        public UIntPtr MaximumWorkingSetSize;
        public uint ActiveProcessLimit;
        public UIntPtr Affinity;
        public uint PriorityClass;
        public uint SchedulingClass;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct IO_COUNTERS
    {
        public ulong ReadOperationCount;
        public ulong WriteOperationCount;
        public ulong OtherOperationCount;
        public ulong ReadTransferCount;
        public ulong WriteTransferCount;
        public ulong OtherTransferCount;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION
    {
        public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
        public IO_COUNTERS IoInfo;
        public UIntPtr ProcessMemoryLimit;
        public UIntPtr JobMemoryLimit;
        public UIntPtr PeakProcessMemoryUsed;
        public UIntPtr PeakJobMemoryUsed;
    }
}
