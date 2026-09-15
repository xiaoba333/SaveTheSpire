@echo off
REM Start the backend HTTP server (the Unity front-end talks to it, default port 8080).
REM
REM Why this script exists: if java is launched from another process (IDE, agent tool,
REM proxy), that process's exit can take java down with it -- the symptom is
REM "it worked a second ago, now HTTP 0 / connection refused".
REM Launching it here (or by hand in your own terminal) keeps it owned by that console.
REM
REM NOTE: keep this file ASCII-only and CRLF. cmd.exe reads .bat in byte chunks, so
REM LF line endings combined with multi-byte characters make it mis-parse lines.
REM
REM Log goes to server-run.log (stdout+stderr merged).
cd /d "%~dp0"
echo [%date% %time%] starting backend >> server-run.log
java -cp target/classes com.roguelike.dungeon.http.HttpServerMain >> server-run.log 2>&1
echo [%date% %time%] backend exited (code %errorlevel%) >> server-run.log
