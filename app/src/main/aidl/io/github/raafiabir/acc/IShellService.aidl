package io.github.raafiabir.acc;

interface IShellService {
    // Reserved transaction code: Shizuku calls this to shut the service down.
    void destroy() = 16777114;

    // Runs a script with sh and returns combined stdout/stderr (tail, max 16 KB).
    String exec(String script) = 1;
}
