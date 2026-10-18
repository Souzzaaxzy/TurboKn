package com.turbokn.app;

interface ITurboKnService {

    /** Reserved: called by the Shizuku server to destroy the user service. */
    void destroy() = 16777114;

    /** Runs a shell command as the shell user and returns its combined output. */
    String exec(String command) = 1;

    /** Returns "uid=<uid> pid=<pid>" so the UI can prove the service is running elevated. */
    String whoami() = 2;
}
